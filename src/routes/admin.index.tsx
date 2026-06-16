import { createFileRoute, Link } from "@tanstack/react-router";
import { useMemo } from "react";
import { useSuspenseQuery } from "@tanstack/react-query";
import { IndianRupee, ShoppingBag, Users, Building2, TrendingUp, ArrowRight } from "lucide-react";
import { api, qk } from "@/lib/api";
import { useTenantOrders } from "@/hooks/use-tenant-orders";
import { useOrdersStore } from "@/stores";
import { useAdminStore } from "@/stores/admin";
import { KpiCard } from "@/components/admin/KpiCard";
import { LazyRevenueChart } from "@/components/admin/LazyCharts";
import { formatCurrency } from "@/lib/format";
import { computePlatformMetrics } from "@/lib/superadmin";

export const Route = createFileRoute("/admin/")({
  head: () => ({ meta: [{ title: "Overview · Platform admin" }] }),
  loader: ({ context }) => {
    context.queryClient.ensureQueryData({ queryKey: qk.tenants, queryFn: api.listTenants });
  },
  component: SuperAdminOverview,
});

function SuperAdminOverview() {
  const { data: tenants } = useSuspenseQuery({ queryKey: qk.tenants, queryFn: api.listTenants });
  const userOrders = useOrdersStore((s) => s.orders);
  const historical = useAdminStore((s) => s.historicalOrders);
  const overrides = useAdminStore((s) => s.orderStatusOverrides);

  const metrics = useMemo(() => {
    const all = [...userOrders, ...historical].map((o) => (overrides[o.id] ? { ...o, status: overrides[o.id] } : o));
    return computePlatformMetrics(all, tenants, 14);
  }, [userOrders, historical, overrides, tenants]);

  return (
    <div className="mx-auto max-w-7xl px-4 py-6 md:px-6 md:py-8">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <p className="text-[11.5px] font-medium uppercase tracking-wide text-muted-foreground">Platform admin</p>
          <h1 className="text-2xl font-semibold tracking-tight md:text-3xl">Marketly at a glance</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            {tenants.length} active tenants · last 14 days
          </p>
        </div>
        <Link
          to="/admin/analytics"
          className="inline-flex h-9 items-center gap-1.5 rounded-full bg-foreground px-4 text-[12.5px] font-semibold text-background hover:opacity-90"
        >
          Advanced analytics <ArrowRight className="h-3.5 w-3.5" />
        </Link>
      </div>

      <div className="mt-6 grid grid-cols-2 gap-3 md:grid-cols-4 md:gap-4">
        <KpiCard label="GMV · 14d" value={formatCurrency(metrics.gmv)} icon={IndianRupee} tone="primary" hint="gross merch volume" />
        <KpiCard label={`Net (${Math.round(metrics.takeRate * 100)}% take)`} value={formatCurrency(Math.round(metrics.netRevenue))} icon={TrendingUp} />
        <KpiCard label="Orders" value={String(metrics.orders)} icon={ShoppingBag} hint={`${metrics.cancelled} cancelled`} />
        <KpiCard label="Customers" value={String(metrics.customers)} icon={Users} hint="unique buyers" />
      </div>

      <div className="mt-6 grid gap-4 lg:grid-cols-[1.6fr_1fr]">
        <section className="rounded-3xl border border-border/60 bg-card p-5 shadow-soft md:p-6">
          <div className="flex items-center justify-between">
            <div>
              <h2 className="text-base font-semibold">Platform revenue</h2>
              <p className="text-[11.5px] text-muted-foreground">All tenants combined · 14d</p>
            </div>
          </div>
          <div className="mt-4">
            <LazyRevenueChart data={metrics.series} />
          </div>
        </section>

        <section className="rounded-3xl border border-border/60 bg-card p-5 shadow-soft md:p-6">
          <div className="flex items-center justify-between">
            <h2 className="flex items-center gap-2 text-base font-semibold">
              <Building2 className="h-4 w-4" /> Top stores
            </h2>
            <Link to="/admin/tenants" className="text-[12px] font-medium text-primary hover:underline">All</Link>
          </div>
          <ul className="mt-4 space-y-2.5">
            {metrics.byTenant.slice(0, 6).map((t, i) => (
              <li key={t.slug} className="flex items-center gap-3">
                <span className="w-4 text-[11px] font-semibold text-muted-foreground tabular-nums">{i + 1}</span>
                <span className="grid h-9 w-9 place-items-center rounded-xl text-lg" style={{ background: t.gradient }}>
                  {t.emoji}
                </span>
                <div className="min-w-0 flex-1">
                  <Link to="/s/$tenant/admin" params={{ tenant: t.slug }} className="block truncate text-[13px] font-medium hover:text-primary">
                    {t.name}
                  </Link>
                  <div className="text-[11px] text-muted-foreground">{t.orders} orders · {formatCurrency(t.revenue)}</div>
                </div>
              </li>
            ))}
          </ul>
        </section>
      </div>
    </div>
  );
}
