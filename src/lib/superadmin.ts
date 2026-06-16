import type { Order, Tenant } from "./types";

export interface PlatformMetrics {
  gmv: number;
  takeRate: number;
  netRevenue: number;
  orders: number;
  cancelled: number;
  customers: number;
  aov: number;
  series: { label: string; revenue: number; orders: number }[];
  byTenant: { slug: string; name: string; emoji: string; gradient: string; revenue: number; orders: number; aov: number }[];
  paymentMix: { name: string; value: number }[];
  hourly: { hour: number; count: number }[];
  cohort: { week: string; placed: number; delivered: number; cancelled: number }[];
}

const PLATFORM_TAKE = 0.08;

export function computePlatformMetrics(
  orders: Order[],
  tenants: Tenant[],
  rangeDays = 14,
): PlatformMetrics {
  const now = Date.now();
  const recent = orders.filter((o) => now - +new Date(o.placedAt) <= rangeDays * 86400_000);
  const valid = recent.filter((o) => o.status !== "cancelled");

  const gmv = valid.reduce((s, o) => s + o.total, 0);
  const netRevenue = gmv * PLATFORM_TAKE;
  const customers = new Set(recent.map((o) => o.customer?.email ?? o.address.pincode)).size;
  const aov = valid.length ? gmv / valid.length : 0;

  // Daily series
  const buckets: Record<string, { revenue: number; orders: number }> = {};
  for (let i = rangeDays - 1; i >= 0; i--) {
    const d = new Date(now - i * 86400_000);
    const key = d.toLocaleDateString("en-IN", { day: "numeric", month: "short" });
    buckets[key] = { revenue: 0, orders: 0 };
  }
  valid.forEach((o) => {
    const key = new Date(o.placedAt).toLocaleDateString("en-IN", { day: "numeric", month: "short" });
    if (buckets[key]) {
      buckets[key].revenue += o.total;
      buckets[key].orders += 1;
    }
  });
  const series = Object.entries(buckets).map(([label, v]) => ({ label, ...v }));

  // By tenant
  const tMap = new Map<string, { revenue: number; orders: number }>();
  valid.forEach((o) => {
    const cur = tMap.get(o.tenantSlug) ?? { revenue: 0, orders: 0 };
    cur.revenue += o.total;
    cur.orders += 1;
    tMap.set(o.tenantSlug, cur);
  });
  const byTenant = tenants
    .map((t) => {
      const m = tMap.get(t.slug) ?? { revenue: 0, orders: 0 };
      return {
        slug: t.slug,
        name: t.name,
        emoji: t.logoEmoji,
        gradient: t.bannerGradient,
        revenue: m.revenue,
        orders: m.orders,
        aov: m.orders ? m.revenue / m.orders : 0,
      };
    })
    .sort((a, b) => b.revenue - a.revenue);

  // Payment mix
  const pmMap: Record<string, number> = {};
  recent.forEach((o) => (pmMap[o.payment] = (pmMap[o.payment] ?? 0) + 1));
  const paymentMix = Object.entries(pmMap).map(([name, value]) => ({ name, value }));

  // Hourly heatmap
  const hourly: { hour: number; count: number }[] = Array.from({ length: 24 }, (_, i) => ({ hour: i, count: 0 }));
  recent.forEach((o) => {
    const h = new Date(o.placedAt).getHours();
    hourly[h].count += 1;
  });

  // Status cohort by week-ago bucket
  const cohortMap: Record<string, { placed: number; delivered: number; cancelled: number }> = {};
  recent.forEach((o) => {
    const weeksAgo = Math.floor((now - +new Date(o.placedAt)) / (7 * 86400_000));
    const label = weeksAgo === 0 ? "This week" : `${weeksAgo}w ago`;
    const cur = cohortMap[label] ?? { placed: 0, delivered: 0, cancelled: 0 };
    cur.placed += 1;
    if (o.status === "delivered") cur.delivered += 1;
    if (o.status === "cancelled") cur.cancelled += 1;
    cohortMap[label] = cur;
  });
  const cohort = Object.entries(cohortMap).map(([week, v]) => ({ week, ...v }));

  return {
    gmv,
    takeRate: PLATFORM_TAKE,
    netRevenue,
    orders: valid.length,
    cancelled: recent.length - valid.length,
    customers,
    aov,
    series,
    byTenant,
    paymentMix,
    hourly,
    cohort,
  };
}
