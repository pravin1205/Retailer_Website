import { cn } from "@/lib/utils";
import type { Order } from "@/lib/types";

const META: Record<Order["status"], { label: string; cls: string }> = {
  placed: { label: "Placed", cls: "bg-blue-500/10 text-blue-700 ring-blue-500/20" },
  packing: { label: "Packing", cls: "bg-amber-500/10 text-amber-700 ring-amber-500/20" },
  out_for_delivery: { label: "Out for delivery", cls: "bg-purple-500/10 text-purple-700 ring-purple-500/20" },
  delivered: { label: "Delivered", cls: "bg-success/10 text-success ring-success/20" },
  cancelled: { label: "Cancelled", cls: "bg-destructive/10 text-destructive ring-destructive/20" },
};

export function OrderStatusBadge({ status }: { status: Order["status"] }) {
  const m = META[status];
  return (
    <span className={cn("inline-flex items-center rounded-full px-2 py-0.5 text-[10.5px] font-medium ring-1 ring-inset", m.cls)}>
      {m.label}
    </span>
  );
}
