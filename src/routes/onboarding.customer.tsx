import { createFileRoute, useNavigate, Link } from "@tanstack/react-router";
import { useState, useEffect } from "react";
import { ArrowLeft, ArrowRight, Gift, MapPin, User, ShoppingBag } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { OnboardingStepper } from "@/components/onboarding/OnboardingStepper";
import { PageTransition } from "@/components/motion/PageTransition";
import { useAuthStore, useOnboardingStore } from "@/stores";
import { api } from "@/lib/api";
import { toast } from "sonner";

export const Route = createFileRoute("/onboarding/customer")({
  head: () => ({ meta: [{ title: "Join Marketly · Customer Onboarding" }] }),
  validateSearch: (search: Record<string, unknown>) => ({
    tenant: typeof search.tenant === "string" ? search.tenant : undefined,
  }),
  component: CustomerOnboarding,
});

const STEPS = [
  { label: "Mobile"  },
  { label: "OTP"     },
  { label: "Profile" },
  { label: "Address" },
  { label: "Done"    },
];

const INDIAN_STATES = [
  "Andhra Pradesh","Arunachal Pradesh","Assam","Bihar","Chhattisgarh","Goa","Gujarat",
  "Haryana","Himachal Pradesh","Jharkhand","Karnataka","Kerala","Madhya Pradesh",
  "Maharashtra","Manipur","Meghalaya","Mizoram","Nagaland","Odisha","Punjab",
  "Rajasthan","Sikkim","Tamil Nadu","Telangana","Tripura","Uttar Pradesh",
  "Uttarakhand","West Bengal","Delhi","Jammu & Kashmir","Ladakh",
];

