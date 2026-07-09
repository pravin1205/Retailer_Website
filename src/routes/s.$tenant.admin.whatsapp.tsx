import { createFileRoute } from "@tanstack/react-router";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import {
  MessageSquare,
  CheckCircle2,
  XCircle,
  Loader2,
  Lock,
  PhoneCall,
  Zap,
  AlertTriangle,
  ExternalLink,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { toast } from "sonner";
import { cn } from "@/lib/utils";
import {
  qk,
  getWhatsappStatus,
  connectWhatsapp,
  disconnectWhatsapp,
} from "@/lib/api";
import { useMergedTenant } from "@/stores/admin";

export const Route = createFileRoute("/s/$tenant/admin/whatsapp")({
  head: ({ params }) => ({ meta: [{ title: `WhatsApp · ${params.tenant}` }] }),
  component: WhatsappPage,
});

// ── Helpers ────────────────────────────────────────────────────────────────────

/** Format ISO timestamp to "23 Jun 2026, 10:42 AM" */
function fmtDate(iso: string | undefined): string {
  if (!iso) return "";
  return new Date(iso).toLocaleString("en-IN", {
    day: "numeric",
    month: "short",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}

// ── Page ───────────────────────────────────────────────────────────────────────

function WhatsappPage() {
  const { tenant: slug } = Route.useParams();
  const tenant = useMergedTenant(slug);
  const qc = useQueryClient();

  // Fetch current WhatsApp connection status from the backend
  const { data: status, isLoading } = useQuery({
    queryKey: qk.whatsapp(slug),
    queryFn: () => getWhatsappStatus(slug),
  });

  // Mutations
  const connectMutation = useMutation({
    mutationFn: (payload: { phoneNumberId: string; displayNumber: string; authCode: string }) =>
      connectWhatsapp(slug, payload),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: qk.whatsapp(slug) });
      toast.success("WhatsApp number connected successfully");
    },
    onError: (err: { message?: string }) => {
      toast.error(err?.message ?? "Failed to connect WhatsApp");
    },
  });

  const disconnectMutation = useMutation({
    mutationFn: () => disconnectWhatsapp(slug),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: qk.whatsapp(slug) });
      toast.success("WhatsApp number disconnected");
    },
    onError: (err: { message?: string }) => {
      toast.error(err?.message ?? "Failed to disconnect");
    },
  });

  const isPremium = tenant?.subscriptionPlan === "PREMIUM";
  const isConnected = !!status?.connected;

  // Manual input state — used as a fallback until Meta's Embedded Signup
  // JavaScript SDK is fully integrated. The seller pastes their phone_number_id
  // from the Meta developer portal and enters their display number.
  const [showManualForm, setShowManualForm] = useState(false);
  const [manualPhoneNumberId, setManualPhoneNumberId] = useState("");
  const [manualDisplayNumber, setManualDisplayNumber] = useState("");
  const [manualAuthCode, setManualAuthCode] = useState("");

  // ── Embedded Signup handler ─────────────────────────────────────────────────
  // In production this launches Meta's JavaScript SDK popup. On completion,
  // Meta calls this callback with { phoneNumberId, displayPhoneNumber, code }.
  // For the current build, the seller fills in the values manually from the
  // Meta Business Manager dashboard.
  const handleEmbeddedSignup = () => {
    setShowManualForm(true);
  };

  const handleManualConnect = () => {
    if (!manualPhoneNumberId.trim() || !manualDisplayNumber.trim() || !manualAuthCode.trim()) {
      toast.error("All three fields are required.");
      return;
    }
    connectMutation.mutate({
      phoneNumberId: manualPhoneNumberId.trim(),
      displayNumber: manualDisplayNumber.trim(),
      authCode: manualAuthCode.trim(),
    });
    setShowManualForm(false);
    setManualPhoneNumberId("");
    setManualDisplayNumber("");
    setManualAuthCode("");
  };

  return (
    <div className="mx-auto max-w-3xl px-4 py-6 md:px-6 md:py-8">
      {/* ── Page header ── */}
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight md:text-3xl">
            WhatsApp Ordering
          </h1>
          <p className="mt-1 text-sm text-muted-foreground">
            Let customers order directly from your WhatsApp number
          </p>
        </div>
        {isPremium && (
          <span className="inline-flex items-center gap-1.5 rounded-full border border-emerald-200 bg-emerald-50 px-3 py-1 text-[11.5px] font-semibold text-emerald-700">
            <Zap className="h-3 w-3" />
            Premium feature — active
          </span>
        )}
      </div>

      <div className="mt-6 space-y-5">

        {/* ── Plan gate — shown only for non-premium sellers ── */}
        {!isPremium && (
          <PlanGateCard />
        )}

        {/* ── Connection status card ── */}
        <section
          className={cn(
            "rounded-3xl border border-border/60 bg-card p-5 shadow-soft md:p-6",
            !isPremium && "pointer-events-none select-none opacity-50",
          )}
        >
          <div className="flex items-start justify-between gap-4">
            <div className="flex items-center gap-3">
              <div className="grid h-10 w-10 shrink-0 place-items-center rounded-2xl bg-[#dcfce7] text-[#166534]">
                <MessageSquare className="h-5 w-5" />
              </div>
              <div>
                <h2 className="text-base font-semibold">
                  {isLoading
                    ? "Checking connection…"
                    : isConnected
                    ? "WhatsApp Connected"
                    : "WhatsApp Not Connected"}
                </h2>
                <p className="mt-0.5 text-[12.5px] text-muted-foreground">
                  {isLoading ? (
                    "Loading status…"
                  ) : isConnected ? (
                    <>
                      <span className="font-medium text-foreground">
                        {status?.displayNumber}
                      </span>
                      {status?.connectedAt && (
                        <> · Connected {fmtDate(status.connectedAt)}</>
                      )}
                    </>
                  ) : (
                    "Connect your WhatsApp Business number to start receiving orders"
                  )}
                </p>
              </div>
            </div>

            {/* Status icon */}
            {!isLoading && (
              isConnected ? (
                <CheckCircle2 className="h-5 w-5 shrink-0 text-emerald-500" />
              ) : (
                <XCircle className="h-5 w-5 shrink-0 text-muted-foreground/40" />
              )
            )}
          </div>

          {/* Action buttons */}
          <div className="mt-5 flex flex-wrap gap-3">
            {isLoading ? (
              <Button disabled variant="outline" className="rounded-full" size="sm">
                <Loader2 className="h-3.5 w-3.5 animate-spin" />
                Loading…
              </Button>
            ) : isConnected ? (
              <>
                <Button
                  variant="outline"
                  size="sm"
                  className="rounded-full"
                  onClick={() => {
                    // Opens a WhatsApp deep-link so the seller can test the
                    // connection by messaging their own number
                    const num = status?.displayNumber?.replace(/\D/g, "");
                    if (num) window.open(`https://wa.me/${num}`, "_blank");
                  }}
                >
                  <PhoneCall className="h-3.5 w-3.5" />
                  Test connection
                </Button>
                <Button
                  variant="destructive"
                  size="sm"
                  className="rounded-full"
                  disabled={disconnectMutation.isPending}
                  onClick={() => {
                    if (
                      window.confirm(
                        "Disconnect WhatsApp? Customers will no longer be able to order via WhatsApp until you reconnect.",
                      )
                    ) {
                      disconnectMutation.mutate();
                    }
                  }}
                >
                  {disconnectMutation.isPending ? (
                    <Loader2 className="h-3.5 w-3.5 animate-spin" />
                  ) : (
                    <XCircle className="h-3.5 w-3.5" />
                  )}
                  {disconnectMutation.isPending ? "Disconnecting…" : "Disconnect"}
                </Button>
              </>
            ) : (
              <Button
                size="sm"
                className="rounded-full"
                disabled={connectMutation.isPending || !isPremium}
                onClick={handleEmbeddedSignup}
              >
                {connectMutation.isPending ? (
                  <Loader2 className="h-3.5 w-3.5 animate-spin" />
                ) : (
                  <MessageSquare className="h-3.5 w-3.5" />
                )}
                {connectMutation.isPending ? "Connecting…" : "Connect WhatsApp"}
              </Button>
            )}
          </div>

          {/* ── Manual connection form (dev/setup fallback) ── */}
          {showManualForm && !isConnected && (
            <div className="mt-5 space-y-3 rounded-2xl border border-border/60 bg-surface-muted/50 p-4">
              <p className="text-[12.5px] font-medium text-foreground">
                Enter your WhatsApp Business details from the{" "}
                <a
                  href="https://business.facebook.com/wa/manage/phone-numbers/"
                  target="_blank"
                  rel="noopener noreferrer"
                  className="text-primary underline underline-offset-2"
                >
                  Meta Business Manager
                </a>
              </p>
              <div className="grid gap-2">
                <input
                  type="text"
                  placeholder="Phone Number ID (e.g. 123456789012345)"
                  value={manualPhoneNumberId}
                  onChange={(e) => setManualPhoneNumberId(e.target.value)}
                  className="w-full rounded-xl border border-border bg-background px-3 py-2 text-[13px] placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-primary/30"
                />
                <input
                  type="text"
                  placeholder="Display number (e.g. +919876543210)"
                  value={manualDisplayNumber}
                  onChange={(e) => setManualDisplayNumber(e.target.value)}
                  className="w-full rounded-xl border border-border bg-background px-3 py-2 text-[13px] placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-primary/30"
                />
                <input
                  type="text"
                  placeholder="Auth code from Meta Embedded Signup"
                  value={manualAuthCode}
                  onChange={(e) => setManualAuthCode(e.target.value)}
                  className="w-full rounded-xl border border-border bg-background px-3 py-2 text-[13px] placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-primary/30"
                />
              </div>
              <div className="flex gap-2">
                <Button
                  size="sm"
                  className="rounded-full"
                  disabled={connectMutation.isPending}
                  onClick={handleManualConnect}
                >
                  {connectMutation.isPending
                    ? <Loader2 className="h-3.5 w-3.5 animate-spin" />
                    : <MessageSquare className="h-3.5 w-3.5" />}
                  {connectMutation.isPending ? "Connecting…" : "Confirm"}
                </Button>
                <Button
                  size="sm"
                  variant="outline"
                  className="rounded-full"
                  onClick={() => setShowManualForm(false)}
                >
                  Cancel
                </Button>
              </div>
            </div>
          )}
        </section>

        {/* ── Important notice about WhatsApp Business App ── */}
        {isPremium && !isConnected && (
          <section className="rounded-3xl border border-amber-200 bg-amber-50 p-5 md:p-6">
            <div className="flex gap-3">
              <AlertTriangle className="mt-0.5 h-5 w-5 shrink-0 text-amber-600" />
              <div className="space-y-1">
                <p className="text-[13px] font-semibold text-amber-900">
                  Before you connect — read this
                </p>
                <p className="text-[12.5px] text-amber-800">
                  When you connect your number to Marketly, the{" "}
                  <strong>WhatsApp Business App on that phone will stop receiving messages.</strong>{" "}
                  All customer conversations will be managed through your Marketly dashboard instead.
                </p>
                <p className="mt-2 text-[12.5px] text-amber-800">
                  We recommend using a dedicated number for WhatsApp ordering — separate
                  from your personal or staff communication number.
                </p>
              </div>
            </div>
          </section>
        )}

        {/* ── How it works (always visible) ── */}
        <HowItWorksCard isPremium={isPremium} isConnected={isConnected} />

        {/* ── Message preview card (visible only when connected) ── */}
        {isConnected && <MessagePreviewCard />}

      </div>
    </div>
  );
}

