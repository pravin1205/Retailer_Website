import { createFileRoute } from "@tanstack/react-router";
import { useState } from "react";
import { Plus, Trash2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle, DialogTrigger,
} from "@/components/ui/dialog";
import { useAdminStore, useTenantCategories, useTenantProducts } from "@/stores/admin";
import type { Category } from "@/lib/types";
import { toast } from "sonner";

export const Route = createFileRoute("/s/$tenant/admin/categories")({
  head: ({ params }) => ({ meta: [{ title: `Categories · ${params.tenant}` }] }),
  component: CategoriesPage,
});

function CategoriesPage() {
  const { tenant: slug } = Route.useParams();
  const categories = useTenantCategories(slug);
  const products = useTenantProducts(slug);
  const upsert = useAdminStore((s) => s.upsertCategory);
  const remove = useAdminStore((s) => s.deleteCategory);
  const [editing, setEditing] = useState<Category | null>(null);
  const [open, setOpen] = useState(false);

  const counts = products.reduce<Record<string, number>>((acc, p) => {
    acc[p.categorySlug] = (acc[p.categorySlug] ?? 0) + 1;
    return acc;
  }, {});

  const onOpenNew = () => {
    setEditing({ id: `nc_${Math.random().toString(36).slice(2, 9)}`, tenantSlug: slug, name: "", slug: "", emoji: "📦" });
    setOpen(true);
  };

  return (
    <div className="mx-auto max-w-5xl px-4 py-6 md:px-6 md:py-8">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight md:text-3xl">Categories</h1>
          <p className="mt-1 text-sm text-muted-foreground">{categories.length} categories</p>
        </div>
        <Button onClick={onOpenNew} className="rounded-full" size="sm">
          <Plus className="h-3.5 w-3.5" /> Add category
        </Button>
      </div>

      <ul className="mt-6 grid gap-3 md:grid-cols-2 lg:grid-cols-3">
        {categories.map((c) => (
          <li key={c.id} className="group rounded-2xl border border-border/60 bg-card p-4 shadow-soft transition-shadow hover:shadow-card">
            <div className="flex items-center gap-3">
              <span className="grid h-11 w-11 place-items-center rounded-2xl bg-primary-soft text-2xl">{c.emoji}</span>
              <div className="min-w-0 flex-1">
                <div className="truncate text-sm font-semibold">{c.name}</div>
                <div className="text-[11.5px] text-muted-foreground">{counts[c.slug] ?? 0} products</div>
              </div>
              <div className="flex gap-1 opacity-0 transition-opacity group-hover:opacity-100">
                <button
                  onClick={() => {
                    setEditing(c);
                    setOpen(true);
                  }}
                  className="rounded-md px-2 py-1 text-[12px] hover:bg-surface-muted"
                >
                  Edit
                </button>
                <button
                  onClick={() => {
                    if (!confirm(`Delete category "${c.name}"?`)) return;
                    remove(c.id);
                    toast.success("Category deleted");
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
        <DialogTrigger className="hidden" />
        <DialogContent>
          <DialogHeader><DialogTitle>{editing && categories.find((c) => c.id === editing.id) ? "Edit category" : "New category"}</DialogTitle></DialogHeader>
          {editing && (
            <form
              className="space-y-3"
              onSubmit={(e) => {
                e.preventDefault();
                if (!editing.name) return;
                const slugified = editing.slug || editing.name.toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, "");
                upsert({ ...editing, slug: slugified });
                toast.success("Saved");
                setOpen(false);
              }}
            >
              <div>
                <Label className="mb-1.5 block text-[12.5px] font-medium">Name</Label>
                <Input value={editing.name} onChange={(e) => setEditing({ ...editing, name: e.target.value })} placeholder="Snacks" />
              </div>
              <div>
                <Label className="mb-1.5 block text-[12.5px] font-medium">URL slug</Label>
                <Input value={editing.slug} onChange={(e) => setEditing({ ...editing, slug: e.target.value })} placeholder="auto-generated" />
              </div>
              <div>
                <Label className="mb-1.5 block text-[12.5px] font-medium">Emoji</Label>
                <Input value={editing.emoji} onChange={(e) => setEditing({ ...editing, emoji: e.target.value })} maxLength={2} className="w-20 text-center text-xl" />
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
