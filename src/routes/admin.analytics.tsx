import { createFileRoute } from "@tanstack/react-router";
import { useMemo, lazy, Suspense } from "react";
import { useSuspenseQuery } from "@tanstack/react-query";
import { api, qk } from "@/lib/api";
import { useOrdersStore } from "@/stores";
import { useAdminStore } from "@/stores/admin";
import { computePlatformMetrics } from "@/lib/superadmin";
import { formatCurrency } from "@/lib/format";
import { LazyRevenueChart, ChartFallback } from "@/components/admin/LazyCharts";

const RechartsBundle = lazy(() =>
  import("recharts").then((m) => ({
    default: (props: AdvancedChartsProps) => <AdvancedCharts {...props} R={m} />,
  })),
);

export const Route = createFileRoute("/admin/analytics")({
  head: () => ({ meta: [{ title: "Analytics · Platform admin" }] }),
  loader: ({ context }) => {
    context.queryClient.ensureQueryData({ queryKey: qk.tenants, queryFn: api.listTenants });
  },
  component: AnalyticsPage,
});

function AnalyticsPage() {
  const { data: tenants } = useSuspenseQuery({ queryKey: qk.tenants, queryFn: api.listTenants });
  const userOrders = useOrdersStore((s) => s.orders);
  const historical = useAdminStore((s) => s.historicalOrders);
  const overrides = useAdminStore((s) => s.orderStatusOverrides);

  const metrics = useMemo(() => {
    const all = [...userOrders, ...historical].map((o) => (overrides[o.id] ? { ...o, status: overrides[o.id] } : o));
    return computePlatformMetrics(all, tenants, 30);
  }, [userOrders, historical, overrides, tenants]);

  const peakHour = useMemo(() => metrics.hourly.reduce((p, c) => (c.count > p.count ? c : p), { hour: 0, count: 0 }), [metrics.hourly]);
  const maxHour = peakHour.count || 1;

  return (
    <div className="mx-auto max-w-7xl px-4 py-6 md:px-6 md:py-8">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight md:text-3xl">Advanced analytics</h1>
        <p className="mt-1 text-sm text-muted-foreground">
          GMV {formatCurrency(metrics.gmv)} · AOV {formatCurrency(Math.round(metrics.aov))} · {metrics.orders} valid orders · 30d
        </p>
      </div>

      <section className="mt-6 rounded-3xl border border-border/60 bg-card p-5 shadow-soft md:p-6">
        <h2 className="text-base font-semibold">Revenue trend</h2>
        <p className="text-[11.5px] text-muted-foreground">All tenants · 30d</p>
        <div className="mt-4">
          <LazyRevenueChart data={metrics.series} />
        </div>
      </section>

      <div className="mt-4 grid gap-4 lg:grid-cols-2">
        <section className="rounded-3xl border border-border/60 bg-card p-5 shadow-soft md:p-6">
          <h2 className="text-base font-semibold">Order volume by hour</h2>
          <p className="text-[11.5px] text-muted-foreground">Peak at {peakHour.hour.toString().padStart(2, "0")}:00 — {peakHour.count} orders</p>
          <div className="mt-4 grid grid-cols-12 gap-1">
            {metrics.hourly.map((h) => {
              const intensity = h.count / maxHour;
              return (
                <div key={h.hour} className="flex flex-col items-center gap-1">
                  <div
                    className="h-16 w-full rounded-md"
                    title={`${h.hour.toString().padStart(2, "0")}:00 — ${h.count}`}
                    style={{
                      background: `color-mix(in oklab, var(--color-primary) ${Math.round(intensity * 100)}%, var(--color-surface-muted))`,
                    }}
                  />
                  <span className="text-[9.5px] text-muted-foreground tabular-nums">{h.hour}</span>
                </div>
              );
            })}
          </div>
        </section>

        <section className="rounded-3xl border border-border/60 bg-card p-5 shadow-soft md:p-6">
          <h2 className="text-base font-semibold">Payment mix</h2>
          <p className="text-[11.5px] text-muted-foreground">How customers checked out</p>
          <Suspense fallback={<ChartFallback height={240} />}>
            <RechartsBundle kind="payment" data={metrics.paymentMix} />
          </Suspense>
        </section>
      </div>

      <section className="mt-4 rounded-3xl border border-border/60 bg-card p-5 shadow-soft md:p-6">
        <h2 className="text-base font-semibold">Tenant league</h2>
        <p className="text-[11.5px] text-muted-foreground">Performance over the last 30 days</p>
        <div className="mt-4 overflow-x-auto">
          <table className="w-full text-[13px]">
            <thead>
              <tr className="border-b border-border text-left text-[11.5px] font-medium uppercase tracking-wide text-muted-foreground">
                <th className="py-2.5">Tenant</th>
                <th className="py-2.5 text-right">Orders</th>
                <th className="py-2.5 text-right">GMV</th>
                <th className="py-2.5 text-right">AOV</th>
                <th className="py-2.5">Share</th>
              </tr>
            </thead>
            <tbody>
              {metrics.byTenant.map((t) => {
                const share = metrics.gmv ? (t.revenue / metrics.gmv) * 100 : 0;
                return (
                  <tr key={t.slug} className="border-b border-border last:border-0">
                    <td className="py-3">
                      <div className="flex items-center gap-2">
                        <span className="grid h-7 w-7 place-items-center rounded-lg text-[13px]" style={{ background: t.gradient }}>{t.emoji}</span>
                        <span className="font-medium">{t.name}</span>
                      </div>
                    </td>
                    <td className="py-3 text-right tabular-nums">{t.orders}</td>
                    <td className="py-3 text-right font-semibold tabular-nums">{formatCurrency(t.revenue)}</td>
                    <td className="py-3 text-right tabular-nums">{formatCurrency(Math.round(t.aov))}</td>
                    <td className="py-3">
                      <div className="flex items-center gap-2">
                        <div className="h-1.5 w-32 overflow-hidden rounded-full bg-surface-muted">
                          <div className="h-full rounded-full bg-primary" style={{ width: `${share}%` }} />
                        </div>
                        <span className="text-[11.5px] tabular-nums text-muted-foreground">{share.toFixed(1)}%</span>
                      </div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </section>

      <section className="mt-4 rounded-3xl border border-border/60 bg-card p-5 shadow-soft md:p-6">
        <h2 className="text-base font-semibold">Status cohorts</h2>
        <p className="text-[11.5px] text-muted-foreground">Delivery success rate by week</p>
        <ul className="mt-4 space-y-2">
          {metrics.cohort.map((c) => {
            const rate = c.placed ? (c.delivered / c.placed) * 100 : 0;
            return (
              <li key={c.week} className="flex items-center gap-3 text-[12.5px]">
                <span className="w-20 font-medium">{c.week}</span>
                <div className="h-2 flex-1 overflow-hidden rounded-full bg-surface-muted">
                  <div className="h-full rounded-full bg-success" style={{ width: `${rate}%` }} />
                </div>
                <span className="w-14 text-right tabular-nums text-muted-foreground">{rate.toFixed(0)}%</span>
                <span className="w-24 text-right text-[11px] text-muted-foreground">{c.delivered}/{c.placed} delivered</span>
              </li>
            );
          })}
        </ul>
      </section>
    </div>
  );
}

interface AdvancedChartsProps {
  kind: "payment";
  data: { name: string; value: number }[];
}

function AdvancedCharts({ kind, data, R }: AdvancedChartsProps & { R: typeof import("recharts") }) {
  const { ResponsiveContainer, PieChart, Pie, Cell, Tooltip, Legend } = R;
  const PALETTE = ["var(--color-primary)", "var(--color-chart-2)", "var(--color-chart-3)", "var(--color-chart-4)"];
  if (kind === "payment") {
    return (
      <div className="mt-4 h-60">
        <ResponsiveContainer width="100%" height="100%">
          <PieChart>
            <Pie data={data} dataKey="value" nameKey="name" innerRadius={50} outerRadius={88} paddingAngle={2}>
              {data.map((_, i) => (
                <Cell key={i} fill={PALETTE[i % PALETTE.length]} />
              ))}
            </Pie>
            <Tooltip contentStyle={{ borderRadius: 12, fontSize: 12, border: "1px solid var(--color-border)" }} />
            <Legend wrapperStyle={{ fontSize: 11 }} iconType="circle" />
          </PieChart>
        </ResponsiveContainer>
      </div>
    );
  }
  return null;
}
