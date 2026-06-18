import { createFileRoute, useNavigate, Link } from "@tanstack/react-router";
import { useState, useEffect, useCallback } from "react";
import {
  ArrowLeft, ArrowRight, Store, MapPin, Palette, FileText, CheckCircle2,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { OnboardingStepper } from "@/components/onboarding/OnboardingStepper";
import { PageTransition } from "@/components/motion/PageTransition";
import { useAuthStore, useOnboardingStore } from "@/stores";
import { api } from "@/lib/api";
import { toast } from "sonner";
import { cn } from "@/lib/utils";
import type { TenantAccent } from "@/lib/types";

export const Route = createFileRoute("/onboarding/seller")({
  head: () => ({ meta: [{ title: "Become a seller · Marketly" }] }),
  component: SellerOnboarding,
});

const STEPS = [
  { label: "Mobile"   },
  { label: "OTP"      },
  { label: "Business" },
  { label: "Address"  },
  { label: "Branding" },
  { label: "KYC"      },
  { label: "Done"     },
];

const STORE_CATEGORIES = ["Grocery","Organic","Bakery","Electronics","Pharmacy","Fashion","Restaurant","Other"];
const BUSINESS_TYPES   = ["Individual","Partnership","Private Limited","Limited Liability Partnership","One Person Company"];
const INDIAN_STATES    = [
  "Andhra Pradesh","Arunachal Pradesh","Assam","Bihar","Chhattisgarh","Goa","Gujarat",
  "Haryana","Himachal Pradesh","Jharkhand","Karnataka","Kerala","Madhya Pradesh",
  "Maharashtra","Manipur","Meghalaya","Mizoram","Nagaland","Odisha","Punjab",
  "Rajasthan","Sikkim","Tamil Nadu","Telangana","Tripura","Uttar Pradesh",
  "Uttarakhand","West Bengal","Delhi","Jammu & Kashmir","Ladakh",
];
const ACCENTS: { value: TenantAccent; label: string; hex: string }[] = [
  { value: "emerald", label: "Emerald", hex: "#22a26b" },
  { value: "orange",  label: "Sunset",  hex: "#f08a3e" },
  { value: "purple",  label: "Royal",   hex: "#7d4ed6" },
  { value: "rose",    label: "Rose",    hex: "#e0426a" },
];
const BANNER_GRADIENTS = [
  "linear-gradient(135deg, oklch(0.95 0.04 158), oklch(0.88 0.08 158))",
  "linear-gradient(135deg, oklch(0.94 0.05 145), oklch(0.85 0.1 145))",
  "linear-gradient(135deg, oklch(0.95 0.03 290), oklch(0.82 0.12 290))",
  "linear-gradient(135deg, oklch(0.96 0.04 60), oklch(0.85 0.13 50))",
  "linear-gradient(135deg, oklch(0.96 0.03 15), oklch(0.85 0.12 15))",
  "linear-gradient(135deg, oklch(0.95 0.04 240), oklch(0.82 0.12 240))",
];

function slugify(name: string) {
  return name.toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, "").slice(0, 60);
}

