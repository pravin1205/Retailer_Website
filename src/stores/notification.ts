import { create } from "zustand";
import { persist } from "zustand/middleware";
import { useMemo } from "react";
import { useOrdersStore } from "./index";
import { useAdminStore, useTenantProducts } from "./admin";
import { tenants } from "@/lib/mock/tenants";
import type { Order } from "@/lib/types";

export type NotificationKind = "order_placed" | "status_changed" | "low_stock" | "new_customer" | "new_tenant";

export interface Notification {
  id: string;
  kind: NotificationKind;
  scope: "platform" | string; // tenant slug or "platform"
  title: string;
  body?: string;
  emoji: string;
  at: string; // ISO
  href?: { to: string; params?: Record<string, string> };
}

interface NotifState {
  // last-read timestamp per scope ("platform" | tenant slug)
  cursors: Record<string, string>;
  markAllRead: (scope: string) => void;
}

export const useNotifStore = create<NotifState>()(
  persist(
    (set) => ({
      cursors: {},
      markAllRead: (scope) =>
        set((s) => ({ cursors: { ...s.cursors, [scope]: new Date().toISOString() } })),
    }),
    { name: "marketly-notifs" },
  ),
);

function fromOrder(o: Order, prevStatus?: Order["status"]): Notification {
  const tenant = tenants.find((t) => t.slug === o.tenantSlug);
  if (prevStatus && prevStatus !== o.status) {
    return {
      id: `stat_${o.id}_${o.status}`,
      kind: "status_changed",
      scope: o.tenantSlug,
      emoji: o.status === "delivered" ? "✅" : o.status === "cancelled" ? "⚠️" : "🚚",
      title: `Order #${o.id.slice(-6).toUpperCase()} · ${o.status.replace(/_/g, " ")}`,
      body: `${tenant?.name ?? o.tenantSlug} — ${o.customer?.name ?? "Guest"}`,
      at: o.placedAt,
      href: { to: "/s/$tenant/admin/orders/$id", params: { tenant: o.tenantSlug, id: o.id } },
    };
  }
  return {
    id: `ord_${o.id}`,
    kind: "order_placed",
    scope: o.tenantSlug,
    emoji: o.items[0]?.imageEmoji ?? "🧾",
    title: `New order · ₹${Math.round(o.total).toLocaleString("en-IN")}`,
    body: `${o.customer?.name ?? "Guest"} — ${o.items.reduce((n, it) => n + it.quantity, 0)} items`,
    at: o.placedAt,
    href: { to: "/s/$tenant/admin/orders/$id", params: { tenant: o.tenantSlug, id: o.id } },
  };
}

/** Derive a notification feed for a tenant owner. */
export function useTenantNotifications(slug: string) {
  const userOrders = useOrdersStore((s) => s.orders);
  const historical = useAdminStore((s) => s.historicalOrders);
  const statusOverrides = useAdminStore((s) => s.orderStatusOverrides);
  const cursor = useNotifStore((s) => s.cursors[slug]);
  const products = useTenantProducts(slug);

  return useMemo(() => {
    const orders = [...userOrders, ...historical]
      .filter((o) => o.tenantSlug === slug)
      .map((o) => (statusOverrides[o.id] ? { ...o, status: statusOverrides[o.id] } : o))
      .sort((a, b) => +new Date(b.placedAt) - +new Date(a.placedAt))
      .slice(0, 40);

    const list: Notification[] = orders.map((o) => fromOrder(o, statusOverrides[o.id] ? "placed" : undefined));

    products
      .filter((p) => p.stock <= 5)
      .slice(0, 5)
      .forEach((p) =>
        list.push({
          id: `low_${p.id}`,
          kind: "low_stock",
          scope: slug,
          emoji: p.stock === 0 ? "🛑" : "⚠️",
          title: `${p.stock === 0 ? "Out of stock" : "Low stock"} · ${p.name}`,
          body: `${p.stock} units left`,
          at: new Date(Date.now() - 3600_000).toISOString(),
          href: { to: "/s/$tenant/admin/products/$id", params: { tenant: slug, id: p.id } },
        }),
      );

    list.sort((a, b) => +new Date(b.at) - +new Date(a.at));
    const unread = cursor ? list.filter((n) => +new Date(n.at) > +new Date(cursor)).length : list.length;
    return { list, unread };
  }, [userOrders, historical, statusOverrides, products, slug, cursor]);
}

/** Derive platform-wide feed for super admin. */
export function usePlatformNotifications() {
  const userOrders = useOrdersStore((s) => s.orders);
  const historical = useAdminStore((s) => s.historicalOrders);
  const tenantOverrides = useAdminStore((s) => s.tenantOverrides);
  const cursor = useNotifStore((s) => s.cursors["platform"]);

  return useMemo(() => {
    const orders = [...userOrders, ...historical]
      .sort((a, b) => +new Date(b.placedAt) - +new Date(a.placedAt))
      .slice(0, 30);
    const list: Notification[] = orders.map((o) => fromOrder(o));

    Object.keys(tenantOverrides).forEach((slug) => {
      const t = tenants.find((x) => x.slug === slug);
      if (!t) return;
      list.push({
        id: `tweak_${slug}`,
        kind: "new_tenant",
        scope: "platform",
        emoji: t.logoEmoji,
        title: `${t.name} updated branding`,
        body: "Settings changed via owner dashboard",
        at: new Date(Date.now() - 30 * 60_000).toISOString(),
      });
    });

    list.sort((a, b) => +new Date(b.at) - +new Date(a.at));
    const unread = cursor ? list.filter((n) => +new Date(n.at) > +new Date(cursor)).length : list.length;
    return { list, unread };
  }, [userOrders, historical, tenantOverrides, cursor]);
}
