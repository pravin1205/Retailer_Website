import { createFileRoute } from "@tanstack/react-router";
import { useState } from "react";
import { Save, ExternalLink } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Link } from "@tanstack/react-router";
import { useAdminStore, useMergedTenant } from "@/stores/admin";
import { useQueryClient } from "@tanstack/react-query";
import { qk } from "@/lib/api";
import type { Tenant, TenantAccent } from "@/lib/types";
import { toast } from "sonner";
import { cn } from "@/lib/utils";

export const Route = createFileRoute("/s/$tenant/admin/settings")({
  head: ({ params }) => ({ meta: [{ title: `Settings · ${params.tenant}` }] }),
  component: SettingsPage,
});

const ACCENTS: { value: TenantAccent; label: string; hex: string }[] = [
  { value: "emerald", label: "Emerald", hex: "#22a26b" },
  { value: "orange", label: "Sunset", hex: "#f08a3e" },
  { value: "purple", label: "Royal", hex: "#7d4ed6" },
  { value: "rose", label: "Rose", hex: "#e0426a" },
];

const GRADIENTS = [
  "linear-gradient(135deg, oklch(0.95 0.04 158), oklch(0.88 0.08 158))",
  "linear-gradient(135deg, oklch(0.94 0.05 145), oklch(0.85 0.1 145))",
  "linear-gradient(135deg, oklch(0.95 0.03 290), oklch(0.82 0.12 290))",
  "linear-gradient(135deg, oklch(0.96 0.04 60), oklch(0.85 0.13 50))",
  "linear-gradient(135deg, oklch(0.96 0.03 15), oklch(0.85 0.12 15))",
  "linear-gradient(135deg, oklch(0.95 0.04 240), oklch(0.82 0.12 240))",
];