function SellerOnboarding() {
  const navigate = useNavigate();

  const seller            = useOnboardingStore((s) => s.seller);
  const updateSeller      = useOnboardingStore((s) => s.updateSeller);
  const updateBusiness    = useOnboardingStore((s) => s.updateSellerBusiness);
  const updateAddress     = useOnboardingStore((s) => s.updateSellerAddress);
  const updateBranding    = useOnboardingStore((s) => s.updateSellerBranding);
  const updateKyc         = useOnboardingStore((s) => s.updateSellerKyc);
  const resetSeller       = useOnboardingStore((s) => s.resetSeller);

  const loginFromOtp = useAuthStore((s) => s.loginFromOtp);

  const step = seller.step;
  const setStep = useCallback((n: number) => updateSeller({ step: n as typeof seller.step }), [updateSeller]);

  // Resume banner
  const hasDraft = seller.tenantSlug != null && seller.step > 2;

  // ── Step 1 — Mobile ───────────────────────────────────────────────────
  const [phoneInput, setPhoneInput] = useState(seller.phone);
  const [sendingOtp, setSendingOtp] = useState(false);

  const handleSendOtp = async () => {
    if (!/^[6-9]\d{9}$/.test(phoneInput)) {
      toast.error("Enter a valid 10-digit mobile number"); return;
    }
    setSendingOtp(true);
    try {
      await api.sendOtp(phoneInput);
      updateSeller({ phone: phoneInput, step: 2 });
      toast.success("OTP sent to " + phoneInput);
    } catch (err: unknown) {
      toast.error((err as { message?: string })?.message ?? "Failed to send OTP");
    } finally {
      setSendingOtp(false);
    }
  };

  // ── Step 2 — OTP ──────────────────────────────────────────────────────
  const [otp, setOtp]       = useState("");
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
      const result = await api.verifyOtp(seller.phone, otp, { role: "TENANT_OWNER" });
      loginFromOtp(result);
      setStep(3);
    } catch (err: unknown) {
      toast.error((err as { message?: string })?.message ?? "Invalid OTP");
    } finally {
      setVerifying(false);
    }
  };

  // ── Step 3 — Business ─────────────────────────────────────────────────
  const biz = seller.businessForm;
  const [slugAvail, setSlugAvail] = useState<boolean | null>(null);
  const [checkingSlug, setCheckingSlug] = useState(false);
  const [creatingStore, setCreatingStore] = useState(false);

  const handleSlugChange = async (raw: string) => {
    const slug = slugify(raw);
    updateBusiness({ slug });
    if (slug.length < 3) { setSlugAvail(null); return; }
    setCheckingSlug(true);
    try {
      const avail = await api.checkSlugAvailability(slug);
      setSlugAvail(avail);
    } finally {
      setCheckingSlug(false);
    }
  };

  const handleCreateStore = async () => {
    if (!biz.name.trim())     { toast.error("Store name is required"); return; }
    if (!biz.slug || slugAvail === false) { toast.error("Choose a different store URL — this one is taken"); return; }
    if (!biz.category)        { toast.error("Select a store category"); return; }
    if (!biz.email || !/^[^@]+@[^@]+\.[^@]+$/.test(biz.email)) { toast.error("Enter a valid business email"); return; }
    setCreatingStore(true);
    try {
      await api.createSellerTenant({
        slug:        biz.slug,
        name:        biz.name,
        tagline:     biz.tagline,
        description: biz.description,
        category:    biz.category,
        ownerEmail:  biz.email,
      });
      // Save tenant settings for business_email (needed by KYC/approval notifications)
      await api.saveSellerAddress(biz.slug, { business_email: biz.email });
      updateSeller({ tenantSlug: biz.slug, step: 4 });
      toast.success("Store created!");
    } catch (err: unknown) {
      toast.error((err as { message?: string })?.message ?? "Failed to create store");
    } finally {
      setCreatingStore(false);
    }
  };

  // ── Step 4 — Address ──────────────────────────────────────────────────
  const addr = seller.addressForm;
  const [savingAddr, setSavingAddr] = useState(false);

  const handleSaveAddress = async () => {
    if (!addr.line1.trim() || !addr.city.trim() || !addr.pincode.trim()) {
      toast.error("Address, city and pincode are required"); return;
    }
    setSavingAddr(true);
    try {
      await api.saveSellerAddress(seller.tenantSlug!, {
        address:          addr.line1 + (addr.line2 ? ", " + addr.line2 : ""),
        city:             addr.city,
        state:            addr.state,
        pincode:          addr.pincode,
        latitude:         addr.lat,
        longitude:        addr.lng,
        deliveryRadiusKm: String(addr.deliveryRadius),
        onboardingStep:   "ADDRESS",
      });
      updateSeller({ step: 5 });
    } catch {
      toast.error("Failed to save address — please try again");
    } finally {
      setSavingAddr(false);
    }
  };

  // ── Step 5 — Branding ─────────────────────────────────────────────────
  const brand = seller.brandingForm;
  const [savingBrand, setSavingBrand] = useState(false);

  const handleSaveBranding = async () => {
    setSavingBrand(true);
    try {
      await api.saveSellerBranding(
        seller.tenantSlug!,
        { accentColor: brand.accent },
        { logoEmoji: brand.logoEmoji, bannerGradient: brand.bannerGradient, tagline: brand.tagline, onboardingStep: "BRANDING" },
      );
      updateSeller({ step: 6 });
    } catch {
      toast.error("Failed to save branding — please try again");
    } finally {
      setSavingBrand(false);
    }
  };

  // ── Step 6 — KYC ──────────────────────────────────────────────────────
  const kyc = seller.kycForm;
  const [submittingKyc, setSubmittingKyc] = useState(false);

  const handleSubmitKyc = async () => {
    if (!kyc.aadhaar || !/^\d{4}\s?\d{4}\s?\d{4}$/.test(kyc.aadhaar.replace(/\s/g,""))) {
      toast.error("Enter a valid 12-digit Aadhaar number"); return;
    }
    if (!kyc.pan || !/^[A-Z]{5}[0-9]{4}[A-Z]$/.test(kyc.pan)) {
      toast.error("Enter a valid PAN number (e.g. ABCDE1234F)"); return;
    }
    setSubmittingKyc(true);
    try {
      await api.submitSellerKyc(seller.tenantSlug!, {
        aadhaarNumber: kyc.aadhaar.replace(/\s/g, ""),
        panNumber:     kyc.pan.toUpperCase(),
        gstNumber:     kyc.gst || undefined,
        documentUrls:  kyc.documentUrls,
        storeImageUrl: kyc.storeImageUrl,
      });
      updateSeller({ step: 7 });
      toast.success("KYC submitted successfully!");
    } catch (err: unknown) {
      toast.error((err as { message?: string })?.message ?? "KYC submission failed");
    } finally {
      setSubmittingKyc(false);
    }
  };

  return (
    <PageTransition>
      {/* Resume draft banner */}
      {hasDraft && step === 1 && (
        <div className="mb-6 rounded-2xl border border-primary/20 bg-primary-soft px-4 py-3">
          <p className="text-[13px] font-medium">
            You have an incomplete application for <strong>{seller.businessForm.name || seller.tenantSlug}</strong>.{" "}
            <button className="text-primary underline" onClick={() => setStep(seller.step)}>Resume</button>
            {" or "}
            <button className="text-muted-foreground underline" onClick={() => { resetSeller(); }}>Start fresh</button>
          </p>
        </div>
      )}

      {/* Stepper */}
      <div className="mb-8 flex justify-center">
        <OnboardingStepper steps={STEPS} current={step} />
      </div>

      {/* ── Step 1 — Mobile ─────────────────────────────── */}
      {step === 1 && (
        <div className="mx-auto max-w-md">
          <div className="rounded-3xl border border-border/60 bg-card p-7 shadow-card">
            <div className="mb-1 flex items-center gap-2">
              <Store className="h-5 w-5 text-primary" />
              <h1 className="text-xl font-semibold tracking-tight">Open your store</h1>
            </div>
            <p className="mb-5 text-sm text-muted-foreground">
              Join thousands of sellers on Marketly. Start with your mobile number.
            </p>
            <div className="space-y-4">
              <div>
                <Label htmlFor="sel-ph" className="mb-1.5 block text-[12.5px] font-medium">Mobile number</Label>
                <div className="flex gap-2">
                  <span className="flex h-11 items-center rounded-xl border border-border bg-surface px-3 text-sm text-muted-foreground">+91</span>
                  <Input
                    id="sel-ph"
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
                {sendingOtp ? "Sending…" : "Get OTP"} {!sendingOtp && <ArrowRight className="ml-1.5 h-4 w-4" />}
              </Button>
            </div>
            <p className="mt-4 text-center text-[11.5px] text-muted-foreground">
              Already a seller? <Link to="/auth/login" className="text-primary hover:underline">Sign in</Link>
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
            <p className="mt-1 text-sm text-muted-foreground">Sent to <strong>+91 {seller.phone}</strong></p>
            <div className="mt-5 space-y-4">
              <div>
                <Label className="mb-1.5 block text-[12.5px] font-medium">6-digit OTP</Label>
                <Input
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
              <Button onClick={handleVerifyOtp} disabled={verifying || otp.length !== 6}
                className="h-11 w-full rounded-full bg-foreground text-sm font-semibold text-background hover:opacity-90">
                {verifying ? "Verifying…" : "Verify & Continue"}
              </Button>
              <div className="text-center text-[12px] text-muted-foreground">
                {resendTimer > 0 ? <span>Resend in {resendTimer}s</span> : (
                  <button className="text-primary hover:underline" onClick={async () => { await api.sendOtp(seller.phone); setResendTimer(30); toast.success("OTP resent"); }}>
                    Resend OTP
                  </button>
                )}
              </div>
            </div>
          </div>
        </div>
      )}

      {/* ── Step 3 — Business Info ───────────────────────── */}
      {step === 3 && (
        <div className="mx-auto max-w-lg">
          <div className="rounded-3xl border border-border/60 bg-card p-7 shadow-card">
            <div className="mb-1 flex items-center gap-2">
              <Store className="h-5 w-5 text-primary" />
              <h1 className="text-xl font-semibold tracking-tight">Business information</h1>
            </div>
            <p className="mb-5 text-sm text-muted-foreground">Tell us about your store</p>
            <div className="space-y-4">
              <div className="grid gap-3 md:grid-cols-2">
                <div>
                  <Label className="mb-1.5 block text-[12.5px] font-medium">Store name *</Label>
                  <Input
                    value={biz.name}
                    onChange={(e) => {
                      updateBusiness({ name: e.target.value });
                      if (!biz.slug || biz.slug === slugify(biz.name)) {
                        handleSlugChange(e.target.value);
                      }
                    }}
                    placeholder="Jane's Bakery"
                    className="rounded-xl"
                  />
                </div>
                <div>
                  <Label className="mb-1.5 block text-[12.5px] font-medium">Store URL *</Label>
                  <div className="relative">
                    <Input
                      value={biz.slug}
                      onChange={(e) => handleSlugChange(e.target.value)}
                      placeholder="janes-bakery"
                      className={cn(
                        "rounded-xl pr-8",
                        slugAvail === true  && "border-primary",
                        slugAvail === false && "border-destructive",
                      )}
                    />
                    {checkingSlug && <span className="absolute right-3 top-1/2 -translate-y-1/2 text-[10px] text-muted-foreground">checking…</span>}
                    {!checkingSlug && slugAvail === true  && <span className="absolute right-3 top-1/2 -translate-y-1/2 text-[10px] text-primary">✓ available</span>}
                    {!checkingSlug && slugAvail === false && <span className="absolute right-3 top-1/2 -translate-y-1/2 text-[10px] text-destructive">taken</span>}
                  </div>
                  <p className="mt-1 text-[11px] text-muted-foreground">marketly.com/s/{biz.slug || "your-store"}</p>
                </div>
              </div>
              <div className="grid gap-3 md:grid-cols-2">
                <div>
                  <Label className="mb-1.5 block text-[12.5px] font-medium">Category *</Label>
                  <Select value={biz.category} onValueChange={(v) => updateBusiness({ category: v })}>
                    <SelectTrigger className="rounded-xl"><SelectValue placeholder="Select" /></SelectTrigger>
                    <SelectContent>{STORE_CATEGORIES.map((c) => <SelectItem key={c} value={c}>{c}</SelectItem>)}</SelectContent>
                  </Select>
                </div>
                <div>
                  <Label className="mb-1.5 block text-[12.5px] font-medium">Business type</Label>
                  <Select value={biz.businessType} onValueChange={(v) => updateBusiness({ businessType: v })}>
                    <SelectTrigger className="rounded-xl"><SelectValue placeholder="Select" /></SelectTrigger>
                    <SelectContent>{BUSINESS_TYPES.map((t) => <SelectItem key={t} value={t}>{t}</SelectItem>)}</SelectContent>
                  </Select>
                </div>
              </div>
              <div>
                <Label className="mb-1.5 block text-[12.5px] font-medium">Tagline</Label>
                <Input value={biz.tagline} onChange={(e) => updateBusiness({ tagline: e.target.value })} placeholder="Fresh bakes every morning" className="rounded-xl" />
              </div>
              <div>
                <Label className="mb-1.5 block text-[12.5px] font-medium">Description</Label>
                <Textarea rows={3} value={biz.description} onChange={(e) => updateBusiness({ description: e.target.value })} placeholder="What makes your store special?" className="rounded-xl" />
              </div>
              <div>
                <Label className="mb-1.5 block text-[12.5px] font-medium">Business email *</Label>
                <Input type="email" value={biz.email} onChange={(e) => updateBusiness({ email: e.target.value })} placeholder="jane@example.com" className="rounded-xl" />
              </div>
              <Button onClick={handleCreateStore} disabled={creatingStore}
                className="h-11 w-full rounded-full bg-foreground text-sm font-semibold text-background hover:opacity-90">
                {creatingStore ? "Creating…" : "Create store"} {!creatingStore && <ArrowRight className="ml-1.5 h-4 w-4" />}
              </Button>
            </div>
          </div>
        </div>
      )}

      {/* ── Step 4 — Store Address ───────────────────────── */}
      {step === 4 && (
        <div className="mx-auto max-w-lg">
          <div className="rounded-3xl border border-border/60 bg-card p-7 shadow-card">
            <div className="mb-1 flex items-center gap-2">
              <MapPin className="h-5 w-5 text-primary" />
              <h1 className="text-xl font-semibold tracking-tight">Store address</h1>
            </div>
            <p className="mb-5 text-sm text-muted-foreground">Where is your store located?</p>
            <div className="space-y-4">
              <div>
                <Label className="mb-1.5 block text-[12.5px] font-medium">Address line 1 *</Label>
                <Input value={addr.line1} onChange={(e) => updateAddress({ line1: e.target.value })} placeholder="42, Baker Street, Andheri West" className="rounded-xl" />
              </div>
              <div>
                <Label className="mb-1.5 block text-[12.5px] font-medium">Address line 2</Label>
                <Input value={addr.line2} onChange={(e) => updateAddress({ line2: e.target.value })} placeholder="Landmark, area" className="rounded-xl" />
              </div>
              <div className="grid gap-3 md:grid-cols-3">
                <div>
                  <Label className="mb-1.5 block text-[12.5px] font-medium">City *</Label>
                  <Input value={addr.city} onChange={(e) => updateAddress({ city: e.target.value })} className="rounded-xl" />
                </div>
                <div>
                  <Label className="mb-1.5 block text-[12.5px] font-medium">State</Label>
                  <Select value={addr.state} onValueChange={(v) => updateAddress({ state: v })}>
                    <SelectTrigger className="rounded-xl"><SelectValue placeholder="State" /></SelectTrigger>
                    <SelectContent>{INDIAN_STATES.map((s) => <SelectItem key={s} value={s}>{s}</SelectItem>)}</SelectContent>
                  </Select>
                </div>
                <div>
                  <Label className="mb-1.5 block text-[12.5px] font-medium">Pincode *</Label>
                  <Input value={addr.pincode} onChange={(e) => updateAddress({ pincode: e.target.value.replace(/\D/g, "") })} maxLength={6} inputMode="numeric" className="rounded-xl" />
                </div>
              </div>
              <div>
                <Label className="mb-1.5 block text-[12.5px] font-medium">Delivery radius (km)</Label>
                <Input type="number" min={1} max={50} value={addr.deliveryRadius} onChange={(e) => updateAddress({ deliveryRadius: Number(e.target.value) })} className="w-32 rounded-xl" />
              </div>
              <Button onClick={handleSaveAddress} disabled={savingAddr}
                className="h-11 w-full rounded-full bg-foreground text-sm font-semibold text-background hover:opacity-90">
                {savingAddr ? "Saving…" : "Continue"} {!savingAddr && <ArrowRight className="ml-1.5 h-4 w-4" />}
              </Button>
            </div>
          </div>
        </div>
      )}

      {/* ── Step 5 — Branding ────────────────────────────── */}
      {step === 5 && (
        <div className="mx-auto max-w-lg">
          <div className="rounded-3xl border border-border/60 bg-card p-7 shadow-card">
            <div className="mb-1 flex items-center gap-2">
              <Palette className="h-5 w-5 text-primary" />
              <h1 className="text-xl font-semibold tracking-tight">Branding & theme</h1>
            </div>
            <p className="mb-5 text-sm text-muted-foreground">Customise how your store looks</p>
            <div className="space-y-5">
              {/* Logo emoji */}
              <div>
                <Label className="mb-1.5 block text-[12.5px] font-medium">Store logo emoji</Label>
                <Input value={brand.logoEmoji} onChange={(e) => updateBranding({ logoEmoji: e.target.value })} maxLength={2} className="w-24 rounded-xl text-center text-2xl" />
              </div>

              {/* Accent color */}
              <div>
                <Label className="mb-2 block text-[12.5px] font-medium">Accent color</Label>
                <div className="flex flex-wrap gap-2">
                  {ACCENTS.map((a) => (
                    <button
                      key={a.value}
                      type="button"
                      onClick={() => updateBranding({ accent: a.value })}
                      className={cn(
                        "flex items-center gap-2 rounded-full border px-3 py-1.5 text-[12px] font-medium transition-colors",
                        brand.accent === a.value ? "border-primary bg-primary-soft" : "border-border bg-card hover:border-primary/40",
                      )}
                    >
                      <span className="h-3 w-3 rounded-full" style={{ background: a.hex }} />
                      {a.label}
                    </button>
                  ))}
                </div>
              </div>

              {/* Banner */}
              <div>
                <Label className="mb-2 block text-[12.5px] font-medium">Banner gradient</Label>
                <div className="grid grid-cols-3 gap-2 md:grid-cols-6">
                  {BANNER_GRADIENTS.map((g) => (
                    <button
                      key={g}
                      type="button"
                      onClick={() => updateBranding({ bannerGradient: g })}
                      className={cn("h-14 rounded-xl ring-2 ring-offset-2 ring-offset-card transition", brand.bannerGradient === g ? "ring-primary" : "ring-transparent")}
                      style={{ background: g }}
                    />
                  ))}
                </div>
              </div>

              {/* Live preview */}
              <div className="overflow-hidden rounded-2xl border border-border/60">
                <div className="grid h-24 place-items-center text-5xl" style={{ background: brand.bannerGradient }}>
                  {brand.logoEmoji}
                </div>
                <div className="p-3">
                  <div className="text-sm font-semibold">{seller.businessForm.name || "Your Store"}</div>
                  <div className="text-[11.5px] text-muted-foreground">{seller.businessForm.category} · {seller.addressForm.city || "City"}</div>
                </div>
              </div>

              <Button onClick={handleSaveBranding} disabled={savingBrand}
                className="h-11 w-full rounded-full bg-foreground text-sm font-semibold text-background hover:opacity-90">
                {savingBrand ? "Saving…" : "Continue"} {!savingBrand && <ArrowRight className="ml-1.5 h-4 w-4" />}
              </Button>
            </div>
          </div>
        </div>
      )}

      {/* ── Step 6 — KYC ─────────────────────────────────── */}
      {step === 6 && (
        <div className="mx-auto max-w-lg">
          <div className="rounded-3xl border border-border/60 bg-card p-7 shadow-card">
            <div className="mb-1 flex items-center gap-2">
              <FileText className="h-5 w-5 text-primary" />
              <h1 className="text-xl font-semibold tracking-tight">KYC verification</h1>
            </div>
            <p className="mb-5 text-sm text-muted-foreground">Required to activate your store. Your documents are secure.</p>
            <div className="space-y-4">
              <div>
                <Label className="mb-1.5 block text-[12.5px] font-medium">Aadhaar number *</Label>
                <Input
                  value={kyc.aadhaar}
                  onChange={(e) => updateKyc({ aadhaar: e.target.value })}
                  inputMode="numeric"
                  maxLength={14}
                  placeholder="1234 5678 9012"
                  className="rounded-xl"
                />
              </div>
              <div>
                <Label className="mb-1.5 block text-[12.5px] font-medium">PAN number *</Label>
                <Input
                  value={kyc.pan}
                  onChange={(e) => updateKyc({ pan: e.target.value.toUpperCase() })}
                  maxLength={10}
                  placeholder="ABCDE1234F"
                  className="rounded-xl uppercase"
                />
              </div>
              <div>
                <Label className="mb-1.5 block text-[12.5px] font-medium">
                  GST number <span className="text-muted-foreground">(optional)</span>
                </Label>
                <Input
                  value={kyc.gst}
                  onChange={(e) => updateKyc({ gst: e.target.value.toUpperCase() })}
                  maxLength={15}
                  placeholder="27ABCDE1234F1Z5"
                  className="rounded-xl uppercase"
                />
              </div>
              <div>
                <Label className="mb-1.5 block text-[12.5px] font-medium">
                  Document URL(s) <span className="text-muted-foreground">(Aadhaar, PAN — paste upload links)</span>
                </Label>
                <Textarea
                  rows={3}
                  value={kyc.documentUrls.join("\n")}
                  onChange={(e) => updateKyc({ documentUrls: e.target.value.split("\n").filter(Boolean) })}
                  placeholder="https://cdn.example.com/aadhaar-front.jpg&#10;https://cdn.example.com/pan.jpg"
                  className="rounded-xl font-mono text-xs"
                />
                <p className="mt-1 text-[11px] text-muted-foreground">One URL per line</p>
              </div>
              <div className="rounded-2xl border border-amber-200 bg-amber-50 px-4 py-3 text-[12px] text-amber-800">
                🔒 Your KYC data is encrypted and used only for identity verification. Review takes 1-2 business days.
              </div>
              <Button onClick={handleSubmitKyc} disabled={submittingKyc}
                className="h-11 w-full rounded-full bg-foreground text-sm font-semibold text-background hover:opacity-90">
                {submittingKyc ? "Submitting…" : "Submit KYC"}
              </Button>
            </div>
          </div>
        </div>
      )}

      {/* ── Step 7 — Submitted ───────────────────────────── */}
      {step === 7 && (
        <div className="mx-auto max-w-md text-center">
          <div className="rounded-3xl border border-border/60 bg-card p-8 shadow-card">
            <div className="mx-auto grid h-16 w-16 place-items-center rounded-2xl bg-primary-soft">
              <CheckCircle2 className="h-8 w-8 text-primary" />
            </div>
            <h1 className="mt-5 text-2xl font-semibold tracking-tight">Application submitted!</h1>
            <p className="mt-2 text-sm text-muted-foreground">
              Your KYC is under review. We'll email you at <strong>{seller.businessForm.email}</strong> within 1-2 business days.
            </p>

            {/* Timeline */}
            <div className="mt-6 space-y-2 text-left">
              {[
                { label: "KYC submitted",   done: true  },
                { label: "Under review",     done: false },
                { label: "Approved",         done: false },
                { label: "Store goes live",  done: false },
              ].map((item, i) => (
                <div key={i} className="flex items-center gap-3">
                  <div className={cn("h-2.5 w-2.5 rounded-full", item.done ? "bg-primary" : "bg-border")} />
                  <span className={cn("text-[13px]", item.done ? "font-medium" : "text-muted-foreground")}>{item.label}</span>
                </div>
              ))}
            </div>

            <Button
              asChild
              className="mt-6 h-11 w-full rounded-full bg-foreground text-sm font-semibold text-background hover:opacity-90"
            >
              <Link to="/onboarding/seller/status">View application status</Link>
            </Button>
          </div>
        </div>
      )}
    </PageTransition>
  );
}
