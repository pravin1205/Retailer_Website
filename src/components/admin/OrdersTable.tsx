import { Link } from "@tanstack/react-router";
import type { Order } from "@/lib/types";
import { formatCurrency, formatDateTime } from "@/lib/format";
import { OrderStatusBadge } from "./OrderStatusBadge";
import { ChevronRight } from "lucide-react";

interface Props {
  orders: Order[];
  tenantSlug: string;
  empty?: React.ReactNode;
  compact?: boolean;
}

export function OrdersTable({ orders, tenantSlug, empty, compact }: Props) {
  if (orders.length === 0) {
    return <div className="rounded-2xl border border-dashed border-border p-10 text-center text-sm text-muted-foreground">{empty ?? "No orders yet"}</div>;
  }

  return (
    <div className="overflow-hidden rounded-2xl border border-border/60 bg-card shadow-soft">
      <div className="hidden md:block">
        <table className="w-full text-[13px]">
          <thead>
            <tr className="border-b border-border bg-surface-muted/60 text-left text-[11.5px] font-medium uppercase tracking-wide text-muted-foreground">
              <th className="px-4 py-3">Order</th>
              <th className="px-4 py-3">Customer</th>
              <th className="px-4 py-3">Placed</th>
              <th className="px-4 py-3">Items</th>
              <th className="px-4 py-3 text-right">Total</th>
              <th className="px-4 py-3">Status</th>
              <th className="px-4 py-3" />
            </tr>
          </thead>
          <tbody>
            {orders.map((o) => (
              <tr key={o.id} className="border-b border-border last:border-0 transition-colors hover:bg-surface-muted/40">
                <td className="px-4 py-3 font-mono text-[12px]">#{o.id.slice(-6).toUpperCase()}</td>
                <td className="px-4 py-3">
                  <div className="font-medium">{o.customer?.name ?? "Guest"}</div>
                  <div className="text-[11px] text-muted-foreground">{o.address.line2 ?? o.address.city}</div>
                </td>
                <td className="px-4 py-3 text-muted-foreground">{formatDateTime(o.placedAt)}</td>
                <td className="px-4 py-3 text-muted-foreground">{o.items.reduce((n, it) => n + it.quantity, 0)} items</td>
                <td className="px-4 py-3 text-right font-semibold tabular-nums">{formatCurrency(o.total)}</td>
                <td className="px-4 py-3"><OrderStatusBadge status={o.status} /></td>
                <td className="px-4 py-3 text-right">
                  <Link
                    to="/s/$tenant/admin/orders/$id"
                    params={{ tenant: tenantSlug, id: o.id }}
                    className="inline-flex items-center gap-1 text-[12px] font-medium text-primary hover:underline"
                  >
                    View <ChevronRight className="h-3.5 w-3.5" />
                  </Link>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* Mobile cards */}
      <ul className="divide-y divide-border md:hidden">
        {orders.map((o) => (
          <li key={o.id}>
            <Link
              to="/s/$tenant/admin/orders/$id"
              params={{ tenant: tenantSlug, id: o.id }}
              className="flex items-start gap-3 px-4 py-3 hover:bg-surface-muted/50"
            >
              <span className="grid h-10 w-10 place-items-center rounded-xl bg-primary-soft text-lg">{o.items[0]?.imageEmoji ?? "🧾"}</span>
              <div className="min-w-0 flex-1">
                <div className="flex items-center justify-between gap-2">
                  <span className="truncate text-[13px] font-medium">{o.customer?.name ?? "Guest"}</span>
                  <span className="text-[13px] font-semibold tabular-nums">{formatCurrency(o.total)}</span>
                </div>
                <div className="mt-0.5 flex items-center justify-between gap-2 text-[11px] text-muted-foreground">
                  <span>#{o.id.slice(-6).toUpperCase()} · {formatDateTime(o.placedAt)}</span>
                  <OrderStatusBadge status={o.status} />
                </div>
              </div>
            </Link>
          </li>
        ))}
      </ul>

      {!compact && null}
    </div>
  );
}
