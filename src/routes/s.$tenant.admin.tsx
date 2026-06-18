import { createFileRoute, Outlet, Link } from "@tanstack/react-router";
import { useQuery } from "@tanstack/react-query";
import React, { useState, useEffect, useCallback } from "react";
import { ShieldCheck, Store } from "lucide-react";
import { api, qk } from "@/lib/api";
import { useAuthStore } from "@/stores";
import { SidebarProvider, SidebarInset } from "@/components/ui/sidebar";
import { AdminSidebar } from "@/components/admin/AdminSidebar";
import { AdminTopbar } from "@/components/admin/AdminTopbar";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { toast } from "sonner";

export const Route = createFileRoute("/s/$tenant/admin")({
  head: ({ params }) => ({ meta: [{ title: `Dashboard · ${params.tenant}` }] }),
  loader: ({ context, params }) =>
    context.queryClient.ensureQueryData({
      queryKey: qk.tenant(params.tenant),
      queryFn: () => api.getTenant(params.tenant),
    }).catch(() => undefined),
  component: AdminLayout,
});

function AdminLayout() {
  const { tenant: slug } = Route.useParams();
  const { data: tenant } = useQuery({ queryKey: qk.tenant(slug), queryFn: () => api.getTenant(slug), retry: false });
  const user = useAuthStore((s) => s.user);

  if (!tenant) return null;

  if (user?.role !== "owner") {
    return <OwnerSignInWall tenantName={tenant.name} />;
  }

  return (
    <SidebarProvider>
      <AdminSidebar />
      <SidebarInset className="bg-surface-muted">
        <AdminTopbar />
        <div className="flex-1">
          <Outlet />
        </div>
      </SidebarInset>
    </SidebarProvider>
  );
}

