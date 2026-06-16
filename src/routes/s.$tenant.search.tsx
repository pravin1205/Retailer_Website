import { createFileRoute, Link } from "@tanstack/react-router";
import { useQuery } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import { Search as SearchIcon, X } from "lucide-react";
import { api, qk } from "@/lib/api";
import { ProductCard, ProductCardSkeleton } from "@/components/storefront/ProductCard";
import { PageTransition } from "@/components/motion/PageTransition";
import { useDebounced } from "@/hooks/use-media";
import { Input } from "@/components/ui/input";
import { formatCurrency } from "@/lib/format";

export const Route = createFileRoute("/s/$tenant/search")({
  head: ({ params }) => ({ meta: [{ title: `Search · ${params.tenant}` }] }),
  component: SearchPage,
});

function SearchPage() {
  const { tenant: slug } = Route.useParams();
  const [q, setQ] = useState("");
  const debounced = useDebounced(q, 200);

  const { data: results, isFetching } = useQuery({
    queryKey: qk.products(slug, { search: debounced }),
    queryFn: () => api.listProducts(slug, { search: debounced || undefined }),
  });
  const { data: autocomplete } = useQuery({
    queryKey: qk.autocomplete(slug, debounced),
    queryFn: () => api.searchAutocomplete(slug, debounced),
    enabled: debounced.length > 0 && debounced.length < 3,
  });

  useEffect(() => {
    const input = document.getElementById("search-input");
    input?.focus();
  }, []);

  return (
    <PageTransition>
      <div className="mx-auto max-w-5xl px-4 py-6 md:px-6 md:py-10">
        <div className="relative">
          <SearchIcon className="absolute left-4 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
          <Input
            id="search-input"
            value={q}
            onChange={(e) => setQ(e.target.value)}
            placeholder="Search for products, brands…"
            className="h-12 rounded-full pl-11 pr-12 text-sm shadow-soft"
          />
          {q && (
            <button onClick={() => setQ("")} className="absolute right-3 top-1/2 grid h-8 w-8 -translate-y-1/2 place-items-center rounded-full text-muted-foreground hover:bg-surface-muted" aria-label="Clear search">
              <X className="h-4 w-4" />
            </button>
          )}
        </div>

        {/* Autocomplete suggestions */}
        {debounced && autocomplete && autocomplete.length > 0 && debounced.length < 3 && (
          <ul className="mt-2 overflow-hidden rounded-2xl border border-border/60 bg-card shadow-pop">
            {autocomplete.map((p) => (
              <li key={p.id}>
                <Link to="/s/$tenant/p/$productId" params={{ tenant: slug, productId: p.id }} className="flex items-center gap-3 px-3 py-2 hover:bg-surface-muted">
                  <span className="grid h-9 w-9 place-items-center rounded-xl text-xl" style={{ background: p.imageBg }}>{p.imageEmoji}</span>
                  <span className="flex-1 truncate text-sm">{p.name}</span>
                  <span className="text-sm font-semibold">{formatCurrency(p.price)}</span>
                </Link>
              </li>
            ))}
          </ul>
        )}

        {/* Results */}
        <div className="mt-8">
          {!q && (
            <div className="grid place-items-center rounded-3xl border border-dashed border-border bg-surface-muted/40 px-6 py-20 text-center">
              <SearchIcon className="h-8 w-8 text-muted-foreground" />
              <p className="mt-3 text-sm font-medium">Search for anything</p>
              <p className="mt-1 text-xs text-muted-foreground">Try “milk”, “apples”, “airpods”…</p>
            </div>
          )}
          {q && (
            <>
              <p className="mb-4 text-sm text-muted-foreground">
                {isFetching ? "Searching…" : `${results?.length ?? 0} results for "${q}"`}
              </p>
              {isFetching && (
                <div className="grid grid-cols-2 gap-3 md:grid-cols-4">
                  {Array.from({ length: 8 }).map((_, i) => <ProductCardSkeleton key={i} />)}
                </div>
              )}
              {!isFetching && results && results.length > 0 && (
                <div className="grid grid-cols-2 gap-3 md:grid-cols-4">
                  {results.map((p) => <ProductCard key={p.id} product={p} />)}
                </div>
              )}
              {!isFetching && results && results.length === 0 && (
                <div className="grid place-items-center rounded-3xl border border-dashed border-border bg-surface-muted/40 px-6 py-20 text-center">
                  <div className="text-4xl">🔍</div>
                  <p className="mt-3 text-sm font-medium">No results found</p>
                  <p className="mt-1 text-xs text-muted-foreground">Try a different keyword.</p>
                </div>
              )}
            </>
          )}
        </div>
      </div>
    </PageTransition>
  );
}