// ── Plan gate card ─────────────────────────────────────────────────────────────

function PlanGateCard() {
  return (
    <section className="overflow-hidden rounded-3xl border border-border/60 bg-card shadow-soft">
      {/* Gradient header strip matching the page's premium feel */}
      <div className="bg-gradient-to-r from-emerald-600 to-emerald-500 px-6 py-5">
        <div className="flex items-center gap-3">
          <div className="grid h-10 w-10 place-items-center rounded-2xl bg-white/20">
            <Lock className="h-5 w-5 text-white" />
          </div>
          <div>
            <p className="text-[13px] font-semibold text-white">Premium Feature</p>
            <p className="text-[12px] text-white/80">
              WhatsApp ordering is available on the Premium plan
            </p>
          </div>
        </div>
      </div>
      <div className="px-6 py-5">
        <p className="text-[13px] text-muted-foreground">
          Upgrade to <span className="font-semibold text-foreground">Premium (₹3,999/month)</span> to
          let customers place orders by messaging your WhatsApp number in text or voice.
          WhatsApp ordering tools alone cost ₹2,500 – ₹7,000/month elsewhere — you get
          everything included.
        </p>
        <div className="mt-4 flex flex-wrap gap-3">
          <Button size="sm" className="rounded-full">
            <Zap className="h-3.5 w-3.5" />
            Upgrade to Premium
          </Button>
          <Button
            asChild
            variant="outline"
            size="sm"
            className="rounded-full"
          >
            <a
              href="https://marketly.in/pricing"
              target="_blank"
              rel="noopener noreferrer"
            >
              <ExternalLink className="h-3.5 w-3.5" />
              View pricing
            </a>
          </Button>
        </div>
      </div>
    </section>
  );
}

