import { createFileRoute, Link } from "@tanstack/react-router";
import { useSuspenseQuery } from "@tanstack/react-query";
import { useMemo, useState } from "react";
import { Filter, SlidersHorizontal, ArrowLeft } from "lucide-react";
import { api, qk } from "@/lib/api";
import { ProductCard } from "@/components/storefront/ProductCard";
import { PageTransition } from "@/components/motion/PageTransition";
import { Sheet, SheetContent, SheetHeader, SheetTitle, SheetTrigger } from "@/components/ui/sheet";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Slider } from "@/components/ui/slider";
import { Checkbox } from "@/components/ui/checkbox";
import { Label } from "@/components/ui/label";
import { Button } from "@/components/ui/button";

export const Route = createFileRoute("/s/$tenant/c/$category")({
  head: ({ params }) => ({
    meta: [
      { title: `${params.category} — ${params.tenant}` },
      { name: "description", content: `Browse ${params.category} at ${params.tenant} on Marketly.` },
    ],
  }),
  loader: ({ context, params }) => {
    context.queryClient.ensureQueryData({
      queryKey: qk.products(params.tenant, { categorySlug: params.category }),
      queryFn: () => api.listProducts(params.tenant, { categorySlug: params.category }),
    });
  },
  component: CategoryPage,
});

type SortKey = "relevance" | "price-asc" | "price-desc" | "rating";

function CategoryPage() {
  const { tenant: slug, category } = Route.useParams();
  const { data: categories } = useSuspenseQuery({ queryKey: qk.categories(slug), queryFn: () => api.listCategories(slug) });
  const { data: products } = useSuspenseQuery({
    queryKey: qk.products(slug, { categorySlug: category }),
    queryFn: () => api.listProducts(slug, { categorySlug: category }),
  });

  const cat = categories.find((c) => c.slug === category);
  const maxPrice = Math.max(...products.map((p) => p.price), 1000);

  const [sort, setSort] = useState<SortKey>("relevance");
  const [priceMax, setPriceMax] = useState(maxPrice);
  const [brands, setBrands] = useState<string[]>([]);
  const allBrands = useMemo(
    () => Array.from(new Set(products.map((p) => p.brand).filter(Boolean))) as string[],
    [products],
  );

  const filtered = useMemo(() => {
    let list = products.filter((p) => p.price <= priceMax);
    if (brands.length) list = list.filter((p) => p.brand && brands.includes(p.brand));
    switch (sort) {
      case "price-asc": list = [...list].sort((a, b) => a.price - b.price); break;
      case "price-desc": list = [...list].sort((a, b) => b.price - a.price); break;
      case "rating": list = [...list].sort((a, b) => b.rating - a.rating); break;
    }
    return list;
  }, [products, sort, priceMax, brands]);

  const filterPanel = (
    <div className="space-y-6">
      <div>
        <Label className="mb-3 block text-sm font-semibold">Max price</Label>
        <Slider value={[priceMax]} onValueChange={(v) => setPriceMax(v[0])} min={0} max={maxPrice} step={Math.max(1, Math.round(maxPrice / 100))} />
        <div className="mt-2 text-xs text-muted-foreground">Up to ₹{priceMax.toLocaleString()}</div>
      </div>
      {allBrands.length > 0 && (
        <div>
          <Label className="mb-3 block text-sm font-semibold">Brand</Label>
          <div className="space-y-2.5">
            {allBrands.map((b) => (
              <label key={b} className="flex cursor-pointer items-center gap-2.5 text-sm">
                <Checkbox
                  checked={brands.includes(b)}
                  onCheckedChange={(v) =>
                    setBrands((prev) => (v ? [...prev, b] : prev.filter((x) => x !== b)))
                  }
                />
                {b}
              </label>
            ))}
          </div>
        </div>
      )}
      <Button
        variant="outline"
        className="w-full"
        onClick={() => { setPriceMax(maxPrice); setBrands([]); setSort("relevance"); }}
      >
        Reset
      </Button>
    </div>
  );

  return (
    <PageTransition>
      <div className="mx-auto max-w-7xl px-4 py-6 md:px-6 md:py-10">
        <Link to="/s/$tenant" params={{ tenant: slug }} className="inline-flex items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground">
          <ArrowLeft className="h-4 w-4" /> Back to store
        </Link>
        <div className="mt-3 flex flex-wrap items-end justify-between gap-3">
          <div>
            <h1 className="text-2xl font-semibold tracking-tight md:text-3xl">
              <span className="mr-2 text-2xl">{cat?.emoji}</span>
              {cat?.name ?? category}
            </h1>
            <p className="mt-1 text-sm text-muted-foreground">{filtered.length} products</p>
          </div>
          <div className="flex items-center gap-2">
            {/* Mobile filter */}
            <Sheet>
              <SheetTrigger asChild>
                <Button variant="outline" size="sm" className="rounded-full md:hidden">
                  <Filter className="h-4 w-4" /> Filters
                </Button>
              </SheetTrigger>
              <SheetContent side="bottom" className="rounded-t-3xl">
                <SheetHeader>
                  <SheetTitle>Filters</SheetTitle>
                </SheetHeader>
                <div className="mt-4">{filterPanel}</div>
              </SheetContent>
            </Sheet>

            <Select value={sort} onValueChange={(v) => setSort(v as SortKey)}>
              <SelectTrigger className="h-9 w-[170px] rounded-full">
                <SlidersHorizontal className="mr-1 h-3.5 w-3.5" />
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="relevance">Relevance</SelectItem>
                <SelectItem value="price-asc">Price: low to high</SelectItem>
                <SelectItem value="price-desc">Price: high to low</SelectItem>
                <SelectItem value="rating">Top rated</SelectItem>
              </SelectContent>
            </Select>
          </div>
        </div>

        <div className="mt-8 grid gap-8 md:grid-cols-[240px_1fr]">
          <aside className="hidden md:block">
            <div className="sticky top-24 rounded-3xl border border-border/60 bg-card p-5 shadow-soft">
              {filterPanel}
            </div>
          </aside>
          <div>
            {filtered.length === 0 ? (
              <div className="grid place-items-center rounded-3xl border border-dashed border-border bg-surface-muted/40 px-6 py-20 text-center">
                <div className="text-4xl">🛒</div>
                <p className="mt-3 text-sm font-medium">No products match these filters</p>
                <Button variant="ghost" className="mt-3" onClick={() => { setPriceMax(maxPrice); setBrands([]); }}>Reset filters</Button>
              </div>
            ) : (
              <div className="grid grid-cols-2 gap-3 md:grid-cols-3 md:gap-4">
                {filtered.map((p) => (
                  <ProductCard key={p.id} product={p} />
                ))}
              </div>
            )}
          </div>
        </div>
      </div>
    </PageTransition>
  );
}
