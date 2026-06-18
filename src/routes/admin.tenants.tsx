import { createFileRoute, Link } from "@tanstack/react-router";
import { useSuspenseQuery, useQueryClient, useQuery } from "@tanstack/react-query";
import { useState } from "react";
import {
  Star, StarOff, ExternalLink, Settings2,
  CheckCircle2, XCircle, Clock, ChevronDown, ChevronUp,
  Building2, MapPin, FileText, User, Loader2,
} from "lucide-react";
import { toast } from "sonner";
import { api, qk } from "@/lib/api";
import { useAdminStore } from "@/stores/admin";
import type { TenantAccent } from "@/lib/types";
import { cn } from "@/lib/utils";

export const Route = createFileRoute("/admin/tenants")({
  head: () => ({ meta: [{ title: "Tenants · Platform admin" }] }),
  loader: ({ context }) => {
    context.queryClient.ensureQueryData({ queryKey: qk.tenants, queryFn: api.listTenants });
  },
  component: TenantsPage,
});

const ACCENTS: { key: TenantAccent; color: string }[] = [
  { key: "emerald", color: "oklch(0.62 0.14 158)" },
  { key: "orange",  color: "oklch(0.68 0.17 50)"  },
  { key: "purple",  color: "oklch(0.55 0.18 290)"  },
  { key: "rose",    color: "oklch(0.62 0.18 15)"   },
];

const PENDING_STATUSES = ["DRAFT", "PENDING", "PENDING_VERIFICATION", "UNDER_REVIEW"];

function DetailRow({ label, value }: { label: string; value?: string | null }) {
  if (!value) return null;
  return (
    <div className="flex gap-2 text-[12.5px]">
      <span className="w-36 shrink-0 text-muted-foreground">{label}</span>
      <span className="font-medium break-all">{value}</span>
    </div>
  );
}

