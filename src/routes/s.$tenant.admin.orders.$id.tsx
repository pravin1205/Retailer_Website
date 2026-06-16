import { createFileRoute, Link } from "@tanstack/react-router";
import { ArrowLeft, Printer } from "lucide-react";
import { useTenantOrders } from "@/hooks/use-tenant-orders";
import { useAdminStore, useMergedTenant } from "@/stores/admin";
import { OrderStatusBadge } from "@/components/admin/OrderStatusBadge";
import { Button } from "@/components/ui/button";
import { formatCurrency, formatDateTime } from "@/lib/format";
import type { Order } from "@/lib/types";
import { toast } from "sonner";

export const Route = createFileRoute("/s/$tenant/admin/orders/$id")({
  head: ({ params }) => ({ meta: [{ title: `Order ${params.id} · ${params.tenant}` }] }),
  component: OrderDetail,
});

const NEXT: Record<Order["status"], Order["status"] | null> = {
  placed: "packing",
  packing: "out_for_delivery",
  out_for_delivery: "delivered",
  delivered: null,
  cancelled: null,
};

function OrderDetail() {
  const { tenant: slug, id } = Route.useParams();
  const orders = useTenantOrders(slug);
  const order = orders.find((o) => o.id === id);
  const updateStatus = useAdminStore((s) => s.updateOrderStatus);
  const tenant = useMergedTenant(slug);

  if (!order) {
    return (
      <div className="mx-auto max-w-3xl px-4 py-16 text-center">
        <h1 className="text-lg font-semibold">Order not found</h1>
        <Button asChild className="mt-4 rounded-full">
          <Link to="/s/$tenant/admin/orders" params={{ tenant: slug }}>Back to orders</Link>
        </Button>
      </div>
    );
  }

  const next = NEXT[order.status];

  return (
    <div className="mx-auto max-w-5xl px-4 py-6 md:px-6 md:py-8">
      <Link to="/s/$tenant/admin/orders" params={{ tenant: slug }} className="inline-flex items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground">
        <ArrowLeft className="h-4 w-4" /> All orders
      </Link>

      <div className="mt-3 flex flex-wrap items-center justify-between gap-3 print:hidden">
        <div>
          <h1 className="font-mono text-xl font-semibold md:text-2xl">#{order.id.slice(-8).toUpperCase()}</h1>
          <div className="mt-1 flex items-center gap-2 text-[12.5px] text-muted-foreground">
            <span>{formatDateTime(order.placedAt)}</span> · <OrderStatusBadge status={order.status} />
          </div>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          {next && (
            <Button
              onClick={() => {
                updateStatus(order.id, next);
                toast.success(`Moved to ${next.replace(/_/g, " ")}`);
              }}
              className="rounded-full"
            >
              Mark as {next.replace(/_/g, " ")}
            </Button>
          )}
          {order.status !== "cancelled" && order.status !== "delivered" && (
            <Button
              variant="outline"
              className="rounded-full"
              onClick={() => {
                updateStatus(order.id, "cancelled");
                toast.success("Order cancelled");
              }}
            >
              Cancel
            </Button>
          )}
          <Button variant="outline" className="rounded-full" onClick={() => window.print()}>
            <Printer className="h-3.5 w-3.5" /> Invoice
          </Button>
        </div>
      </div>

      <div className="mt-6 grid gap-4 lg:grid-cols-[1.6fr_1fr]">
        <section className="rounded-3xl border border-border/60 bg-card p-5 shadow-soft md:p-6">
          <h2 className="text-base font-semibold">Items</h2>
          <ul className="mt-4 divide-y divide-border">
            {order.items.map((it, idx) => (
              <li key={idx} className="flex items-center gap-3 py-3">
                <span className="grid h-12 w-12 place-items-center rounded-xl text-xl" style={{ background: it.imageBg }}>{it.imageEmoji}</span>
                <div className="min-w-0 flex-1">
                  <div className="line-clamp-1 text-sm font-medium">{it.name}</div>
                  <div className="text-[12px] text-muted-foreground">Qty {it.quantity} · {formatCurrency(it.price)} each</div>
                </div>
                <div className="text-sm font-semibold tabular-nums">{formatCurrency(it.price * it.quantity)}</div>
              </li>
            ))}
          </ul>

          <div className="mt-4 space-y-2 border-t border-border pt-4 text-sm">
            <Row label="Subtotal" value={formatCurrency(order.subtotal)} />
            {order.discount > 0 && <Row label="Discount" value={`- ${formatCurrency(order.discount)}`} />}
            <Row label="Delivery" value={order.delivery ? formatCurrency(order.delivery) : "Free"} />
            <Row label="Total" value={formatCurrency(order.total)} bold />
          </div>
        </section>

        <aside className="space-y-4">
          <section className="rounded-3xl border border-border/60 bg-card p-5 shadow-soft">
            <h2 className="text-sm font-semibold">Customer</h2>
            <div className="mt-3 text-[13px]">
              <div className="font-medium">{order.customer?.name ?? "Guest customer"}</div>
              {order.customer?.email && <div className="text-muted-foreground">{order.customer.email}</div>}
              {order.customer?.phone && <div className="text-muted-foreground">{order.customer.phone}</div>}
            </div>
          </section>
          <section className="rounded-3xl border border-border/60 bg-card p-5 shadow-soft">
            <h2 className="text-sm font-semibold">Delivery</h2>
            <div className="mt-3 text-[13px] text-muted-foreground">
              <div className="text-foreground">{order.address.line1}</div>
              {order.address.line2 && <div>{order.address.line2}</div>}
              <div>{order.address.city} · {order.address.pincode}</div>
              <div className="mt-2">Slot: <span className="text-foreground">{order.slot}</span></div>
              <div>Payment: <span className="text-foreground">{order.payment.toUpperCase()}</span></div>
            </div>
          </section>
          <section className="rounded-3xl border border-border/60 bg-card p-5 shadow-soft">
            <h2 className="text-sm font-semibold">Store</h2>
            <div className="mt-3 text-[13px]">
              <div className="font-medium">{tenant?.name}</div>
              <div className="text-muted-foreground">{tenant?.address}</div>
            </div>
          </section>
        </aside>
      </div>
    </div>
  );
}

function Row({ label, value, bold }: { label: string; value: string; bold?: boolean }) {
  return (
    <div className={"flex justify-between " + (bold ? "border-t border-border pt-3 text-base font-semibold" : "text-muted-foreground")}>
      <span>{label}</span>
      <span className={bold ? "" : "text-foreground"}>{value}</span>
    </div>
  );
}
