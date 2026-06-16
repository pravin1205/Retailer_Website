import type { LucideIcon } from "lucide-react";
import { ArrowDownRight, ArrowUpRight } from "lucide-react";
import { cn } from "@/lib/utils";

interface Props {
  label: string;
  value: string;
  delta?: number; // percent
  hint?: string;
  icon?: LucideIcon;
  tone?: "default" | "primary";
}

export function KpiCard({ label, value, delta, hint, icon: Icon, tone = "default" }: Props) {
  const positive = (delta ?? 0) >= 0;
  return (
    <div
      className={cn(
        "rounded-2xl border border-border/60 bg-card p-4 shadow-soft md:p-5",
        tone === "primary" && "bg-gradient-to-br from-primary-soft to-card",
      )}
    >
      <div className="flex items-center justify-between">
        <span className="text-[11.5px] font-medium uppercase tracking-wide text-muted-foreground">{label}</span>
        {Icon && (
          <span className="grid h-8 w-8 place-items-center rounded-xl bg-surface-muted text-primary">
            <Icon className="h-4 w-4" />
          </span>
        )}
      </div>
      <div className="mt-3 text-2xl font-semibold tabular-nums tracking-tight md:text-[26px]">{value}</div>
      <div className="mt-1 flex items-center gap-2 text-[11.5px]">
        {typeof delta === "number" && (
          <span
            className={cn(
              "inline-flex items-center gap-0.5 rounded-full px-1.5 py-0.5 font-medium",
              positive ? "bg-success/10 text-success" : "bg-destructive/10 text-destructive",
            )}
          >
            {positive ? <ArrowUpRight className="h-3 w-3" /> : <ArrowDownRight className="h-3 w-3" />}
            {Math.abs(delta).toFixed(1)}%
          </span>
        )}
        {hint && <span className="text-muted-foreground">{hint}</span>}
      </div>
    </div>
  );
}
