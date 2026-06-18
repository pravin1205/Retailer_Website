import { createFileRoute, Link } from "@tanstack/react-router";
import { useEffect, useState } from "react";
import { RefreshCw, CheckCircle2, Clock, XCircle, Store, ArrowRight } from "lucide-react";
import { Button } from "@/components/ui/button";
import { PageTransition } from "@/components/motion/PageTransition";
import { useOnboardingStore } from "@/stores";
import { api } from "@/lib/api";
import { cn } from "@/lib/utils";

export const Route = createFileRoute("/onboarding/seller/status")({
  head: () => ({ meta: [{ title: "Application status · Marketly" }] }),
  component: SellerStatusPage,
});

interface StatusConfig {
  icon:    React.ReactNode;
  label:   string;
  color:   string;
  bg:      string;
  message: string;
}

const STATUS_CONFIG: Record<string, StatusConfig> = {
  DRAFT: {
    icon:    <Clock className="h-5 w-5" />,
    label:   "Draft",
    color:   "text-muted-foreground",
    bg:      "bg-surface",
    message: "Your application is incomplete. Continue the onboarding to submit KYC.",
  },
  PENDING: {
    icon:    <Clock className="h-5 w-5" />,
    label:   "Pending",
    color:   "text-amber-700",
    bg:      "bg-amber-50",
    message: "Your application has been received and is queued for review.",
  },
  PENDING_VERIFICATION: {
    icon:    <Clock className="h-5 w-5" />,
    label:   "Pending Review",
    color:   "text-amber-700",
    bg:      "bg-amber-50",
    message: "Your KYC documents have been submitted. Our team will begin review shortly.",
  },
  UNDER_REVIEW: {
    icon:    <RefreshCw className="h-5 w-5 animate-spin" />,
    label:   "Under Review",
    color:   "text-blue-700",
    bg:      "bg-blue-50",
    message: "Our verification team is reviewing your documents. This usually takes 1 business day.",
  },
  APPROVED: {
    icon:    <CheckCircle2 className="h-5 w-5" />,
    label:   "Approved",
    color:   "text-emerald-700",
    bg:      "bg-emerald-50",
    message: "Congratulations! Your store has been approved and is now live.",
  },
  ACTIVE: {
    icon:    <CheckCircle2 className="h-5 w-5" />,
    label:   "Store Live",
    color:   "text-emerald-700",
    bg:      "bg-emerald-50",
    message: "Your store is live and accepting orders. Welcome to Marketly!",
  },
  REJECTED: {
    icon:    <XCircle className="h-5 w-5" />,
    label:   "Rejected",
    color:   "text-red-700",
    bg:      "bg-red-50",
    message: "Your KYC was not approved. Please review the feedback and resubmit.",
  },
};

const TIMELINE = [
  { label: "KYC submitted",       statuses: ["PENDING_VERIFICATION","UNDER_REVIEW","APPROVED","ACTIVE","REJECTED"] },
  { label: "Under review",        statuses: ["UNDER_REVIEW","APPROVED","ACTIVE","REJECTED"] },
  { label: "Approved",            statuses: ["APPROVED","ACTIVE"] },
  { label: "Store live",          statuses: ["ACTIVE"] },
];