function CustomerOnboarding() {
  const navigate = useNavigate();
  // ?tenant=freshmart — set from store header "Sign in" or homepage CTA
  const { tenant: tenantParam } = Route.useSearch();

  // Onboarding store (session state — not persisted)
  const step          = useOnboardingStore((s) => s.customerStep);
  const phone         = useOnboardingStore((s) => s.customerPhone);
  const tenantSlug    = useOnboardingStore((s) => s.customerTenantSlug);
  const setStep       = useOnboardingStore((s) => s.setCustomerStep);
  const setPhone      = useOnboardingStore((s) => s.setCustomerPhone);
  const setTenantSlug = useOnboardingStore((s) => s.setCustomerTenantSlug);

  // Seed tenantSlug from URL search param on first render
  useEffect(() => {
    if (tenantParam && !tenantSlug) {
      setTenantSlug(tenantParam);
    }
  }, [tenantParam]);

  // Auth
  const loginFromOtp = useAuthStore((s) => s.loginFromOtp);

  // ── Step 1 — Mobile ───────────────────────────────────────────────────
  const [phoneInput, setPhoneInput] = useState(phone);
  const [sendingOtp, setSendingOtp] = useState(false);

  const handleSendOtp = async () => {
    if (!/^[6-9]\d{9}$/.test(phoneInput)) {
      toast.error("Please enter a valid 10-digit mobile number");
      return;
    }
    setSendingOtp(true);
    try {
      await api.sendOtp(phoneInput);
      setPhone(phoneInput);
      setStep(2);
      toast.success("OTP sent to " + phoneInput);
    } catch (err: unknown) {
      toast.error((err as { message?: string })?.message ?? "Failed to send OTP");
    } finally {
      setSendingOtp(false);
    }
  };

  // ── Step 2 — OTP ──────────────────────────────────────────────────────
  const [otp, setOtp]           = useState("");
  const [verifying, setVerifying] = useState(false);
  const [resendTimer, setResendTimer] = useState(30);

  useEffect(() => {
    if (step !== 2) return;
    setResendTimer(30);
    const t = setInterval(() => setResendTimer((n) => (n > 0 ? n - 1 : 0)), 1000);
    return () => clearInterval(t);
  }, [step]);

  const handleVerifyOtp = async () => {
    if (otp.length !== 6) { toast.error("Enter the 6-digit OTP"); return; }
    setVerifying(true);
    try {
      const result = await api.verifyOtp(phone, otp, { tenantSlug: tenantSlug ?? undefined });
      loginFromOtp(result, tenantSlug ?? undefined);
      if (result.isNewUser) {
        setStep(3);
      } else {
        // Returning user — skip to address
        setStep(4);
        toast.success("Welcome back!");
      }
    } catch (err: unknown) {
      toast.error((err as { message?: string })?.message ?? "Invalid OTP");
    } finally {
      setVerifying(false);
    }
  };

  // ── Step 3 — Profile ──────────────────────────────────────────────────
  const [profile, setProfile] = useState({
    firstName: "", lastName: "", email: "", gender: "", dob: "",
  });
  const [savingProfile, setSavingProfile] = useState(false);

  const handleSaveProfile = async () => {
    if (!profile.firstName.trim() || !profile.lastName.trim()) {
      toast.error("First and last name are required"); return;
    }
    if (!profile.email || !/^[^@]+@[^@]+\.[^@]+$/.test(profile.email)) {
      toast.error("Please enter a valid email"); return;
    }
    setSavingProfile(true);
    try {
      if (tenantSlug) {
        // api.updateCustomerProfile resolves slug → UUID internally
        await api.updateCustomerProfile(tenantSlug, {
          firstName:   profile.firstName,
          lastName:    profile.lastName,
          email:       profile.email,
          dateOfBirth: profile.dob || undefined,
        });
      }
      setStep(4);
    } catch {
      // Non-fatal — profile can be updated later from account page
      setStep(4);
    } finally {
      setSavingProfile(false);
    }
  };

  // ── Step 4 — Address ──────────────────────────────────────────────────
  const [address, setAddress] = useState({
    label: "Home", line1: "", line2: "", city: "", state: "", pincode: "",
  });
  const [savingAddress, setSavingAddress] = useState(false);

  const handleSaveAddress = async () => {
    if (!address.line1.trim() || !address.city.trim() || !address.pincode.trim()) {
      toast.error("Address, city and pincode are required"); return;
    }
    if (!/^\d{6}$/.test(address.pincode)) {
      toast.error("Pincode must be 6 digits"); return;
    }
    setSavingAddress(true);
    try {
      if (tenantSlug) {
        await api.addCustomerAddress(tenantSlug, {
          label:     address.label,
          line1:     address.line1,
          line2:     address.line2 || undefined,
          city:      address.city,
          state:     address.state,
          pincode:   address.pincode,
          isDefault: true,
        });
      }
      setStep(5);
    } catch {
      // Non-fatal — address can be added from account page
      setStep(5);
    } finally {
      setSavingAddress(false);
    }
  };

  const handleSkipAddress = () => setStep(5);

  // ── Step 5 — Complete ─────────────────────────────────────────────────
  const handleDone = () => {
    setStep(1); setPhone(""); // reset for next time
    if (tenantSlug) navigate({ to: "/s/$tenant", params: { tenant: tenantSlug } });
    else navigate({ to: "/stores" });
  };

  return (
    <PageTransition>
      {/* Stepper */}
      <div className="mb-8 flex justify-center">
        <OnboardingStepper steps={STEPS} current={step} />
      </div>

      {/* ── Step 1 — Mobile ─────────────────────────────── */}
      {step === 1 && (
        <div className="mx-auto max-w-md">
          <div className="rounded-3xl border border-border/60 bg-card p-7 shadow-card">
            <h1 className="text-xl font-semibold tracking-tight">Welcome to Marketly</h1>
            <p className="mt-1 text-sm text-muted-foreground">Enter your mobile number to get started</p>
            <div className="mt-6 space-y-4">
              <div>
                <Label htmlFor="ph" className="mb-1.5 block text-[12.5px] font-medium">Mobile number</Label>
                <div className="flex gap-2">
                  <span className="flex h-11 items-center rounded-xl border border-border bg-surface px-3 text-sm text-muted-foreground">+91</span>
                  <Input
                    id="ph"
                    type="tel"
                    inputMode="numeric"
                    maxLength={10}
                    placeholder="9876543210"
                    value={phoneInput}
                    onChange={(e) => setPhoneInput(e.target.value.replace(/\D/g, ""))}
                    onKeyDown={(e) => e.key === "Enter" && handleSendOtp()}
                    className="h-11 flex-1 rounded-xl"
                  />
                </div>
              </div>
              <Button
                onClick={handleSendOtp}
                disabled={sendingOtp || phoneInput.length !== 10}
                className="h-11 w-full rounded-full bg-foreground text-sm font-semibold text-background hover:opacity-90"
              >
                {sendingOtp ? "Sending…" : "Get OTP"}
                {!sendingOtp && <ArrowRight className="ml-1.5 h-4 w-4" />}
              </Button>
            </div>
            <p className="mt-5 text-center text-[11.5px] text-muted-foreground">
              Already have an account?{" "}
              <Link to="/auth/login" className="text-primary hover:underline">Sign in</Link>
            </p>
          </div>
        </div>
      )}

      {/* ── Step 2 — OTP ────────────────────────────────── */}
      {step === 2 && (
        <div className="mx-auto max-w-md">
          <div className="rounded-3xl border border-border/60 bg-card p-7 shadow-card">
            <button onClick={() => setStep(1)} className="mb-4 inline-flex items-center gap-1 text-[12.5px] text-muted-foreground hover:text-foreground">
              <ArrowLeft className="h-3.5 w-3.5" /> Back
            </button>
            <h1 className="text-xl font-semibold tracking-tight">Enter OTP</h1>
            <p className="mt-1 text-sm text-muted-foreground">
              Sent to <strong>+91 {phone}</strong>
            </p>
            <div className="mt-6 space-y-4">
              <div>
                <Label htmlFor="otp-in" className="mb-1.5 block text-[12.5px] font-medium">6-digit OTP</Label>
                <Input
                  id="otp-in"
                  type="text"
                  inputMode="numeric"
                  maxLength={6}
                  placeholder="· · · · · ·"
                  value={otp}
                  onChange={(e) => setOtp(e.target.value.replace(/\D/g, ""))}
                  onKeyDown={(e) => e.key === "Enter" && handleVerifyOtp()}
                  className="h-12 rounded-xl text-center text-2xl font-semibold tracking-[0.4em]"
                />
              </div>
              <Button
                onClick={handleVerifyOtp}
                disabled={verifying || otp.length !== 6}
                className="h-11 w-full rounded-full bg-foreground text-sm font-semibold text-background hover:opacity-90"
              >
                {verifying ? "Verifying…" : "Verify OTP"}
              </Button>
              <div className="text-center text-[12px] text-muted-foreground">
                {resendTimer > 0 ? (
                  <span>Resend OTP in {resendTimer}s</span>
                ) : (
                  <button
                    className="text-primary hover:underline"
                    onClick={async () => {
                      await api.sendOtp(phone);
                      setResendTimer(30);
                      toast.success("OTP resent");
                    }}
                  >
                    Resend OTP
                  </button>
                )}
              </div>
            </div>
          </div>
        </div>
      )}

      {/* ── Step 3 — Profile ────────────────────────────── */}
      {step === 3 && (
        <div className="mx-auto max-w-md">
          <div className="rounded-3xl border border-border/60 bg-card p-7 shadow-card">
            <div className="mb-1 flex items-center gap-2">
              <User className="h-5 w-5 text-primary" />
              <h1 className="text-xl font-semibold tracking-tight">Your profile</h1>
            </div>
            <p className="mb-5 text-sm text-muted-foreground">Tell us a bit about yourself</p>
            <div className="space-y-4">
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <Label className="mb-1.5 block text-[12.5px] font-medium">First name *</Label>
                  <Input value={profile.firstName} onChange={(e) => setProfile((p) => ({ ...p, firstName: e.target.value }))} className="rounded-xl" />
                </div>
                <div>
                  <Label className="mb-1.5 block text-[12.5px] font-medium">Last name *</Label>
                  <Input value={profile.lastName} onChange={(e) => setProfile((p) => ({ ...p, lastName: e.target.value }))} className="rounded-xl" />
                </div>
              </div>
              <div>
                <Label className="mb-1.5 block text-[12.5px] font-medium">Email *</Label>
                <Input type="email" value={profile.email} onChange={(e) => setProfile((p) => ({ ...p, email: e.target.value }))} className="rounded-xl" />
              </div>
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <Label className="mb-1.5 block text-[12.5px] font-medium">Gender <span className="text-muted-foreground">(optional)</span></Label>
                  <Select value={profile.gender} onValueChange={(v) => setProfile((p) => ({ ...p, gender: v }))}>
                    <SelectTrigger className="rounded-xl"><SelectValue placeholder="Select" /></SelectTrigger>
                    <SelectContent>
                      <SelectItem value="male">Male</SelectItem>
                      <SelectItem value="female">Female</SelectItem>
                      <SelectItem value="nonbinary">Non-binary</SelectItem>
                      <SelectItem value="prefer_not">Prefer not to say</SelectItem>
                    </SelectContent>
                  </Select>
                </div>
                <div>
                  <Label className="mb-1.5 block text-[12.5px] font-medium">Date of birth <span className="text-muted-foreground">(optional)</span></Label>
                  <Input type="date" value={profile.dob} onChange={(e) => setProfile((p) => ({ ...p, dob: e.target.value }))} className="rounded-xl" />
                </div>
              </div>
              <Button
                onClick={handleSaveProfile}
                disabled={savingProfile}
                className="h-11 w-full rounded-full bg-foreground text-sm font-semibold text-background hover:opacity-90"
              >
                {savingProfile ? "Saving…" : "Continue"}
                {!savingProfile && <ArrowRight className="ml-1.5 h-4 w-4" />}
              </Button>
            </div>
          </div>
        </div>
      )}

      {/* ── Step 4 — Address ─────────────────────────────── */}
      {step === 4 && (
        <div className="mx-auto max-w-md">
          <div className="rounded-3xl border border-border/60 bg-card p-7 shadow-card">
            <div className="mb-1 flex items-center gap-2">
              <MapPin className="h-5 w-5 text-primary" />
              <h1 className="text-xl font-semibold tracking-tight">Delivery address</h1>
            </div>
            <p className="mb-5 text-sm text-muted-foreground">Add your first delivery address</p>
            <div className="space-y-3">
              <div>
                <Label className="mb-1.5 block text-[12.5px] font-medium">Label</Label>
                <Select value={address.label} onValueChange={(v) => setAddress((a) => ({ ...a, label: v }))}>
                  <SelectTrigger className="rounded-xl"><SelectValue /></SelectTrigger>
                  <SelectContent>
                    <SelectItem value="Home">Home</SelectItem>
                    <SelectItem value="Office">Office</SelectItem>
                    <SelectItem value="Other">Other</SelectItem>
                  </SelectContent>
                </Select>
              </div>
              <div>
                <Label className="mb-1.5 block text-[12.5px] font-medium">House no. & building *</Label>
                <Input value={address.line1} onChange={(e) => setAddress((a) => ({ ...a, line1: e.target.value }))} placeholder="12, Lotus Apartments" className="rounded-xl" />
              </div>
              <div>
                <Label className="mb-1.5 block text-[12.5px] font-medium">Street & area</Label>
                <Input value={address.line2} onChange={(e) => setAddress((a) => ({ ...a, line2: e.target.value }))} placeholder="Near City Mall" className="rounded-xl" />
              </div>
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <Label className="mb-1.5 block text-[12.5px] font-medium">City *</Label>
                  <Input value={address.city} onChange={(e) => setAddress((a) => ({ ...a, city: e.target.value }))} className="rounded-xl" />
                </div>
                <div>
                  <Label className="mb-1.5 block text-[12.5px] font-medium">Pincode *</Label>
                  <Input value={address.pincode} onChange={(e) => setAddress((a) => ({ ...a, pincode: e.target.value.replace(/\D/g, "") }))} maxLength={6} inputMode="numeric" className="rounded-xl" />
                </div>
              </div>
              <div>
                <Label className="mb-1.5 block text-[12.5px] font-medium">State</Label>
                <Select value={address.state} onValueChange={(v) => setAddress((a) => ({ ...a, state: v }))}>
                  <SelectTrigger className="rounded-xl"><SelectValue placeholder="Select state" /></SelectTrigger>
                  <SelectContent>
                    {INDIAN_STATES.map((s) => <SelectItem key={s} value={s}>{s}</SelectItem>)}
                  </SelectContent>
                </Select>
              </div>
              <Button
                onClick={handleSaveAddress}
                disabled={savingAddress}
                className="h-11 w-full rounded-full bg-foreground text-sm font-semibold text-background hover:opacity-90"
              >
                {savingAddress ? "Saving…" : "Save address"}
                {!savingAddress && <ArrowRight className="ml-1.5 h-4 w-4" />}
              </Button>
              <button onClick={handleSkipAddress} className="w-full text-center text-[12.5px] text-muted-foreground hover:text-foreground">
                Skip for now
              </button>
            </div>
          </div>
        </div>
      )}

      {/* ── Step 5 — Complete ────────────────────────────── */}
      {step === 5 && (
        <div className="mx-auto max-w-md text-center">
          <div className="rounded-3xl border border-border/60 bg-card p-8 shadow-card">
            <div className="mx-auto grid h-16 w-16 place-items-center rounded-2xl bg-primary-soft text-3xl">
              🎉
            </div>
            <h1 className="mt-5 text-2xl font-semibold tracking-tight">You're all set!</h1>
            <p className="mt-2 text-sm text-muted-foreground">
              Your Marketly account is ready. Start exploring local stores.
            </p>

            {/* Loyalty badge */}
            <div className="mt-5 flex items-center justify-center gap-2 rounded-2xl border border-border/60 px-4 py-3" style={{ background: "linear-gradient(135deg, var(--color-primary-soft), oklch(0.96 0.05 60))" }}>
              <Gift className="h-5 w-5 text-primary" />
              <span className="text-sm font-medium">You've earned <strong>50 welcome points</strong> · ₹25 off your first order</span>
            </div>

            <Button
              onClick={handleDone}
              className="mt-6 h-11 w-full rounded-full bg-foreground text-sm font-semibold text-background hover:opacity-90"
            >
              <ShoppingBag className="mr-1.5 h-4 w-4" />
              {tenantSlug ? "Go to store" : "Browse stores"}
            </Button>
          </div>
        </div>
      )}
    </PageTransition>
  );
}
