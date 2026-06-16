import { lazy, Suspense, type ComponentProps } from "react";

const RevenueChartReal = lazy(() =>
  import("@/components/admin/RevenueChart").then((m) => ({ default: m.RevenueChart })),
);

function ChartFallback({ height = 256 }: { height?: number }) {
  return (
    <div
      className="w-full animate-pulse rounded-2xl bg-surface-muted"
      style={{ height }}
      aria-label="Loading chart"
    />
  );
}

export function LazyRevenueChart(props: ComponentProps<typeof RevenueChartReal>) {
  return (
    <Suspense fallback={<ChartFallback height={256} />}>
      <RevenueChartReal {...props} />
    </Suspense>
  );
}

export { ChartFallback };
