import { createFileRoute, Link } from "@tanstack/react-router";
import { useMemo, useState, memo } from "react";
import { useSuspenseQuery } from "@tanstack/react-query";
import { Download, Search } from "lucide-react";
import { api, qk } from "@/lib/api";
import { useOrdersStore } from "@/stores";
import { useAdminStore } from "@/stores/admin";
import { OrderStatusBadge } from "@/components/admin/OrderStatusBadge";
import { Button } from "@/components/ui/button";
import { formatCurrency, formatDateTime } from "@/lib/format";
import { downloadCsv, toCsv } from "@/lib/csv";
import type { Order, Tenant } from "@/lib/types";

export const Route = createFileRoute("/admin/orders")({
  head: () => ({ meta: [{ title: "All orders · Platform admin" }] }),
  loader: ({ context }) => {
    context.queryClient.ensureQueryData({ queryKey: qk.tenants, queryFn: api.listTenants });
  },
  component: GlobalOrdersPage,
});

const STATUSES = ["all", "placed", "packing", "out_for_delivery", "delivered", "cancelled"] as const;

const RowMemo = memo(function Row({ o, tenant }: { o: Order; tenant?: Tenant }) {
  return (
    <tr className="border-b border-border last:border-0 transition-colors hover:bg-surface-muted/40">
      <td className="px-4 py-3 font-mono text-[12px]">#{o.id.slice(-6).toUpperCase()}</td>
      <td className="px-4 py-3">
        <Link to="/s/$tenant/admin" params={{ tenant: o.tenantSlug }} className="flex items-center gap-2 hover:text-primary">
          <span className="grid h-7 w-7 place-items-center rounded-lg text-[13px]" style={{ background: tenant?.bannerGradient }}>
            {tenant?.logoEmoji}
          </span>
          <span className="text-[12.5px] font-medium">{tenant?.name ?? o.tenantSlug}</span>
        </Link>
      </td>
      <td className="px-4 py-3">
        <div className="font-medium">{o.customer?.name ?? "Guest"}</div>
        <div className="text-[11px] text-muted-foreground">{o.address.line2 ?? o.address.city}</div>
      </td>
      <td className="px-4 py-3 text-muted-foreground">{formatDateTime(o.placedAt)}</td>
      <td className="px-4 py-3 text-right font-semibold tabular-nums">{formatCurrency(o.total)}</td>
      <td className="px-4 py-3"><OrderStatusBadge status={o.status} /></td>
      <td className="px-4 py-3 text-right">
        <Link to="/s/$tenant/admin/orders/$id" params={{ tenant: o.tenantSlug, id: o.id }} className="text-[12px] font-medium text-primary hover:underline">
          Open
        </Link>
      </td>
    </tr>
  );
});

function GlobalOrdersPage() {
  const { data: tenants } = useSuspenseQuery({ queryKey: qk.tenants, queryFn: api.listTenants });
  const userOrders = useOrdersStore((s) => s.orders);
  const historical = useAdminStore((s) => s.historicalOrders);
  const overrides = useAdminStore((s) => s.orderStatusOverrides);
  const [status, setStatus] = useState<(typeof STATUSES)[number]>("all");
  const [tenantFilter, setTenantFilter] = useState<string>("all");
  const [query, setQuery] = useState("");

  const tenantMap = useMemo(() => new Map(tenants.map((t) => [t.slug, t])), [tenants]);

  const orders = useMemo(() => {
    const all = [...userOrders, ...historical]
      .map((o) => (overrides[o.id] ? { ...o, status: overrides[o.id] } : o))
      .sort((a, b) => +new Date(b.placedAt) - +new Date(a.placedAt));
    return all.filter((o) => {
      if (status !== "all" && o.status !== status) return false;
      if (tenantFilter !== "all" && o.tenantSlug !== tenantFilter) return false;
      if (query) {
        const q = query.toLowerCase();
        if (
          !o.id.toLowerCase().includes(q) &&
          !(o.customer?.name ?? "").toLowerCase().includes(q) &&
          !(o.customer?.email ?? "").toLowerCase().includes(q)
        )
          return false;
      }
      return true;
    });
  }, [userOrders, historical, overrides, status, tenantFilter, query]);

  return (
    <div className="mx-auto max-w-7xl px-4 py-6 md:px-6 md:py-8">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight md:text-3xl">All orders</h1>
          <p className="mt-1 text-sm text-muted-foreground">{orders.length} matching orders across the platform</p>
        </div>
        <Button
          variant="outline"
          size="sm"
          className="rounded-full"
          onClick={() =>
            downloadCsv(
              `platform-orders.csv`,
              toCsv(
                orders.map((o) => ({
                  id: o.id,
                  tenant: o.tenantSlug,
                  placedAt: o.placedAt,
                  status: o.status,
                  customer: o.customer?.name ?? "",
                  total: o.total,
                  payment: o.payment,
                })),
              ),
            )
          }
        >
          <Download className="h-3.5 w-3.5" /> Export CSV
        </Button>
      </div>

      <div className="mt-5 flex flex-wrap items-center gap-2">
        <div className="relative flex-1 min-w-[200px] max-w-md">
          <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
          <input
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Search by order ID, customer…"
            className="h-9 w-full rounded-full border border-border bg-surface pl-9 pr-3 text-[13px] outline-none focus:border-primary/50"
          />
        </div>
        <select
          value={tenantFilter}
          onChange={(e) => setTenantFilter(e.target.value)}
          className="h-9 rounded-full border border-border bg-surface px-3 text-[12.5px] outline-none focus:border-primary/50"
        >
          <option value="all">All tenants</option>
          {tenants.map((t) => (
            <option key={t.slug} value={t.slug}>{t.name}</option>
          ))}
        </select>
        <div className="flex gap-1 rounded-full border border-border bg-card p-0.5">
          {STATUSES.map((s) => (
            <button
              key={s}
              onClick={() => setStatus(s)}
              className={
                "rounded-full px-3 py-1 text-[12px] font-medium capitalize " +
                (status === s ? "bg-primary text-primary-foreground" : "text-muted-foreground hover:text-foreground")
              }
            >
              {s.replace(/_/g, " ")}
            </button>
          ))}
        </div>
      </div>

      <div className="mt-5 overflow-hidden rounded-2xl border border-border/60 bg-card shadow-soft">
        {orders.length === 0 ? (
          <div className="p-10 text-center text-sm text-muted-foreground">No orders match these filters</div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-[13px]">
              <thead>
                <tr className="border-b border-border bg-surface-muted/60 text-left text-[11.5px] font-medium uppercase tracking-wide text-muted-foreground">
                  <th className="px-4 py-3">Order</th>
                  <th className="px-4 py-3">Tenant</th>
                  <th className="px-4 py-3">Customer</th>
                  <th className="px-4 py-3">Placed</th>
                  <th className="px-4 py-3 text-right">Total</th>
                  <th className="px-4 py-3">Status</th>
                  <th className="px-4 py-3" />
                </tr>
              </thead>
              <tbody>
                {orders.slice(0, 200).map((o) => (
                  <RowMemo key={o.id} o={o} tenant={tenantMap.get(o.tenantSlug)} />
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
      {orders.length > 200 && (
        <p className="mt-3 text-center text-[11.5px] text-muted-foreground">Showing first 200 — refine filters to narrow down.</p>
      )}
    </div>
  );
}
