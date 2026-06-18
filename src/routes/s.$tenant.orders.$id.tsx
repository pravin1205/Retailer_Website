import { createFileRoute, Link, notFound } from "@tanstack/react-router";
import { useQuery } from "@tanstack/react-query";
import { useOrdersStore, useAuthStore } from "@/stores";
import { api, qk } from "@/lib/api";
import { formatCurrency, formatDateTime } from "@/lib/format";
import { PageTransition } from "@/components/motion/PageTransition";
import { Button } from "@/components/ui/button";
import { Check, Package, Truck, MapPin, ArrowLeft, CircleDot } from "lucide-react";
import { cn } from "@/lib/utils";

export const Route = createFileRoute("/s/$tenant/orders/$id")({
  head: ({ params }) => ({ meta: [{ title: `Order ${params.id}` }] }),
  component: OrderDetail,
});

const STEPS: { key: "placed" | "packing" | "out_for_delivery" | "delivered"; label: string; icon: typeof Check }[] = [
  { key: "placed",           label: "Order placed",    icon: Check   },
  { key: "packing",          label: "Packing",         icon: Package },
  { key: "out_for_delivery", label: "Out for delivery",icon: Truck   },
  { key: "delivered",        label: "Delivered",       icon: MapPin  },
];

function OrderDetail() {
  const { tenant: slug, id } = Route.useParams();
  const user      = useAuthStore((s) => s.user);
  const localOrder = useOrdersStore((s) => s.orders.find((o) => o.id === id));

  // For authenticated users, attempt to fetch from backend.
  // localOrder (guest checkout) is used as a fallback if present.
  const { data: backendOrder, isLoading } = useQuery({
    queryKey: qk.order(id, slug),
    queryFn:  () => api.getOrder(id, slug),
    enabled:  !!user && !localOrder,   // skip backend call if we already have it locally
    retry:    1,
  });

  const order = localOrder ?? backendOrder;

  // Show spinner while the backend is being queried for authenticated users
  if (isLoading) {
    return (
      <div className="mx-auto max-w-3xl px-4 py-20 text-center">
        <div className="mx-auto h-8 w-8 animate-spin rounded-full border-2 border-primary border-t-transparent" />
        <p className="mt-4 text-sm text-muted-foreground">Loading order…</p>
      </div>
    );
  }

  // Only throw notFound after we've given the backend a chance to respond
  if (!order) throw notFound();

  const currentIndex =
    order.status === "delivered"        ? 3 :
    order.status === "out_for_delivery" ? 2 :
    order.status === "packing"          ? 1 : 0;

  return (
    <PageTransition>
      <div className="mx-auto max-w-3xl px-4 py-6 md:px-6 md:py-10">
        <Link to="/s/$tenant/orders" params={{ tenant: slug }} className="inline-flex items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground">
          <ArrowLeft className="h-4 w-4" /> All orders
        </Link>

        <div className="mt-4 rounded-3xl border border-border/60 bg-card p-5 shadow-card md:p-6">
          <div className="flex items-start justify-between gap-3">
            <div>
              <h1 className="text-xl font-semibold">Order {order.id}</h1>
              <p className="text-[12.5px] text-muted-foreground">Placed {formatDateTime(order.placedAt)}</p>
            </div>
            <span className="rounded-full bg-success/10 px-3 py-1 text-[12px] font-semibold text-success">
              {order.status.replaceAll("_", " ")}
            </span>
          </div>

          {/* Steps */}
          <ol className="mt-6 grid grid-cols-4 gap-2">
            {STEPS.map((s, i) => {
              const done = i <= currentIndex;
              const Icon = s.icon;
              return (
                <li key={s.key} className="flex flex-col items-center text-center">
                  <div className={cn(
                    "grid h-9 w-9 place-items-center rounded-full border-2",
                    done ? "border-primary bg-primary text-primary-foreground" : "border-border bg-surface text-muted-foreground",
                  )}>
                    {done ? <Icon className="h-4 w-4" /> : <CircleDot className="h-4 w-4" />}
                  </div>
                  <span className={cn("mt-2 text-[11px]", done ? "font-semibold text-foreground" : "text-muted-foreground")}>
                    {s.label}
                  </span>
                </li>
              );
            })}
          </ol>
          <div className="relative mt-3 h-1 rounded-full bg-border">
            <div
              className="absolute inset-y-0 left-0 rounded-full bg-primary transition-all"
              style={{ width: `${(currentIndex / 3) * 100}%` }}
            />
          </div>
          <p className="mt-3 text-center text-[12.5px] text-muted-foreground">
            Estimated delivery within {order.slot}
          </p>
        </div>

        {/* Items + summary */}
        <div className="mt-5 grid gap-5 md:grid-cols-[1fr_280px]">
          <div className="rounded-3xl border border-border/60 bg-card p-5 shadow-soft">
            <h2 className="text-base font-semibold">Items</h2>
            <ul className="mt-3 space-y-3">
              {order.items.map((it, i) => (
                <li key={i} className="flex items-center gap-3">
                  <span
                    className="grid h-12 w-12 place-items-center rounded-2xl text-2xl"
                    style={{ background: it.imageBg }}
                  >
                    {it.imageEmoji}
                  </span>
                  <div className="min-w-0 flex-1">
                    <div className="line-clamp-1 text-sm font-medium">{it.name}</div>
                    <div className="text-[12px] text-muted-foreground">Qty {it.quantity}</div>
                  </div>
                  <span className="text-sm font-semibold">{formatCurrency(it.price * it.quantity)}</span>
                </li>
              ))}
            </ul>
          </div>

          <aside className="rounded-3xl border border-border/60 bg-card p-5 shadow-soft">
            <h2 className="text-base font-semibold">Summary</h2>
            <dl className="mt-3 space-y-2 text-sm">
              <div className="flex justify-between text-muted-foreground">
                <dt>Subtotal</dt>
                <dd className="text-foreground">{formatCurrency(order.subtotal)}</dd>
              </div>
              <div className="flex justify-between text-muted-foreground">
                <dt>Delivery</dt>
                <dd className="text-foreground">{order.delivery ? formatCurrency(order.delivery) : "Free"}</dd>
              </div>
              <div className="flex justify-between border-t border-border pt-2 font-semibold">
                <dt>Total</dt>
                <dd>{formatCurrency(order.total)}</dd>
              </div>
            </dl>
            <div className="mt-4 rounded-2xl bg-surface-muted p-3 text-[12.5px]">
              <div className="font-semibold">{order.address.label}</div>
              <div className="text-muted-foreground">
                {order.address.line1}
                {order.address.line2 ? `, ${order.address.line2}` : ""}, {order.address.city} {order.address.pincode}
              </div>
            </div>
            <Button asChild variant="outline" className="mt-4 w-full rounded-full">
              <Link to="/s/$tenant" params={{ tenant: slug }}>Continue shopping</Link>
            </Button>
          </aside>
        </div>
      </div>
    </PageTransition>
  );
}
