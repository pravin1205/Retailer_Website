import { createFileRoute } from "@tanstack/react-router";
import { useMemo, useState } from "react";
import { Download } from "lucide-react";
import { Bar, BarChart, CartesianGrid, Cell, Pie, PieChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { Button } from "@/components/ui/button";
import { LazyRevenueChart } from "@/components/admin/LazyCharts";
import { useTenantOrders } from "@/hooks/use-tenant-orders";
import { useTenantProducts } from "@/stores/admin";
import { formatCurrency } from "@/lib/format";
import { downloadCsv, toCsv } from "@/lib/csv";
import { cn } from "@/lib/utils";

export const Route = createFileRoute("/s/$tenant/admin/reports")({
  head: ({ params }) => ({ meta: [{ title: `Reports · ${params.tenant}` }] }),
  component: ReportsPage,
});

const RANGES = [
  { value: 7, label: "7d" },
  { value: 14, label: "14d" },
  { value: 30, label: "30d" },
] as const;

const PALETTE = ["var(--color-primary)", "var(--color-chart-2)", "var(--color-chart-3)", "var(--color-chart-4)", "var(--color-chart-5)"];

function ReportsPage() {
  const { tenant: slug } = Route.useParams();
  const orders = useTenantOrders(slug);
  const products = useTenantProducts(slug);
  const [range, setRange] = useState<7 | 14 | 30>(14);

  const data = useMemo(() => {
    const now = Date.now();
    const recent = orders.filter((o) => now - +new Date(o.placedAt) <= range * 86400_000);
    const valid = recent.filter((o) => o.status !== "cancelled");

    // Daily series
    const buckets: Record<string, { revenue: number; orders: number }> = {};
    for (let i = range - 1; i >= 0; i--) {
      const d = new Date(now - i * 86400_000);
      const key = d.toLocaleDateString("en-IN", { day: "numeric", month: "short" });
      buckets[key] = { revenue: 0, orders: 0 };
    }
    valid.forEach((o) => {
      const d = new Date(o.placedAt);
      const key = d.toLocaleDateString("en-IN", { day: "numeric", month: "short" });
      if (buckets[key]) {
        buckets[key].revenue += o.total;
        buckets[key].orders += 1;
      }
    });
    const series = Object.entries(buckets).map(([label, v]) => ({ label, ...v }));

    // Status breakdown
    const statusMap: Record<string, number> = {};
    recent.forEach((o) => (statusMap[o.status] = (statusMap[o.status] ?? 0) + 1));
    const status = Object.entries(statusMap).map(([name, value]) => ({ name: name.replace(/_/g, " "), value }));

    // Category mix
    const catMap: Record<string, number> = {};
    valid.forEach((o) =>
      o.items.forEach((it) => {
        const p = products.find((x) => x.id === it.productId);
        const c = p?.categorySlug ?? "other";
        catMap[c] = (catMap[c] ?? 0) + it.price * it.quantity;
      }),
    );
    const cats = Object.entries(catMap).map(([name, value]) => ({ name, value })).sort((a, b) => b.value - a.value);

    const totals = {
      revenue: valid.reduce((s, o) => s + o.total, 0),
      orders: valid.length,
      cancelled: recent.length - valid.length,
    };

    return { series, status, cats, totals };
  }, [orders, products, range]);

  return (
    <div className="mx-auto max-w-7xl px-4 py-6 md:px-6 md:py-8">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight md:text-3xl">Reports</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            {formatCurrency(data.totals.revenue)} revenue · {data.totals.orders} orders · {data.totals.cancelled} cancelled
          </p>
        </div>
        <div className="flex items-center gap-2">
          <div className="flex gap-1 rounded-full border border-border bg-card p-0.5">
            {RANGES.map((r) => (
              <button
                key={r.value}
                onClick={() => setRange(r.value)}
                className={cn(
                  "rounded-full px-3 py-1 text-[12px] font-medium",
                  range === r.value ? "bg-primary text-primary-foreground" : "text-muted-foreground hover:text-foreground",
                )}
              >
                {r.label}
              </button>
            ))}
          </div>
          <Button
            variant="outline"
            size="sm"
            className="rounded-full"
            onClick={() => downloadCsv(`revenue-${slug}-${range}d.csv`, toCsv(data.series))}
          >
            <Download className="h-3.5 w-3.5" /> CSV
          </Button>
        </div>
      </div>

      <section className="mt-6 rounded-3xl border border-border/60 bg-card p-5 shadow-soft md:p-6">
        <h2 className="text-base font-semibold">Revenue trend</h2>
        <div className="mt-4">
          <LazyRevenueChart data={data.series} />
        </div>
      </section>

      <div className="mt-4 grid gap-4 lg:grid-cols-2">
        <section className="rounded-3xl border border-border/60 bg-card p-5 shadow-soft md:p-6">
          <h2 className="text-base font-semibold">Orders by status</h2>
          <div className="mt-4 h-64">
            <ResponsiveContainer width="100%" height="100%">
              <PieChart>
                <Pie data={data.status} dataKey="value" nameKey="name" innerRadius={50} outerRadius={90} paddingAngle={2}>
                  {data.status.map((_, i) => (
                    <Cell key={i} fill={PALETTE[i % PALETTE.length]} />
                  ))}
                </Pie>
                <Tooltip contentStyle={{ borderRadius: 12, fontSize: 12, border: "1px solid var(--color-border)" }} />
              </PieChart>
            </ResponsiveContainer>
          </div>
          <ul className="mt-3 grid grid-cols-2 gap-2 text-[12px]">
            {data.status.map((s, i) => (
              <li key={s.name} className="flex items-center gap-2">
                <span className="h-2.5 w-2.5 rounded-full" style={{ background: PALETTE[i % PALETTE.length] }} />
                <span className="capitalize text-muted-foreground">{s.name}</span>
                <span className="ml-auto font-semibold tabular-nums">{s.value}</span>
              </li>
            ))}
          </ul>
        </section>

        <section className="rounded-3xl border border-border/60 bg-card p-5 shadow-soft md:p-6">
          <h2 className="text-base font-semibold">Revenue by category</h2>
          <div className="mt-4 h-64">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={data.cats} margin={{ top: 8, right: 8, left: -16, bottom: 0 }}>
                <CartesianGrid stroke="var(--color-border)" strokeDasharray="3 6" vertical={false} />
                <XAxis dataKey="name" tickLine={false} axisLine={false} tick={{ fontSize: 11, fill: "var(--color-muted-foreground)" }} />
                <YAxis tickLine={false} axisLine={false} tick={{ fontSize: 11, fill: "var(--color-muted-foreground)" }} tickFormatter={(v) => (v >= 1000 ? `${(v / 1000).toFixed(0)}k` : `${v}`)} />
                <Tooltip cursor={{ fill: "var(--color-surface-muted)" }} contentStyle={{ borderRadius: 12, fontSize: 12, border: "1px solid var(--color-border)" }} formatter={(v: number) => formatCurrency(v)} />
                <Bar dataKey="value" fill="var(--color-primary)" radius={[8, 8, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </section>
      </div>
    </div>
  );
}
