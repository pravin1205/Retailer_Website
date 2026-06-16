import { createFileRoute } from "@tanstack/react-router";
import { useState } from "react";
import { Plus, Trash2, Percent, IndianRupee } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Switch } from "@/components/ui/switch";
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { useAdminStore, useAllCoupons } from "@/stores/admin";
import { formatCurrency } from "@/lib/format";
import type { Coupon } from "@/lib/types";
import { toast } from "sonner";

export const Route = createFileRoute("/s/$tenant/admin/coupons")({
  head: ({ params }) => ({ meta: [{ title: `Coupons · ${params.tenant}` }] }),
  component: CouponsPage,
});

function CouponsPage() {
  const coupons = useAllCoupons();
  const upsert = useAdminStore((s) => s.upsertCoupon);
  const remove = useAdminStore((s) => s.deleteCoupon);
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<Coupon | null>(null);

  const blank = (): Coupon => ({ code: "", description: "", percent: 10, minOrder: 0 });

  return (
    <div className="mx-auto max-w-5xl px-4 py-6 md:px-6 md:py-8">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight md:text-3xl">Coupons</h1>
          <p className="mt-1 text-sm text-muted-foreground">{coupons.filter((c) => !c.disabled).length} active · {coupons.length} total</p>
        </div>
        <Button
          className="rounded-full"
          size="sm"
          onClick={() => {
            setEditing(blank());
            setOpen(true);
          }}
        >
          <Plus className="h-3.5 w-3.5" /> New coupon
        </Button>
      </div>

      <ul className="mt-6 grid gap-3 md:grid-cols-2 lg:grid-cols-3">
        {coupons.map((c) => (
          <li key={c.code} className="group rounded-2xl border border-border/60 bg-card p-5 shadow-soft transition-shadow hover:shadow-card">
            <div className="flex items-start justify-between gap-2">
              <div className="min-w-0">
                <div className="inline-flex items-center gap-1.5 rounded-md bg-primary-soft px-2 py-0.5 font-mono text-[12px] font-semibold text-accent-foreground">
                  {c.code}
                </div>
                <div className="mt-2 line-clamp-2 text-[13px] text-muted-foreground">{c.description}</div>
              </div>
              <div className="grid h-9 w-9 place-items-center rounded-xl bg-surface-muted text-primary">
                {c.percent ? <Percent className="h-4 w-4" /> : <IndianRupee className="h-4 w-4" />}
              </div>
            </div>
            <div className="mt-4 grid grid-cols-2 gap-2 text-[12px]">
              <Stat label="Discount" value={c.percent ? `${c.percent}%` : formatCurrency(c.flat ?? 0)} />
              <Stat label="Min order" value={c.minOrder ? formatCurrency(c.minOrder) : "—"} />
            </div>
            <div className="mt-4 flex items-center justify-between">
              <label className="inline-flex items-center gap-2 text-[12px]">
                <Switch checked={!c.disabled} onCheckedChange={(v) => upsert({ ...c, disabled: !v })} />
                <span>{c.disabled ? "Disabled" : "Active"}</span>
              </label>
              <div className="flex gap-1">
                <button onClick={() => { setEditing(c); setOpen(true); }} className="rounded-md px-2 py-1 text-[12px] hover:bg-surface-muted">Edit</button>
                <button
                  onClick={() => {
                    if (!confirm(`Delete coupon ${c.code}?`)) return;
                    remove(c.code);
                    toast.success("Coupon deleted");
                  }}
                  className="grid h-7 w-7 place-items-center rounded-md text-muted-foreground hover:bg-destructive/10 hover:text-destructive"
                  aria-label="Delete"
                >
                  <Trash2 className="h-3.5 w-3.5" />
                </button>
              </div>
            </div>
          </li>
        ))}
      </ul>

      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent>
          <DialogHeader><DialogTitle>{editing && coupons.find((c) => c.code === editing.code) ? "Edit coupon" : "New coupon"}</DialogTitle></DialogHeader>
          {editing && (
            <form
              className="space-y-3"
              onSubmit={(e) => {
                e.preventDefault();
                if (!editing.code) return toast.error("Code is required");
                upsert({ ...editing, code: editing.code.toUpperCase() });
                toast.success("Saved");
                setOpen(false);
              }}
            >
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <Label className="mb-1.5 block text-[12.5px] font-medium">Code</Label>
                  <Input value={editing.code} onChange={(e) => setEditing({ ...editing, code: e.target.value.toUpperCase() })} className="font-mono" placeholder="SAVE20" />
                </div>
                <div>
                  <Label className="mb-1.5 block text-[12.5px] font-medium">Type</Label>
                  <div className="flex gap-1 rounded-md border border-border bg-surface-muted p-0.5">
                    <button type="button" onClick={() => setEditing({ ...editing, percent: 10, flat: undefined })} className={"flex-1 rounded-md py-1 text-[12px] " + (editing.percent != null ? "bg-card shadow-soft" : "")}>%</button>
                    <button type="button" onClick={() => setEditing({ ...editing, percent: undefined, flat: 50 })} className={"flex-1 rounded-md py-1 text-[12px] " + (editing.flat != null ? "bg-card shadow-soft" : "")}>Flat</button>
                  </div>
                </div>
              </div>
              <div>
                <Label className="mb-1.5 block text-[12.5px] font-medium">Description</Label>
                <Input value={editing.description} onChange={(e) => setEditing({ ...editing, description: e.target.value })} placeholder="10% off your first order" />
              </div>
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <Label className="mb-1.5 block text-[12.5px] font-medium">{editing.percent != null ? "Percent" : "Flat (INR)"}</Label>
                  {editing.percent != null ? (
                    <Input type="number" min={1} max={100} value={editing.percent} onChange={(e) => setEditing({ ...editing, percent: Number(e.target.value) })} />
                  ) : (
                    <Input type="number" min={1} value={editing.flat ?? 0} onChange={(e) => setEditing({ ...editing, flat: Number(e.target.value) })} />
                  )}
                </div>
                <div>
                  <Label className="mb-1.5 block text-[12.5px] font-medium">Min order (INR)</Label>
                  <Input type="number" min={0} value={editing.minOrder ?? 0} onChange={(e) => setEditing({ ...editing, minOrder: Number(e.target.value) })} />
                </div>
              </div>
              <DialogFooter>
                <Button type="button" variant="outline" onClick={() => setOpen(false)}>Cancel</Button>
                <Button type="submit">Save</Button>
              </DialogFooter>
            </form>
          )}
        </DialogContent>
      </Dialog>
    </div>
  );
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-lg bg-surface-muted px-3 py-2">
      <div className="text-[10.5px] uppercase tracking-wide text-muted-foreground">{label}</div>
      <div className="mt-0.5 font-semibold tabular-nums">{value}</div>
    </div>
  );
}
