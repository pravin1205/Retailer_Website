import { createFileRoute, Link } from "@tanstack/react-router";
import { ClipboardList, Package, ArrowRight } from "lucide-react";
import { useQuery } from "@tanstack/react-query";
import { useOrdersStore, useAuthStore } from "@/stores";
import { api, qk } from "@/lib/api";
import { formatCurrency, formatDateTime } from "@/lib/format";
import { PageTransition } from "@/components/motion/PageTransition";
import { Button } from "@/components/ui/button";
import type { Order } from "@/lib/types";

export const Route = createFileRoute("/s/$tenant/orders/")({
  head: ({ params }) => ({ meta: [{ title: `Orders · ${params.tenant}` }] }),
  component: OrdersPage,
});

const STATUS_LABEL: Record<string, string> = {
  placed:           "Order placed",
  packing:          "Being packed",
  out_for_delivery: "Out for delivery",
  delivered:        "Delivered",
  cancelled:        "Cancelled",
};

function OrdersPage() {
  const { tenant: slug } = Route.useParams();
  const user             = useAuthStore((s) => s.user);
  const localOrders      = useOrdersStore((s) => s.orders.filter((o) => o.tenantSlug === slug));

  // Fetch real orders from backend when authenticated
  const { data: backendOrders, isLoading } = useQuery<Order[]>({
    queryKey: qk.orders(slug),
    queryFn:  () => api.listOrders(slug),
    enabled:  !!user,
    staleTime: 30_000,
  });

  // Authenticated users see backend orders; guests see local store
  const orders: Order[] = user
    ? (backendOrders ?? localOrders)
    : localOrders;

  return (
    <PageTransition>
      <div className="mx-auto max-w-4xl px-4 py-6 md:px-6 md:py-10">
        <h1 className="flex items-center gap-2 text-2xl font-semibold tracking-tight md:text-3xl">
          <ClipboardList className="h-6 w-6" /> Your orders
        </h1>

        {isLoading ? (
          <div className="mt-10 space-y-3">
            {[1, 2, 3].map((i) => (
              <div key={i} className="h-24 animate-pulse rounded-3xl bg-muted/50" />
            ))}
          </div>
        ) : orders.length === 0 ? (
          <div className="mt-10 grid place-items-center rounded-3xl border border-dashed border-border bg-surface-muted/40 px-6 py-20 text-center">
            <Package className="h-10 w-10 text-muted-foreground" />
            <p className="mt-3 text-sm font-medium">No orders yet</p>
            <Button asChild className="mt-4 rounded-full">
              <Link to="/s/$tenant" params={{ tenant: slug }}>Start shopping</Link>
            </Button>
          </div>
        ) : (
          <ul className="mt-6 space-y-3">
            {orders.map((o) => (
              <li key={o.id}>
                <Link
                  to="/s/$tenant/orders/$id"
                  params={{ tenant: slug, id: o.id }}
                  className="flex items-center gap-4 rounded-3xl border border-border/60 bg-card p-4 shadow-soft transition-shadow hover:shadow-pop"
                >
                  <div className="flex -space-x-2">
                    {o.items.slice(0, 3).map((it, i) => (
                      <span
                        key={i}
                        className="grid h-12 w-12 place-items-center rounded-2xl border-2 border-card text-2xl"
                        style={{ background: it.imageBg ?? "#f3f4f6" }}
                      >
                        {it.imageEmoji ?? "📦"}
                      </span>
                    ))}
                  </div>
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-2">
                      <span className="font-semibold">{o.id}</span>
                      <span className="rounded-full bg-primary-soft px-2 py-0.5 text-[11px] font-semibold text-accent-foreground">
                        {STATUS_LABEL[o.status] ?? o.status}
                      </span>
                    </div>
                    <div className="mt-0.5 text-[12.5px] text-muted-foreground">
                      {formatDateTime(o.placedAt)} · {o.items.length} items · {formatCurrency(o.total)}
                    </div>
                  </div>
                  <ArrowRight className="h-4 w-4 text-muted-foreground" />
                </Link>
              </li>
            ))}
          </ul>
        )}
      </div>
    </PageTransition>
  );
}
