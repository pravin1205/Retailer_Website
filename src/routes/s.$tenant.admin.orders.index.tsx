import { createFileRoute } from "@tanstack/react-router";
import { useMemo, useState } from "react";
import { Download, Search } from "lucide-react";
import { OrdersTable } from "@/components/admin/OrdersTable";
import { useTenantOrders } from "@/hooks/use-tenant-orders";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { cn } from "@/lib/utils";
import { downloadCsv, toCsv } from "@/lib/csv";
import type { Order } from "@/lib/types";

export const Route = createFileRoute("/s/$tenant/admin/orders/")({
  head: ({ params }) => ({ meta: [{ title: `Orders · ${params.tenant}` }] }),
  component: OrdersListPage,
});

const TABS: { value: Order["status"] | "all"; label: string }[] = [
  { value: "all", label: "All" },
  { value: "placed", label: "Placed" },
  { value: "packing", label: "Packing" },
  { value: "out_for_delivery", label: "Out for delivery" },
  { value: "delivered", label: "Delivered" },
  { value: "cancelled", label: "Cancelled" },
];

function OrdersListPage() {
  const { tenant: slug } = Route.useParams();
  const orders = useTenantOrders(slug);
  const [tab, setTab] = useState<(typeof TABS)[number]["value"]>("all");
  const [q, setQ] = useState("");

  const counts = useMemo(() => {
    const c: Record<string, number> = { all: orders.length };
    orders.forEach((o) => (c[o.status] = (c[o.status] ?? 0) + 1));
    return c;
  }, [orders]);

  const filtered = useMemo(() => {
    let list = orders;
    if (tab !== "all") list = list.filter((o) => o.status === tab);
    if (q) {
      const s = q.toLowerCase();
      list = list.filter(
        (o) =>
          o.id.toLowerCase().includes(s) ||
          o.customer?.name.toLowerCase().includes(s) ||
          o.customer?.email?.toLowerCase().includes(s) ||
          o.address.pincode.includes(s),
      );
    }
    return list;
  }, [orders, tab, q]);

  return (
    <div className="mx-auto max-w-7xl px-4 py-6 md:px-6 md:py-8">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight md:text-3xl">Orders</h1>
          <p className="mt-1 text-sm text-muted-foreground">{counts.all} total · {counts.placed ?? 0} pending</p>
        </div>
        <Button
          variant="outline"
          size="sm"
          className="rounded-full"
          onClick={() => {
            const rows = filtered.map((o) => ({
              id: o.id,
              placed_at: o.placedAt,
              status: o.status,
              customer: o.customer?.name ?? "",
              email: o.customer?.email ?? "",
              items: o.items.reduce((n, it) => n + it.quantity, 0),
              total: o.total,
              payment: o.payment,
            }));
            downloadCsv(`orders-${slug}.csv`, toCsv(rows));
          }}
        >
          <Download className="h-3.5 w-3.5" /> Export CSV
        </Button>
      </div>

      {/* Tabs */}
      <div className="mt-5 flex flex-wrap items-center gap-2 overflow-x-auto">
        {TABS.map((t) => (
          <button
            key={t.value}
            onClick={() => setTab(t.value)}
            className={cn(
              "shrink-0 rounded-full border px-3 py-1.5 text-[12.5px] font-medium transition-colors",
              tab === t.value
                ? "border-primary bg-primary text-primary-foreground"
                : "border-border bg-card hover:border-primary/40",
            )}
          >
            {t.label} <span className="ml-1 opacity-70">{counts[t.value] ?? 0}</span>
          </button>
        ))}
        <div className="ml-auto relative w-full md:w-72">
          <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
          <Input value={q} onChange={(e) => setQ(e.target.value)} placeholder="Search orders…" className="pl-9" />
        </div>
      </div>

      <div className="mt-5">
        <OrdersTable orders={filtered} tenantSlug={slug} empty="No orders match these filters" />
      </div>
    </div>
  );
}
