import { createFileRoute, Link, useNavigate } from "@tanstack/react-router";
import { useMemo, useState } from "react";
import { Plus, Search, Trash2, Pencil, Download } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { EmptyState } from "@/components/admin/EmptyState";
import { useTenantProducts, useTenantCategories, useAdminStore } from "@/stores/admin";
import { formatCurrency } from "@/lib/format";
import { cn } from "@/lib/utils";
import { downloadCsv, toCsv } from "@/lib/csv";
import { toast } from "sonner";

export const Route = createFileRoute("/s/$tenant/admin/products/")({
  head: ({ params }) => ({ meta: [{ title: `Products · ${params.tenant}` }] }),
  component: ProductsList,
});

function ProductsList() {
  const { tenant: slug } = Route.useParams();
  const navigate = useNavigate();
  const products = useTenantProducts(slug);
  const categories = useTenantCategories(slug);
  const upsert = useAdminStore((s) => s.upsertProduct);
  const remove = useAdminStore((s) => s.deleteProduct);

  const [q, setQ] = useState("");
  const [cat, setCat] = useState<string>("all");

  const filtered = useMemo(() => {
    let list = products;
    if (cat !== "all") list = list.filter((p) => p.categorySlug === cat);
    if (q) {
      const s = q.toLowerCase();
      list = list.filter((p) => p.name.toLowerCase().includes(s) || p.brand?.toLowerCase().includes(s));
    }
    return list;
  }, [products, cat, q]);

  return (
    <div className="mx-auto max-w-7xl px-4 py-6 md:px-6 md:py-8">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight md:text-3xl">Products</h1>
          <p className="mt-1 text-sm text-muted-foreground">{products.length} items · {products.filter((p) => p.stock <= 10).length} low stock</p>
        </div>
        <div className="flex items-center gap-2">
          <Button
            variant="outline"
            size="sm"
            className="rounded-full"
            onClick={() =>
              downloadCsv(
                `products-${slug}.csv`,
                toCsv(
                  filtered.map((p) => ({
                    id: p.id,
                    name: p.name,
                    brand: p.brand ?? "",
                    category: p.categorySlug,
                    price: p.price,
                    mrp: p.mrp,
                    stock: p.stock,
                    rating: p.rating.toFixed(1),
                  })),
                ),
              )
            }
          >
            <Download className="h-3.5 w-3.5" /> Export
          </Button>
          <Button asChild size="sm" className="rounded-full">
            <Link to="/s/$tenant/admin/products/new" params={{ tenant: slug }}>
              <Plus className="h-3.5 w-3.5" /> Add product
            </Link>
          </Button>
        </div>
      </div>

      <div className="mt-5 flex flex-wrap items-center gap-2">
        <div className="flex flex-wrap gap-1.5 overflow-x-auto">
          {[{ slug: "all", name: "All" }, ...categories].map((c) => (
            <button
              key={c.slug}
              onClick={() => setCat(c.slug)}
              className={cn(
                "shrink-0 rounded-full border px-3 py-1.5 text-[12px] font-medium transition-colors",
                cat === c.slug ? "border-primary bg-primary text-primary-foreground" : "border-border bg-card hover:border-primary/40",
              )}
            >
              {c.name}
            </button>
          ))}
        </div>
        <div className="relative ml-auto w-full md:w-72">
          <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
          <Input value={q} onChange={(e) => setQ(e.target.value)} placeholder="Search products…" className="pl-9" />
        </div>
      </div>

      {filtered.length === 0 ? (
        <div className="mt-6">
          <EmptyState
            title="No products yet"
            description="Add your first product to start selling."
            action={
              <Button asChild className="rounded-full">
                <Link to="/s/$tenant/admin/products/new" params={{ tenant: slug }}>
                  <Plus className="h-4 w-4" /> Add product
                </Link>
              </Button>
            }
          />
        </div>
      ) : (
        <div className="mt-5 overflow-hidden rounded-2xl border border-border/60 bg-card shadow-soft">
          <table className="w-full text-[13px]">
            <thead>
              <tr className="border-b border-border bg-surface-muted/60 text-left text-[11.5px] font-medium uppercase tracking-wide text-muted-foreground">
                <th className="px-4 py-3">Product</th>
                <th className="px-4 py-3 hidden md:table-cell">Category</th>
                <th className="px-4 py-3 text-right">Price</th>
                <th className="px-4 py-3 text-right">Stock</th>
                <th className="px-4 py-3 hidden md:table-cell text-right">Rating</th>
                <th className="px-4 py-3" />
              </tr>
            </thead>
            <tbody>
              {filtered.map((p) => (
                <tr key={p.id} className="border-b border-border last:border-0 hover:bg-surface-muted/40">
                  <td className="px-4 py-3">
                    <div className="flex items-center gap-3">
                      <span className="grid h-10 w-10 place-items-center rounded-xl text-lg" style={{ background: p.imageBg }}>{p.imageEmoji}</span>
                      <div className="min-w-0">
                        <div className="line-clamp-1 font-medium">{p.name}</div>
                        <div className="text-[11px] text-muted-foreground">{p.brand ?? "—"} · {p.unit ?? ""}</div>
                      </div>
                    </div>
                  </td>
                  <td className="px-4 py-3 hidden md:table-cell text-muted-foreground">{p.categorySlug}</td>
                  <td className="px-4 py-3 text-right">
                    <input
                      type="number"
                      defaultValue={p.price}
                      onBlur={(e) => {
                        const v = Number(e.target.value);
                        if (!Number.isFinite(v) || v < 0 || v === p.price) return;
                        upsert({ ...p, price: v });
                        toast.success("Price updated");
                      }}
                      className="w-20 rounded-md border border-transparent bg-transparent px-2 py-1 text-right tabular-nums hover:border-border focus:border-primary focus:outline-none"
                    />
                  </td>
                  <td className="px-4 py-3 text-right">
                    <input
                      type="number"
                      defaultValue={p.stock}
                      onBlur={(e) => {
                        const v = Number(e.target.value);
                        if (!Number.isFinite(v) || v < 0 || v === p.stock) return;
                        upsert({ ...p, stock: v });
                        toast.success("Stock updated");
                      }}
                      className={cn(
                        "w-16 rounded-md border border-transparent bg-transparent px-2 py-1 text-right tabular-nums hover:border-border focus:border-primary focus:outline-none",
                        p.stock <= 10 && "text-warning-foreground",
                        p.stock === 0 && "text-destructive",
                      )}
                    />
                  </td>
                  <td className="px-4 py-3 hidden md:table-cell text-right text-muted-foreground tabular-nums">★ {p.rating.toFixed(1)}</td>
                  <td className="px-4 py-3 text-right">
                    <div className="flex justify-end gap-1">
                      <button
                        onClick={() => navigate({ to: "/s/$tenant/admin/products/$id", params: { tenant: slug, id: p.id } })}
                        className="grid h-8 w-8 place-items-center rounded-lg text-muted-foreground hover:bg-surface-muted hover:text-foreground"
                        aria-label="Edit"
                      >
                        <Pencil className="h-3.5 w-3.5" />
                      </button>
                      <button
                        onClick={() => {
                          if (!confirm(`Delete "${p.name}"?`)) return;
                          remove(p.id);
                          toast.success("Product deleted");
                        }}
                        className="grid h-8 w-8 place-items-center rounded-lg text-muted-foreground hover:bg-destructive/10 hover:text-destructive"
                        aria-label="Delete"
                      >
                        <Trash2 className="h-3.5 w-3.5" />
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <p className="mt-3 text-[11px] text-muted-foreground">Tip: click price or stock to edit inline. Press Tab or click away to save. {formatCurrency(0).charAt(0)} = INR.</p>
    </div>
  );
}