function SellerStatusPage() {
  const tenantSlug = useOnboardingStore((s) => s.seller.tenantSlug);
  const resetSeller = useOnboardingStore((s) => s.resetSeller);
  const updateSeller = useOnboardingStore((s) => s.updateSeller);

  const [status, setStatus] = useState<string>("PENDING_VERIFICATION");
  const [loading, setLoading] = useState(true);
  const [lastChecked, setLastChecked] = useState<Date | null>(null);

  const refresh = async () => {
    if (!tenantSlug) return;
    setLoading(true);
    try {
      const data = await api.getSellerStatus(tenantSlug);
      setStatus(data.status);
      setLastChecked(new Date());
    } catch {
      // keep previous status
    } finally {
      setLoading(false);
    }
  };

  // Initial load + poll every 30s
  useEffect(() => {
    refresh();
    const interval = setInterval(refresh, 30_000);
    return () => clearInterval(interval);
  }, [tenantSlug]);

  if (!tenantSlug) {
    return (
      <PageTransition>
        <div className="mx-auto max-w-md text-center">
          <div className="rounded-3xl border border-border/60 bg-card p-8 shadow-card">
            <p className="text-muted-foreground">No active application found.</p>
            <Button asChild className="mt-4 rounded-full" variant="outline">
              <Link to="/onboarding/seller">Start seller onboarding</Link>
            </Button>
          </div>
        </div>
      </PageTransition>
    );
  }

  const config = STATUS_CONFIG[status] ?? STATUS_CONFIG["PENDING_VERIFICATION"];

  return (
    <PageTransition>
      <div className="mx-auto max-w-lg">
        {/* Status card */}
        <div className="rounded-3xl border border-border/60 bg-card p-7 shadow-card">
          <div className="flex items-start justify-between gap-3">
            <div>
              <p className="text-[11.5px] font-medium uppercase tracking-wider text-muted-foreground">
                Application status
              </p>
              <h1 className="mt-1 text-2xl font-semibold tracking-tight">
                {tenantSlug}
              </h1>
            </div>
            <span className={cn("flex items-center gap-1.5 rounded-full px-3 py-1.5 text-[12.5px] font-semibold", config.bg, config.color)}>
              {config.icon}
              {config.label}
            </span>
          </div>

          <p className="mt-4 text-sm text-muted-foreground">{config.message}</p>

          {/* Timeline */}
          <div className="mt-6 space-y-3">
            {TIMELINE.map((item, i) => {
              const done = item.statuses.includes(status);
              return (
                <div key={i} className="flex items-center gap-3">
                  <div className={cn("h-2.5 w-2.5 flex-shrink-0 rounded-full", done ? "bg-primary" : "bg-border")} />
                  <span className={cn("text-[13px]", done ? "font-medium text-foreground" : "text-muted-foreground")}>
                    {item.label}
                  </span>
                </div>
              );
            })}
          </div>

          {/* Actions based on status */}
          <div className="mt-6 space-y-3">
            {(status === "ACTIVE" || status === "APPROVED") && (
              <Button asChild className="h-11 w-full rounded-full bg-foreground text-sm font-semibold text-background hover:opacity-90">
                <Link to="/s/$tenant/admin" params={{ tenant: tenantSlug }}>
                  <Store className="mr-1.5 h-4 w-4" /> Go to store dashboard
                  <ArrowRight className="ml-1.5 h-4 w-4" />
                </Link>
              </Button>
            )}

            {status === "REJECTED" && (
              <Button
                onClick={() => { updateSeller({ step: 6 }); }}
                asChild
                className="h-11 w-full rounded-full bg-foreground text-sm font-semibold text-background hover:opacity-90"
              >
                <Link to="/onboarding/seller">Resubmit KYC</Link>
              </Button>
            )}

            {(status === "DRAFT") && (
              <Button asChild className="h-11 w-full rounded-full bg-foreground text-sm font-semibold text-background hover:opacity-90">
                <Link to="/onboarding/seller">Continue application</Link>
              </Button>
            )}
          </div>

          {/* Refresh / last checked */}
          <div className="mt-4 flex items-center justify-between text-[11.5px] text-muted-foreground">
            {lastChecked && <span>Last updated: {lastChecked.toLocaleTimeString()}</span>}
            <button
              onClick={refresh}
              disabled={loading}
              className="flex items-center gap-1 hover:text-foreground"
            >
              <RefreshCw className={cn("h-3 w-3", loading && "animate-spin")} />
              Refresh
            </button>
          </div>
        </div>

        {/* Info */}
        <p className="mt-4 text-center text-[11.5px] text-muted-foreground">
          Questions? Contact us at <a href="mailto:sellers@marketly.in" className="text-primary hover:underline">sellers@marketly.in</a>
        </p>
      </div>
    </PageTransition>
  );
}
