import { useState } from "react";
import { Link, useNavigate } from "@tanstack/react-router";
import { ArrowLeft } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { useAdminStore, useTenantCategories } from "@/stores/admin";
import { toast } from "sonner";
import type { Product } from "@/lib/types";
import { formatCurrency } from "@/lib/format";

interface Props {
  tenantSlug: string;
  initial?: Product;
  mode: "create" | "edit";
}

const EMOJIS = ["🍎", "🥦", "🍞", "🥛", "🥚", "🍫", "☕", "🥑", "🍅", "🥕", "🥖", "🥐", "🎧", "📱", "💻", "💡", "🔌", "🔋", "💊", "🧴", "🩹", "🌡️", "🍰", "🎂"];
const BGS = [
  { name: "green", value: "oklch(0.96 0.04 158)" },
  { name: "lime", value: "oklch(0.96 0.05 130)" },
  { name: "yellow", value: "oklch(0.96 0.05 90)" },
  { name: "orange", value: "oklch(0.95 0.05 60)" },
  { name: "rose", value: "oklch(0.96 0.04 15)" },
  { name: "blue", value: "oklch(0.95 0.04 240)" },
  { name: "purple", value: "oklch(0.96 0.03 290)" },
  { name: "cream", value: "oklch(0.97 0.025 80)" },
  { name: "mint", value: "oklch(0.95 0.04 170)" },
];