/** Inline component for approving/rejecting/reviewing a tenant */
function ApprovalPanel({
  slug,
  currentStatus,
  onDone,
}: {
  slug: string;
  currentStatus: string;
  onDone: () => void;
}) {
  const [notes, setNotes]     = useState("");
  const [loading, setLoading] = useState(false);

  const { data: detail, isLoading: detailLoading } = useQuery({
    queryKey: ["tenant-detail", slug],
    queryFn:  () => api.getTenantDetail(slug),
    staleTime: 0,
  });

  const s = (detail?.settings ?? {}) as Record<string, string>;

  const handleApproveReject = async (action: "APPROVE" | "REJECT") => {
    if (action === "REJECT" && !notes.trim()) {
      toast.error("Please enter a rejection reason"); return;
    }
    setLoading(true);
    try {
      await api.approveTenant(slug, action, notes || undefined);
      toast.success(action === "APPROVE" ? "Store approved and activated!" : "KYC rejected — seller notified");
      onDone();
    } catch (err: unknown) {
      toast.error((err as { message?: string })?.message ?? "Action failed");
    } finally {
      setLoading(false);
    }
  };

  const handleMarkUnderReview = async () => {
    setLoading(true);
    try {
      await api.markTenantUnderReview(slug);
      toast.success("Marked as Under Review — seller has been notified");
      onDone();
    } catch (err: unknown) {
      toast.error((err as { message?: string })?.message ?? "Action failed");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="mt-4 space-y-4 rounded-2xl border border-border/60 bg-surface-muted p-4">

      {detailLoading ? (
        <div className="flex items-center gap-2 text-[12.5px] text-muted-foreground">
          <Loader2 className="h-4 w-4 animate-spin" /> Loading seller details…
        </div>
      ) : detail ? (
        <div className="grid gap-4 sm:grid-cols-2">

          {/* Business Info */}
          <div className="rounded-xl border border-border bg-card p-3 space-y-1.5">
            <p className="mb-2 flex items-center gap-1.5 text-[11px] font-semibold uppercase tracking-wide text-muted-foreground">
              <Building2 className="h-3.5 w-3.5" /> Business Info
            </p>
            <DetailRow label="Store Name"     value={String(detail.name ?? "")} />
            <DetailRow label="Slug"           value={String(detail.slug ?? "")} />
            <DetailRow label="Category"       value={String(detail.category ?? "")} />
            <DetailRow label="Business Type"  value={s.business_type} />
            <DetailRow label="Business Email" value={s.business_email} />
            <DetailRow label="Contact"        value={s.contact_phone} />
            <DetailRow label="Description"    value={String(detail.description ?? "")} />
          </div>

          {/* Address */}
          <div className="rounded-xl border border-border bg-card p-3 space-y-1.5">
            <p className="mb-2 flex items-center gap-1.5 text-[11px] font-semibold uppercase tracking-wide text-muted-foreground">
              <MapPin className="h-3.5 w-3.5" /> Store Address
            </p>
            <DetailRow label="Address Line 1" value={s.address_line1} />
            <DetailRow label="Address Line 2" value={s.address_line2} />
            <DetailRow label="City"           value={s.address_city} />
            <DetailRow label="State"          value={s.address_state} />
            <DetailRow label="Pincode"        value={s.address_pincode} />
            <DetailRow label="Delivery Radius" value={s.deliveryRadiusKm ? `${s.deliveryRadiusKm} km` : undefined} />
          </div>

          {/* KYC Documents */}
          <div className="rounded-xl border border-border bg-card p-3 space-y-1.5">
            <p className="mb-2 flex items-center gap-1.5 text-[11px] font-semibold uppercase tracking-wide text-muted-foreground">
              <FileText className="h-3.5 w-3.5" /> KYC Documents
            </p>
            <DetailRow label="Aadhaar"     value={s.kyc_aadhaar} />
            <DetailRow label="PAN"         value={s.kyc_pan} />
            <DetailRow label="GST"         value={s.kyc_gst || "Not provided"} />
            <DetailRow label="KYC Status"  value={s.kyc_status} />
            {s.kyc_document_urls && (
              <div className="flex gap-2 text-[12.5px]">
                <span className="w-36 shrink-0 text-muted-foreground">Documents</span>
                <div className="space-y-1">
                  {s.kyc_document_urls.split(",").map((url, i) => (
                    <a key={i} href={url} target="_blank" rel="noopener noreferrer"
                      className="block text-primary underline underline-offset-2 hover:opacity-80">
                      Document {i + 1}
                    </a>
                  ))}
                </div>
              </div>
            )}
          </div>

          {/* Owner */}
          <div className="rounded-xl border border-border bg-card p-3 space-y-1.5">
            <p className="mb-2 flex items-center gap-1.5 text-[11px] font-semibold uppercase tracking-wide text-muted-foreground">
              <User className="h-3.5 w-3.5" /> Owner
            </p>
            <DetailRow label="Owner User ID"   value={String(detail.ownerUserId ?? "")} />
            <DetailRow label="Submitted at"    value={detail.createdAt ? new Date(String(detail.createdAt)).toLocaleString("en-IN") : undefined} />
            <DetailRow label="Onboarding Step" value={String(detail.onboardingStep ?? "")} />
            {s.kyc_review_notes && (
              <DetailRow label="Review Notes" value={s.kyc_review_notes} />
            )}
          </div>
        </div>
      ) : null}

      {/* Review actions */}
      <div>
        <p className="mb-2 text-[12.5px] font-medium">Review notes</p>
        <textarea
          value={notes}
          onChange={(e) => setNotes(e.target.value)}
          placeholder="Add notes or rejection reason…"
          rows={2}
          className="w-full rounded-xl border border-border bg-card px-3 py-2 text-[13px] outline-none focus:ring-2 focus:ring-primary/30"
        />
        <div className="mt-3 flex flex-wrap gap-2">
          {currentStatus !== "UNDER_REVIEW" && (
            <button
              onClick={handleMarkUnderReview}
              disabled={loading}
              className="inline-flex h-8 items-center gap-1.5 rounded-full bg-blue-100 px-4 text-[12px] font-semibold text-blue-700 hover:bg-blue-200 disabled:opacity-50"
            >
              <Clock className="h-3.5 w-3.5" />
              Start Review
            </button>
          )}
          <button
            onClick={() => handleApproveReject("APPROVE")}
            disabled={loading}
            className="inline-flex h-8 items-center gap-1.5 rounded-full bg-emerald-600 px-4 text-[12px] font-semibold text-white hover:bg-emerald-700 disabled:opacity-50"
          >
            <CheckCircle2 className="h-3.5 w-3.5" />
            {loading ? "Processing…" : "Approve"}
          </button>
          <button
            onClick={() => handleApproveReject("REJECT")}
            disabled={loading}
            className="inline-flex h-8 items-center gap-1.5 rounded-full bg-red-100 px-4 text-[12px] font-semibold text-red-700 hover:bg-red-200 disabled:opacity-50"
          >
            <XCircle className="h-3.5 w-3.5" />
            Reject
          </button>
        </div>
      </div>
    </div>
  );
}

function TenantsPage() {
  const qc = useQueryClient();
  const { data: tenants } = useSuspenseQuery({ queryKey: qk.tenants, queryFn: api.listTenants });
  const patchTenant = useAdminStore((s) => s.patchTenant);

  const [expandedApproval, setExpandedApproval] = useState<string | null>(null);

  // Split tenants: pending first, then active
  const pendingTenants = tenants.filter((t) => PENDING_STATUSES.includes(t.status ?? ""));
  const activeTenants  = tenants.filter((t) => !PENDING_STATUSES.includes(t.status ?? ""));

  const handleApprovalDone = () => {
    setExpandedApproval(null);
    qc.invalidateQueries({ queryKey: qk.tenants });
  };

  const TenantCard = ({ t }: { t: typeof tenants[0] }) => {
    const status = t.status ?? "ACTIVE";
    const isPending = PENDING_STATUSES.includes(status);

    return (
      <div className={cn(
        "rounded-3xl border bg-card p-4 shadow-soft md:p-5",
        isPending ? "border-amber-200" : "border-border/60",
      )}>
        <div className="flex flex-wrap items-center gap-4">
          <span className="grid h-14 w-14 shrink-0 place-items-center rounded-2xl text-2xl" style={{ background: t.bannerGradient }}>
            {t.logoEmoji}
          </span>

          <div className="min-w-0 flex-1">
            <div className="flex flex-wrap items-center gap-2">
              <h3 className="text-base font-semibold">{t.name}</h3>

              {/* Status badge */}
              {isPending ? (
                <span className={cn(
                  "flex items-center gap-1 rounded-full px-2 py-0.5 text-[10.5px] font-semibold",
                  status === "UNDER_REVIEW"
                    ? "bg-blue-100 text-blue-800"
                    : status === "DRAFT"
                    ? "bg-surface border border-border text-muted-foreground"
                    : "bg-amber-100 text-amber-800",
                )}>
                  <Clock className="h-2.5 w-2.5" />
                  {status === "UNDER_REVIEW" ? "Under Review" : status === "DRAFT" ? "Draft" : "Pending Review"}
                </span>
              ) : t.featured ? (
                <span className="rounded-full bg-primary-soft px-2 py-0.5 text-[10.5px] font-semibold text-accent-foreground">
                  Featured
                </span>
              ) : null}

              <span className="rounded-full border border-border bg-surface px-2 py-0.5 text-[10.5px] text-muted-foreground">
                {t.category}
              </span>
            </div>
            <p className="mt-0.5 text-[12.5px] text-muted-foreground">{t.tagline}</p>
            <div className="mt-1.5 flex flex-wrap items-center gap-3 text-[11px] text-muted-foreground">
              {isPending ? (
                <span className="text-amber-700">KYC awaiting approval · {t.slug}</span>
              ) : (
                <>
                  <span>⭐ {t.rating} ({t.reviewCount.toLocaleString("en-IN")})</span>
                  <span>· {t.deliveryMinutes} min</span>
                  <span>· min ₹{t.minOrder}</span>
                </>
              )}
            </div>
          </div>

          <div className="flex flex-wrap items-center gap-2">
            {!isPending && (
              <>
                <div className="flex items-center gap-1 rounded-full border border-border bg-surface p-1">
                  {ACCENTS.map((a) => (
                    <button
                      key={a.key}
                      title={a.key}
                      onClick={() => {
                        patchTenant(t.slug, { accent: a.key });
                        toast.success(`${t.name} accent → ${a.key}`);
                      }}
                      className={
                        "h-5 w-5 rounded-full ring-2 ring-offset-1 transition " +
                        (t.accent === a.key ? "ring-foreground" : "ring-transparent")
                      }
                      style={{ background: a.color }}
                    />
                  ))}
                </div>
                <button
                  onClick={() => {
                    patchTenant(t.slug, { featured: !t.featured });
                    toast.success(t.featured ? `${t.name} unfeatured` : `${t.name} featured`);
                  }}
                  className="inline-flex h-9 items-center gap-1.5 rounded-full border border-border bg-surface px-3 text-[12px] font-medium hover:bg-surface-muted"
                >
                  {t.featured ? <StarOff className="h-3.5 w-3.5" /> : <Star className="h-3.5 w-3.5" />}
                  {t.featured ? "Unfeature" : "Feature"}
                </button>
              </>
            )}

            {isPending ? (
              <button
                onClick={() => setExpandedApproval(expandedApproval === t.slug ? null : t.slug)}
                className="inline-flex h-9 items-center gap-1.5 rounded-full bg-amber-600 px-3 text-[12px] font-semibold text-white hover:bg-amber-700"
              >
                Review KYC
                {expandedApproval === t.slug ? <ChevronUp className="h-3.5 w-3.5" /> : <ChevronDown className="h-3.5 w-3.5" />}
              </button>
            ) : (
              <Link
                to="/s/$tenant/admin"
                params={{ tenant: t.slug }}
                className="inline-flex h-9 items-center gap-1.5 rounded-full bg-foreground px-3 text-[12px] font-semibold text-background hover:opacity-90"
              >
                <Settings2 className="h-3.5 w-3.5" /> Manage
              </Link>
            )}

            <Link
              to="/s/$tenant"
              params={{ tenant: t.slug }}
              className="inline-flex h-9 items-center gap-1.5 rounded-full border border-border bg-surface px-3 text-[12px] font-medium hover:bg-surface-muted"
            >
              <ExternalLink className="h-3.5 w-3.5" /> View
            </Link>
          </div>
        </div>

        {/* Inline approval panel */}
        {isPending && expandedApproval === t.slug && (
          <ApprovalPanel slug={t.slug} currentStatus={status} onDone={handleApprovalDone} />
        )}
      </div>
    );
  };

  return (
    <div className="mx-auto max-w-7xl px-4 py-6 md:px-6 md:py-8">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight md:text-3xl">Tenants</h1>
        <p className="mt-1 text-sm text-muted-foreground">{tenants.length} storefronts on Marketly</p>
      </div>

      {/* Pending approval section */}
      {pendingTenants.length > 0 && (
        <section className="mt-6">
          <h2 className="mb-3 flex items-center gap-2 text-base font-semibold">
            <Clock className="h-4 w-4 text-amber-600" />
            Pending approval
            <span className="rounded-full bg-amber-100 px-2 py-0.5 text-[11px] font-semibold text-amber-800">
              {pendingTenants.length}
            </span>
          </h2>
          <div className="grid gap-3">
            {pendingTenants.map((t) => <TenantCard key={t.slug} t={t} />)}
          </div>
        </section>
      )}

      {/* Active tenants */}
      <section className={pendingTenants.length > 0 ? "mt-8" : "mt-6"}>
        {pendingTenants.length > 0 && (
          <h2 className="mb-3 text-base font-semibold">Active stores</h2>
        )}
        <div className="grid gap-3">
          {activeTenants.map((t) => <TenantCard key={t.slug} t={t} />)}
        </div>
      </section>
    </div>
  );
}
