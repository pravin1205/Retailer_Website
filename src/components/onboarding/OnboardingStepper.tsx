import { Check } from "lucide-react";
import { cn } from "@/lib/utils";

interface Step {
  label: string;
}

interface Props {
  steps:   Step[];
  current: number; // 1-based
}

/**
 * Progress stepper used in both customer and seller onboarding flows.
 * Reuses the existing app design tokens (primary color, border, card, etc.)
 */
export function OnboardingStepper({ steps, current }: Props) {
  return (
    <div className="flex items-center gap-0">
      {steps.map((step, i) => {
        const idx       = i + 1;
        const isDone    = idx < current;
        const isActive  = idx === current;
        const isUpcoming= idx > current;

        return (
          <div key={step.label} className="flex items-center">
            {/* Circle */}
            <div className="flex flex-col items-center gap-1">
              <div
                className={cn(
                  "flex h-7 w-7 items-center justify-center rounded-full text-[11px] font-semibold transition-all",
                  isDone    && "bg-primary text-primary-foreground",
                  isActive  && "bg-foreground text-background ring-2 ring-foreground ring-offset-2",
                  isUpcoming && "border border-border bg-surface text-muted-foreground",
                )}
              >
                {isDone ? <Check className="h-3.5 w-3.5" /> : idx}
              </div>
              <span
                className={cn(
                  "hidden text-[10px] font-medium leading-none md:block",
                  isActive   && "text-foreground",
                  isDone     && "text-primary",
                  isUpcoming && "text-muted-foreground",
                )}
              >
                {step.label}
              </span>
            </div>

            {/* Connector line */}
            {i < steps.length - 1 && (
              <div
                className={cn(
                  "mx-1 h-[2px] w-8 rounded-full transition-colors md:w-12",
                  isDone ? "bg-primary" : "bg-border",
                )}
              />
            )}
          </div>
        );
      })}
    </div>
  );
}