export function ProductForm({ tenantSlug, initial, mode }: Props) {
  const navigate = useNavigate();
  const categories = useTenantCategories(tenantSlug);
  const upsert = useAdminStore((s) => s.upsertProduct);

  const [form, setForm] = useState<Product>(
    initial ?? {
      id: `np_${Math.random().toString(36).slice(2, 9)}`,
      tenantSlug,
      categorySlug: categories[0]?.slug ?? "",
      name: "",
      brand: "",
      description: "",
      price: 0,
      mrp: 0,
      unit: "",
      imageEmoji: "🛒",
      imageBg: BGS[0].value,
      stock: 24,
      rating: 4.5,
      reviewCount: 0,
      tags: [],
    },
  );

  const set = <K extends keyof Product>(k: K, v: Product[K]) => setForm((f) => ({ ...f, [k]: v }));

  return (
    <div className="mx-auto max-w-5xl px-4 py-6 md:px-6 md:py-8">
      <Link to="/s/$tenant/admin/products" params={{ tenant: tenantSlug }} className="inline-flex items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground">
        <ArrowLeft className="h-4 w-4" /> Products
      </Link>
      <h1 className="mt-3 text-2xl font-semibold tracking-tight md:text-3xl">
        {mode === "create" ? "New product" : "Edit product"}
      </h1>

      <form
        onSubmit={(e) => {
          e.preventDefault();
          if (!form.name || !form.categorySlug) {
            toast.error("Name and category are required");
            return;
          }
          upsert({ ...form, mrp: form.mrp || Math.round(form.price * 1.15) });
          toast.success(mode === "create" ? "Product created" : "Product updated");
          navigate({ to: "/s/$tenant/admin/products", params: { tenant: tenantSlug } });
        }}
        className="mt-6 grid gap-5 lg:grid-cols-[1fr_320px]"
      >
        <div className="space-y-5">
          <Card title="Basics">
            <div className="grid gap-3 md:grid-cols-2">
              <Field label="Name" required>
                <Input value={form.name} onChange={(e) => set("name", e.target.value)} placeholder="Royal Gala Apples" />
              </Field>
              <Field label="Brand">
                <Input value={form.brand ?? ""} onChange={(e) => set("brand", e.target.value)} placeholder="Farm Fresh" />
              </Field>
              <Field label="Category" required>
                <Select value={form.categorySlug} onValueChange={(v) => set("categorySlug", v)}>
                  <SelectTrigger><SelectValue placeholder="Choose…" /></SelectTrigger>
                  <SelectContent>
                    {categories.map((c) => (
                      <SelectItem key={c.id} value={c.slug}>{c.emoji} {c.name}</SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </Field>
              <Field label="Unit">
                <Input value={form.unit ?? ""} onChange={(e) => set("unit", e.target.value)} placeholder="1 kg / 500 ml" />
              </Field>
            </div>
            <div className="mt-3">
              <Field label="Description">
                <Textarea rows={3} value={form.description} onChange={(e) => set("description", e.target.value)} placeholder="Short, mouth-watering copy…" />
              </Field>
            </div>
          </Card>

          <Card title="Pricing & stock">
            <div className="grid gap-3 md:grid-cols-3">
              <Field label="Price (INR)" required>
                <Input type="number" min={0} value={form.price} onChange={(e) => set("price", Number(e.target.value))} />
              </Field>
              <Field label="MRP (strikethrough)">
                <Input type="number" min={0} value={form.mrp} onChange={(e) => set("mrp", Number(e.target.value))} placeholder="auto" />
              </Field>
              <Field label="Stock">
                <Input type="number" min={0} value={form.stock} onChange={(e) => set("stock", Number(e.target.value))} />
              </Field>
            </div>
          </Card>

          <Card title="Visual">
            <Field label="Icon">
              <div className="flex flex-wrap gap-2">
                {EMOJIS.map((e) => (
                  <button
                    key={e}
                    type="button"
                    onClick={() => set("imageEmoji", e)}
                    className={
                      "grid h-10 w-10 place-items-center rounded-xl text-xl transition " +
                      (form.imageEmoji === e ? "bg-primary text-primary-foreground" : "bg-surface-muted hover:bg-surface")
                    }
                  >
                    {e}
                  </button>
                ))}
              </div>
            </Field>
            <div className="mt-4">
              <Field label="Background">
                <div className="flex flex-wrap gap-2">
                  {BGS.map((b) => (
                    <button
                      key={b.name}
                      type="button"
                      onClick={() => set("imageBg", b.value)}
                      className={
                        "h-9 w-9 rounded-xl ring-2 ring-offset-2 ring-offset-card transition " +
                        (form.imageBg === b.value ? "ring-primary" : "ring-transparent")
                      }
                      style={{ background: b.value }}
                      aria-label={b.name}
                    />
                  ))}
                </div>
              </Field>
            </div>
          </Card>
        </div>

        {/* Preview + actions */}
        <aside className="lg:sticky lg:top-20 lg:self-start">
          <div className="rounded-3xl border border-border/60 bg-card p-5 shadow-card">
            <h2 className="text-sm font-semibold">Preview</h2>
            <div className="mt-3 rounded-2xl border border-border/60 bg-background p-4">
              <div className="aspect-square w-full overflow-hidden rounded-2xl grid place-items-center text-5xl" style={{ background: form.imageBg }}>
                {form.imageEmoji}
              </div>
              <div className="mt-3">
                <div className="line-clamp-1 text-[13px] font-semibold">{form.name || "Product name"}</div>
                {form.brand && <div className="text-[11px] text-muted-foreground">{form.brand}</div>}
                <div className="mt-1.5 flex items-center gap-2">
                  <span className="text-[15px] font-semibold">{formatCurrency(form.price || 0)}</span>
                  {form.mrp > form.price && form.mrp > 0 && (
                    <span className="text-[11.5px] text-muted-foreground line-through">{formatCurrency(form.mrp)}</span>
                  )}
                </div>
              </div>
            </div>
            <div className="mt-4 flex gap-2">
              <Button type="submit" className="h-11 flex-1 rounded-full text-sm font-semibold">
                {mode === "create" ? "Create" : "Save changes"}
              </Button>
            </div>
          </div>
        </aside>
      </form>
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
function Field({ label, required, children }: { label: string; required?: boolean; children: React.ReactNode }) {
  return (
    <div>
      <Label className="mb-1.5 block text-[12.5px] font-medium">{label}{required && <span className="text-destructive"> *</span>}</Label>
      {children}
    </div>
  );
}