// ── How it works card ──────────────────────────────────────────────────────────

function HowItWorksCard({
  isPremium,
  isConnected,
}: {
  isPremium: boolean;
  isConnected: boolean;
}) {
  const steps = [
    {
      icon: "1",
      title: "Connect your number",
      body: "Link your existing WhatsApp Business number to Marketly in one click using Meta's secure signup flow.",
    },
    {
      icon: "2",
      title: "Customers message you",
      body: "Customers send a WhatsApp text or voice note to your number — in any Indian language.",
    },
    {
      icon: "3",
      title: "AI processes the order",
      body: "Marketly understands the order, confirms items and price, and collects payment — all in the chat.",
    },
    {
      icon: "4",
      title: "Order arrives on your dashboard",
      body: "The order appears in your dashboard exactly like any app order — same format, same workflow.",
    },
  ];

  return (
    <section
      className={cn(
        "rounded-3xl border border-border/60 bg-card p-5 shadow-soft md:p-6",
        !isPremium && "opacity-60",
      )}
    >
      <h2 className="mb-4 text-base font-semibold">How it works</h2>
      <ol className="space-y-4">
        {steps.map((s) => (
          <li key={s.icon} className="flex items-start gap-3">
            <span
              className={cn(
                "grid h-7 w-7 shrink-0 place-items-center rounded-full text-[12px] font-bold",
                isPremium && isConnected
                  ? "bg-emerald-100 text-emerald-700"
                  : "bg-surface-muted text-muted-foreground",
              )}
            >
              {s.icon}
            </span>
            <div>
              <p className="text-[13px] font-semibold">{s.title}</p>
              <p className="mt-0.5 text-[12px] text-muted-foreground">{s.body}</p>
            </div>
          </li>
        ))}
      </ol>
    </section>
  );
}

