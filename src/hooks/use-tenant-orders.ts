import { useMemo } from "react";
import { useOrdersStore } from "@/stores";
import { useAdminStore } from "@/stores/admin";
import type { Order } from "@/lib/types";

/** Merged stream of seed historical orders + user-placed orders for a tenant, newest first. */
export function useTenantOrders(tenantSlug: string): Order[] {
  const userOrders = useOrdersStore((s) => s.orders);
  const historical = useAdminStore((s) => s.historicalOrders);
  const overrides = useAdminStore((s) => s.orderStatusOverrides);
  return useMemo(() => {
    const all: Order[] = [
      ...userOrders
        .filter((o) => o.tenantSlug === tenantSlug)
        .map((o) => (overrides[o.id] ? { ...o, status: overrides[o.id] } : o)),
      ...historical.filter((o) => o.tenantSlug === tenantSlug),
    ];
    return all.sort((a, b) => +new Date(b.placedAt) - +new Date(a.placedAt));
  }, [tenantSlug, userOrders, historical, overrides]);
}
