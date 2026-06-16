import { createFileRoute, Link } from "@tanstack/react-router";
import { useMemo } from "react";
import { IndianRupee, ShoppingBag, Users, Package, AlertTriangle, ArrowRight } from "lucide-react";
import { KpiCard } from "@/components/admin/KpiCard";
import { LazyRevenueChart } from "@/components/admin/LazyCharts";
import { OrdersTable } from "@/components/admin/OrdersTable";
import { EmptyState } from "@/components/admin/EmptyState";
import { useTenantOrders } from "@/hooks/use-tenant-orders";
import { useTenantProducts, useMergedTenant } from "@/stores/admin";
import { formatCurrency } from "@/lib/format";

export const Route = createFileRoute("/s/$tenant/admin/")({
  head: ({ params }) => ({ meta: [{ title: `Overview · ${params.tenant}` }] }),
  component: AdminOverview,
});

function AdminOverview() {
  const { tenant: slug } = Route.useParams();
  const tenant = useMergedTenant(slug);
  const orders = useTenantOrders(slug);
  const products = useTenantProducts(slug);

  const metrics = useMemo(() => {
    const now = Date.now();
    const last14 = orders.filter((o) => now - +new Date(o.placedAt) <= 14 * 86400_000 && o.status !== "cancelled");
    const last7 = last14.filter((o) => now - +new Date(o.placedAt) <= 7 * 86400_000);
    const prev7 = last14.filter((o) => {
      const d = now - +new Date(o.placedAt);
      return d > 7 * 86400_000 && d <= 14 * 86400_000;
    });
    const rev7 = last7.reduce((s, o) => s + o.total, 0);
    const revPrev = prev7.reduce((s, o) => s + o.total, 0);
    const revDelta = revPrev ? ((rev7 - revPrev) / revPrev) * 100 : 0;
    const customers = new Set(last14.map((o) => o.customer?.email ?? o.address.pincode)).size;
    const aov = last7.length ? rev7 / last7.length : 0;

    // Daily series for last 14 days
    const buckets: Record<string, { revenue: number; orders: number }> = {};
    for (let i = 13; i >= 0; i--) {
      const d = new Date(now - i * 86400_000);
      const key = d.toLocaleDateString("en-IN", { day: "numeric", month: "short" });
      buckets[key] = { revenue: 0, orders: 0 };
    }
    last14.forEach((o) => {
      const d = new Date(o.placedAt);
      const key = d.toLocaleDateString("en-IN", { day: "numeric", month: "short" });
      if (buckets[key]) {
        buckets[key].revenue += o.total;
        buckets[key].orders += 1;
      }
    });
    const series = Object.entries(buckets).map(([label, v]) => ({ label, ...v }));

    // Top products by units
    const unitsByProduct: Record<string, { name: string; emoji: string; bg: string; units: number; revenue: number }> = {};
    last14.forEach((o) =>
      o.items.forEach((it) => {
        const cur = unitsByProduct[it.productId] ?? { name: it.name, emoji: it.imageEmoji, bg: it.imageBg, units: 0, revenue: 0 };
        cur.units += it.quantity;
        cur.revenue += it.quantity * it.price;
        unitsByProduct[it.productId] = cur;
      }),
    );
    const top = Object.values(unitsByProduct).sort((a, b) => b.units - a.units).slice(0, 5);

    const lowStock = products.filter((p) => p.stock <= 10).slice(0, 6);

    return { rev7, revDelta, customers, aov, ordersCount: last7.length, series, top, lowStock };
  }, [orders, products]);

  return (
    <div className="mx-auto max-w-7xl px-4 py-6 md:px-6 md:py-8">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <p className="text-[11.5px] font-medium uppercase tracking-wide text-muted-foreground">Overview</p>
          <h1 className="text-2xl font-semibold tracking-tight md:text-3xl">Good to see you back</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            Here's how <span className="font-medium text-foreground">{tenant?.name}</span> is performing this week.
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Link
            to="/s/$tenant/admin/products/new"
            params={{ tenant: slug }}
            className="inline-flex h-9 items-center gap-1.5 rounded-full bg-primary px-4 text-[12.5px] font-semibold text-primary-foreground shadow-soft hover:opacity-90"
          >
            <Package className="h-3.5 w-3.5" /> Add product
          </Link>
        </div>
      </div>

      {/* KPIs */}
      <div className="mt-6 grid grid-cols-2 gap-3 md:grid-cols-4 md:gap-4">
        <KpiCard label="Revenue · 7d" value={formatCurrency(metrics.rev7)} delta={metrics.revDelta} icon={IndianRupee} tone="primary" />
        <KpiCard label="Orders · 7d" value={String(metrics.ordersCount)} hint="last 7 days" icon={ShoppingBag} />
        <KpiCard label="Avg order value" value={formatCurrency(Math.round(metrics.aov))} hint="per order" icon={IndianRupee} />
        <KpiCard label="Customers · 14d" value={String(metrics.customers)} hint="unique buyers" icon={Users} />
      </div>

      {/* Chart + Top products */}
      <div className="mt-6 grid gap-4 lg:grid-cols-[1.6fr_1fr]">
        <section className="rounded-3xl border border-border/60 bg-card p-5 shadow-soft md:p-6">
          <div className="flex items-center justify-between">
            <div>
              <h2 className="text-base font-semibold">Revenue</h2>
              <p className="text-[11.5px] text-muted-foreground">Last 14 days</p>
            </div>
            <Link to="/s/$tenant/admin/reports" params={{ tenant: slug }} className="text-[12px] font-medium text-primary hover:underline">
              View reports →
            </Link>
          </div>
          <div className="mt-4">
            <LazyRevenueChart data={metrics.series} />
          </div>
        </section>

        <section className="rounded-3xl border border-border/60 bg-card p-5 shadow-soft md:p-6">
          <div className="flex items-center justify-between">
            <h2 className="text-base font-semibold">Top products</h2>
            <Link to="/s/$tenant/admin/products" params={{ tenant: slug }} className="text-[12px] font-medium text-primary hover:underline">
              All
            </Link>
          </div>
          {metrics.top.length === 0 ? (
            <p className="mt-6 text-center text-sm text-muted-foreground">No sales yet</p>
          ) : (
            <ul className="mt-4 space-y-2.5">
              {metrics.top.map((t, i) => (
                <li key={t.name} className="flex items-center gap-3">
                  <span className="w-4 text-[11px] font-semibold text-muted-foreground tabular-nums">{i + 1}</span>
                  <span className="grid h-9 w-9 place-items-center rounded-xl text-lg" style={{ background: t.bg }}>{t.emoji}</span>
                  <div className="min-w-0 flex-1">
                    <div className="truncate text-[13px] font-medium">{t.name}</div>
                    <div className="text-[11px] text-muted-foreground">{t.units} units · {formatCurrency(t.revenue)}</div>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>

      {/* Recent orders + Low stock */}
      <div className="mt-6 grid gap-4 lg:grid-cols-[1.6fr_1fr]">
        <section>
          <div className="mb-3 flex items-center justify-between">
            <h2 className="text-base font-semibold">Recent orders</h2>
            <Link to="/s/$tenant/admin/orders" params={{ tenant: slug }} className="inline-flex items-center gap-1 text-[12px] font-medium text-primary hover:underline">
              All orders <ArrowRight className="h-3.5 w-3.5" />
            </Link>
          </div>
          <OrdersTable orders={orders.slice(0, 6)} tenantSlug={slug} empty="No orders yet" />
        </section>

        <section className="rounded-3xl border border-border/60 bg-card p-5 shadow-soft md:p-6">
          <div className="flex items-center justify-between">
            <h2 className="flex items-center gap-2 text-base font-semibold">
              <AlertTriangle className="h-4 w-4 text-warning" />
              Low stock
            </h2>
            <Link to="/s/$tenant/admin/products" params={{ tenant: slug }} className="text-[12px] font-medium text-primary hover:underline">
              Manage
            </Link>
          </div>
          {metrics.lowStock.length === 0 ? (
            <EmptyState title="All stocked up" description="No products under the low-stock threshold." />
          ) : (
            <ul className="mt-4 space-y-2.5">
              {metrics.lowStock.map((p) => (
                <li key={p.id} className="flex items-center gap-3">
                  <span className="grid h-9 w-9 place-items-center rounded-xl text-lg" style={{ background: p.imageBg }}>{p.imageEmoji}</span>
                  <div className="min-w-0 flex-1">
                    <Link to="/s/$tenant/admin/products/$id" params={{ tenant: slug, id: p.id }} className="line-clamp-1 text-[13px] font-medium hover:text-primary">
                      {p.name}
                    </Link>
                    <div className="text-[11px] text-muted-foreground">{p.stock} left · {formatCurrency(p.price)}</div>
                  </div>
                  <span className={"rounded-full px-2 py-0.5 text-[10.5px] font-medium " + (p.stock === 0 ? "bg-destructive/10 text-destructive" : "bg-warning/15 text-warning-foreground")}>
                    {p.stock === 0 ? "Out" : "Low"}
                  </span>
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>
    </div>
  );
}