function OwnerSignInWall({ tenantName }: { tenantName: string }) {
  const { tenant: slug } = Route.useParams();
  const login        = useAuthStore((s) => s.login);
  const loginFromOtp = useAuthStore((s) => s.loginFromOtp);
  const loading      = useAuthStore((s) => s.loading);

  // "otp" | "password"
  const [tab, setTab] = useState<"otp" | "password">("otp");
  const [error, setError] = useState<string | null>(null);

  // ── OTP tab state ─────────────────────────────────────────────────────
  const [phone,       setPhone]       = useState("");
  const [otp,         setOtp]         = useState("");
  const [otpSent,     setOtpSent]     = useState(false);
  const [sending,     setSending]     = useState(false);
  const [verifying,   setVerifying]   = useState(false);
  const [resendTimer, setResendTimer] = useState(0);

  useEffect(() => {
    if (resendTimer <= 0) return;
    const t = setInterval(() => setResendTimer((n) => (n > 0 ? n - 1 : 0)), 1000);
    return () => clearInterval(t);
  }, [resendTimer]);

  const handleSendOtp = useCallback(async () => {
    if (!/^[6-9]\d{9}$/.test(phone)) {
      setError("Enter a valid 10-digit mobile number"); return;
    }
    setError(null); setSending(true);
    try {
      await api.sendOtp(phone);
      setOtpSent(true);
      setResendTimer(30);
      toast.success("OTP sent to " + phone);
    } catch (err: unknown) {
      setError((err as { message?: string })?.message ?? "Failed to send OTP");
    } finally { setSending(false); }
  }, [phone]);

  const handleVerifyOtp = useCallback(async () => {
    if (otp.length !== 6) { setError("Enter the 6-digit OTP"); return; }
    setError(null); setVerifying(true);
    try {
      const result = await api.verifyOtp(phone, otp, { tenantSlug: slug, role: "TENANT_OWNER" });
      loginFromOtp(result, slug);
      toast.success("Signed in");
    } catch (err: unknown) {
      setError((err as { message?: string })?.message ?? "Invalid OTP");
    } finally { setVerifying(false); }
  }, [otp, phone, slug, loginFromOtp]);

  // ── Password tab state ────────────────────────────────────────────────
  const [email,    setEmail]    = useState("");
  const [password, setPassword] = useState("");

  const handlePasswordSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    try {
      await login(email, password, slug);
      toast.success("Signed in");
    } catch (err: unknown) {
      setError((err as { message?: string })?.message ?? "Invalid credentials");
    }
  };

  const switchTab = (t: "otp" | "password") => {
    setTab(t); setError(null);
    setOtpSent(false); setOtp(""); setPhone("");
  };

  return (
    <div className="grid min-h-dvh place-items-center px-4 py-10">
      <div className="w-full max-w-md rounded-3xl border border-border/60 bg-card p-7 shadow-card">
        <div className="grid h-12 w-12 place-items-center rounded-2xl bg-primary-soft text-primary">
          <ShieldCheck className="h-5 w-5" />
        </div>
        <h1 className="mt-5 text-xl font-semibold tracking-tight">Owner sign in</h1>
        <p className="mt-1 text-sm text-muted-foreground">
          Manage <span className="font-medium text-foreground">{tenantName}</span> — orders, inventory, customers and more.
        </p>

        {/* Tab switcher */}
        <div className="mt-5 flex rounded-xl bg-surface-muted p-1 text-sm font-medium">
          <button
            onClick={() => switchTab("otp")}
            className={`flex-1 rounded-lg py-2 transition-colors ${tab === "otp" ? "bg-card shadow-sm text-foreground" : "text-muted-foreground hover:text-foreground"}`}
          >
            Mobile OTP
          </button>
          <button
            onClick={() => switchTab("password")}
            className={`flex-1 rounded-lg py-2 transition-colors ${tab === "password" ? "bg-card shadow-sm text-foreground" : "text-muted-foreground hover:text-foreground"}`}
          >
            Email & Password
          </button>
        </div>

        {error && (
          <div className="mt-4 rounded-2xl border border-destructive/30 bg-destructive/10 px-4 py-3 text-sm text-destructive">
            {error}
          </div>
        )}

        {/* ── OTP tab ── */}
        {tab === "otp" && (
          <div className="mt-4 space-y-3">
            <div>
              <Label className="mb-1.5 block text-[12.5px] font-medium">Mobile number</Label>
              <Input
                type="tel"
                placeholder="10-digit mobile number"
                value={phone}
                onChange={(e) => setPhone(e.target.value.replace(/\D/g, "").slice(0, 10))}
                disabled={otpSent}
                className="rounded-xl"
              />
            </div>

            {!otpSent ? (
              <Button onClick={handleSendOtp} disabled={sending || phone.length !== 10} className="h-11 w-full rounded-full text-sm font-semibold">
                {sending ? "Sending…" : "Send OTP"}
              </Button>
            ) : (
              <>
                <div>
                  <Label className="mb-1.5 block text-[12.5px] font-medium">Enter OTP</Label>
                  <Input
                    type="text"
                    inputMode="numeric"
                    placeholder="6-digit OTP"
                    maxLength={6}
                    value={otp}
                    onChange={(e) => setOtp(e.target.value.replace(/\D/g, "").slice(0, 6))}
                    className="rounded-xl tracking-widest"
                    autoFocus
                  />
                </div>
                <Button onClick={handleVerifyOtp} disabled={verifying || otp.length !== 6} className="h-11 w-full rounded-full text-sm font-semibold">
                  {verifying ? "Verifying…" : "Verify & Sign in"}
                </Button>
                <p className="text-center text-[11.5px] text-muted-foreground">
                  {resendTimer > 0
                    ? `Resend OTP in ${resendTimer}s`
                    : <button onClick={handleSendOtp} className="text-primary hover:underline">Resend OTP</button>
                  }
                </p>
              </>
            )}
          </div>
        )}

        {/* ── Password tab ── */}
        {tab === "password" && (
          <form className="mt-4 space-y-3" onSubmit={handlePasswordSubmit}>
            <div>
              <Label htmlFor="oe" className="mb-1.5 block text-[12.5px] font-medium">Email</Label>
              <Input id="oe" type="email" value={email} onChange={(e) => setEmail(e.target.value)} className="rounded-xl" />
            </div>
            <div>
              <Label htmlFor="op" className="mb-1.5 block text-[12.5px] font-medium">Password</Label>
              <Input id="op" type="password" value={password} onChange={(e) => setPassword(e.target.value)} className="rounded-xl" />
            </div>
            <Button type="submit" disabled={loading} className="h-11 w-full rounded-full text-sm font-semibold">
              {loading ? "Signing in…" : "Sign in to dashboard"}
            </Button>
          </form>
        )}

        <div className="mt-5 flex items-center justify-between text-[11.5px] text-muted-foreground">
          <Link to="/s/$tenant" params={{ tenant: slug }} className="inline-flex items-center gap-1 hover:text-foreground">
            <Store className="h-3.5 w-3.5" /> Back to storefront
          </Link>
          <Link to="/onboarding/seller" className="text-primary hover:underline">Register as seller</Link>
        </div>
      </div>
    </div>
  );
}