function SettingsPage() {
  const { tenant: slug } = Route.useParams();
  const tenant = useMergedTenant(slug);
  const patch = useAdminStore((s) => s.patchTenant);
  const qc = useQueryClient();
  const [form, setForm] = useState<Tenant | undefined>(tenant);

  if (!form) return null;

  const set = <K extends keyof Tenant>(k: K, v: Tenant[K]) => setForm({ ...form, [k]: v });

  const save = () => {
    patch(slug, form);
    qc.invalidateQueries({ queryKey: qk.tenant(slug) });
    document.documentElement.setAttribute("data-tenant-accent", form.accent);
    toast.success("Storefront updated");
  };

  return (
    <div className="mx-auto max-w-5xl px-4 py-6 md:px-6 md:py-8">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight md:text-3xl">Settings</h1>
          <p className="mt-1 text-sm text-muted-foreground">Storefront branding, contact and operations</p>
        </div>
        <div className="flex items-center gap-2">
          <Button asChild variant="outline" size="sm" className="rounded-full">
            <Link to="/s/$tenant" params={{ tenant: slug }}>
              <ExternalLink className="h-3.5 w-3.5" /> View storefront
            </Link>
          </Button>
          <Button onClick={save} className="rounded-full" size="sm">
            <Save className="h-3.5 w-3.5" /> Save changes
          </Button>
        </div>
      </div>

      <div className="mt-6 grid gap-5 lg:grid-cols-[1fr_320px]">
        <div className="space-y-5">
          <Card title="Branding">
            <div className="grid gap-3 md:grid-cols-2">
              <Field label="Store name"><Input value={form.name} onChange={(e) => set("name", e.target.value)} /></Field>
              <Field label="Tagline"><Input value={form.tagline} onChange={(e) => set("tagline", e.target.value)} /></Field>
            </div>
            <div className="mt-3"><Field label="Description"><Textarea rows={3} value={form.description} onChange={(e) => set("description", e.target.value)} /></Field></div>

            <div className="mt-4 grid gap-3 md:grid-cols-2">
              <Field label="Logo emoji"><Input value={form.logoEmoji} onChange={(e) => set("logoEmoji", e.target.value)} className="w-24 text-center text-xl" maxLength={2} /></Field>
              <Field label="Category"><Input value={form.category} onChange={(e) => set("category", e.target.value)} /></Field>
            </div>

            <div className="mt-4">
              <Label className="mb-2 block text-[12.5px] font-medium">Accent color</Label>
              <div className="flex flex-wrap gap-2">
                {ACCENTS.map((a) => (
                  <button
                    key={a.value}
                    type="button"
                    onClick={() => set("accent", a.value)}
                    className={cn(
                      "flex items-center gap-2 rounded-full border px-3 py-1.5 text-[12px] font-medium transition-colors",
                      form.accent === a.value ? "border-primary bg-primary-soft" : "border-border bg-card hover:border-primary/40",
                    )}
                  >
                    <span className="h-3 w-3 rounded-full" style={{ background: a.hex }} />
                    {a.label}
                  </button>
                ))}
              </div>
            </div>

            <div className="mt-4">
              <Label className="mb-2 block text-[12.5px] font-medium">Banner</Label>
              <div className="grid grid-cols-3 gap-2 md:grid-cols-6">
                {GRADIENTS.map((g) => (
                  <button
                    key={g}
                    type="button"
                    onClick={() => set("bannerGradient", g)}
                    className={cn("h-16 rounded-xl ring-2 ring-offset-2 ring-offset-card transition", form.bannerGradient === g ? "ring-primary" : "ring-transparent")}
                    style={{ background: g }}
                  />
                ))}
              </div>
            </div>
          </Card>

          <Card title="Contact & address">
            <div className="grid gap-3 md:grid-cols-2">
              <Field label="Phone"><Input value={form.phone} onChange={(e) => set("phone", e.target.value)} /></Field>
              <Field label="Hours"><Input value={form.hours} onChange={(e) => set("hours", e.target.value)} /></Field>
            </div>
            <div className="mt-3"><Field label="Address"><Input value={form.address} onChange={(e) => set("address", e.target.value)} /></Field></div>
          </Card>

          <Card title="Operations">
            <div className="grid gap-3 md:grid-cols-3">
              <Field label="Delivery (min)"><Input type="number" value={form.deliveryMinutes} onChange={(e) => set("deliveryMinutes", Number(e.target.value))} /></Field>
              <Field label="Radius (km)"><Input type="number" value={form.deliveryRadiusKm} onChange={(e) => set("deliveryRadiusKm", Number(e.target.value))} /></Field>
              <Field label="Min order (INR)"><Input type="number" value={form.minOrder} onChange={(e) => set("minOrder", Number(e.target.value))} /></Field>
            </div>
          </Card>
        </div>

        <aside className="lg:sticky lg:top-20 lg:self-start">
          <div className="rounded-3xl border border-border/60 bg-card p-4 shadow-card">
            <h2 className="text-sm font-semibold">Live preview</h2>
            <div className="mt-3 overflow-hidden rounded-2xl border border-border/60">
              <div className="grid h-28 place-items-center text-5xl" style={{ background: form.bannerGradient }}>
                {form.logoEmoji}
              </div>
              <div className="space-y-1 p-4">
                <div className="text-sm font-semibold">{form.name}</div>
                <div className="text-[12px] text-muted-foreground line-clamp-2">{form.tagline}</div>
                <div className="mt-2 flex items-center gap-2 text-[11px] text-muted-foreground">
                  <span className="rounded-full bg-primary-soft px-2 py-0.5 text-[10px] font-medium text-accent-foreground">{form.category}</span>
                  <span>·</span>
                  <span>{form.deliveryMinutes} min</span>
                </div>
              </div>
            </div>
          </div>
        </aside>
      </div>
    </div>
  );
}

function Card({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section className="rounded-3xl border border-border/60 bg-card p-5 shadow-soft md:p-6">
      <h2 className="mb-4 text-base font-semibold">{title}</h2>
      {children}
    </section>
  );
}
function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div>
      <Label className="mb-1.5 block text-[12.5px] font-medium">{label}</Label>
      {children}
    </div>
  );
}
