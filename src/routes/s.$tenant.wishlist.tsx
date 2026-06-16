import { createFileRoute, Link } from "@tanstack/react-router";
import { useQueries } from "@tanstack/react-query";
import { Heart, ArrowLeft } from "lucide-react";
import { api, qk } from "@/lib/api";
import { useWishlistStore } from "@/stores";
import { ProductCard } from "@/components/storefront/ProductCard";
import { PageTransition } from "@/components/motion/PageTransition";
import { Button } from "@/components/ui/button";

export const Route = createFileRoute("/s/$tenant/wishlist")({
  head: ({ params }) => ({ meta: [{ title: `Wishlist · ${params.tenant}` }] }),
  component: WishlistPage,
});

function WishlistPage() {
  const { tenant: slug } = Route.useParams();
  const ids = useWishlistStore((s) => s.ids[slug] ?? []);
  const queries = useQueries({
    queries: ids.map((id) => ({ queryKey: qk.product(id), queryFn: () => api.getProduct(id) })),
  });
  const products = queries.map((q) => q.data).filter(Boolean) as import("@/lib/types").Product[];

  return (
    <PageTransition>
      <div className="mx-auto max-w-7xl px-4 py-6 md:px-6 md:py-10">
        <Link to="/s/$tenant" params={{ tenant: slug }} className="inline-flex items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground">
          <ArrowLeft className="h-4 w-4" /> Back to store
        </Link>
        <h1 className="mt-3 flex items-center gap-2 text-2xl font-semibold tracking-tight md:text-3xl">
          <Heart className="h-6 w-6 text-destructive" /> Your wishlist
        </h1>
        {products.length === 0 ? (
          <div className="mt-10 grid place-items-center rounded-3xl border border-dashed border-border bg-surface-muted/40 px-6 py-20 text-center">
            <div className="text-4xl">💚</div>
            <p className="mt-3 text-sm font-medium">No saved items yet</p>
            <Button asChild className="mt-4 rounded-full"><Link to="/s/$tenant" params={{ tenant: slug }}>Discover products</Link></Button>
          </div>
        ) : (
          <div className="mt-8 grid grid-cols-2 gap-3 md:grid-cols-4 md:gap-4">
            {products.map((p) => <ProductCard key={p.id} product={p} />)}
          </div>
        )}
      </div>
    </PageTransition>
  );
}