// ── Message preview card ───────────────────────────────────────────────────────

/**
 * Shows example WhatsApp messages customers will receive so the seller
 * understands exactly what their customers experience.
 */
function MessagePreviewCard() {
  const messages = [
    {
      from: "customer",
      text: "Hi, I want 2 Amul butter and 1kg basmati rice",
    },
    {
      from: "store",
      text: "Got it! Here's your order:\n🧈 Amul Butter 500g × 2 = ₹220\n🍚 Basmati Rice 1kg × 1 = ₹180\n\nTotal: ₹400\n\nReply PAY to confirm and pay.",
    },
    {
      from: "customer",
      text: "PAY",
    },
    {
      from: "store",
      text: "✅ Order #MKT-001 confirmed!\nDelivery: Evening 5–9 PM\n\nWe'll update you as it's packed.",
    },
  ];

  return (
    <section className="rounded-3xl border border-border/60 bg-card p-5 shadow-soft md:p-6">
      <h2 className="mb-1 text-base font-semibold">Message preview</h2>
      <p className="mb-4 text-[12.5px] text-muted-foreground">
        This is what a typical WhatsApp ordering conversation looks like for your customers.
      </p>

      {/* Chat bubble mockup */}
      <div className="rounded-2xl bg-[#e5ddd5] p-4 space-y-2">
        {messages.map((m, i) => (
          <div
            key={i}
            className={cn(
              "flex",
              m.from === "customer" ? "justify-end" : "justify-start",
            )}
          >
            <div
              className={cn(
                "max-w-[85%] rounded-2xl px-3.5 py-2.5 text-[12.5px] leading-relaxed shadow-sm whitespace-pre-line",
                m.from === "customer"
                  ? "rounded-br-sm bg-[#dcf8c6] text-[#111b21]"
                  : "rounded-bl-sm bg-white text-[#111b21]",
              )}
            >
              {m.from === "store" && (
                <span className="mb-1 block text-[10.5px] font-semibold text-emerald-600">
                  Your Store
                </span>
              )}
              {m.text}
            </div>
          </div>
        ))}
      </div>
    </section>
  );
}
