import { createFileRoute, Link } from "@tanstack/react-router";
import { useSuspenseQuery } from "@tanstack/react-query";
import { Clock, Star, MapPin, ChevronRight } from "lucide-react";
import { api, qk } from "@/lib/api";
import { ProductCard } from "@/components/storefront/ProductCard";
import { PageTransition, FadeIn } from "@/components/motion/PageTransition";

export const Route = createFileRoute("/s/$tenant/")({
  head: ({ params }) => ({
    meta: [
      { title: `${params.tenant} — Marketly` },
      { name: "description", content: `Shop on ${params.tenant} via Marketly.` },
    ],
  }),
  loader: ({ context, params }) => {
    context.queryClient.ensureQueryData({
      queryKey: qk.products(params.tenant),
      queryFn: () => api.listProducts(params.tenant),
    });
  },
  component: TenantHome,
});

function TenantHome() {
  const { tenant: slug } = Route.useParams();
  const { data: tenant } = useSuspenseQuery({ queryKey: qk.tenant(slug), queryFn: () => api.getTenant(slug) });
  const { data: categories } = useSuspenseQuery({ queryKey: qk.categories(slug), queryFn: () => api.listCategories(slug) });
  const { data: products } = useSuspenseQuery({ queryKey: qk.products(slug), queryFn: () => api.listProducts(slug) });

  if (!tenant) return null;
  const trending = products.filter((p) => p.tags?.includes("trending")).slice(0, 8);
  const featured = products.filter((p) => p.tags?.includes("featured")).slice(0, 8);
  const bestsellers = products.filter((p) => p.tags?.includes("bestseller")).slice(0, 8);

  return (
    <PageTransition>
      {/* Banner */}
      <section className="relative overflow-hidden">
        <div className="mx-auto max-w-7xl px-4 pt-4 md:px-6 md:pt-6">
          <div
            className="relative overflow-hidden rounded-3xl px-6 py-8 md:px-10 md:py-12"
            style={{ background: tenant.bannerGradient }}
          >
            <div className="grid gap-6 md:grid-cols-[1.4fr_1fr] md:items-center">
              <div>
                <span className="inline-flex items-center gap-1 rounded-full bg-background/80 px-3 py-1 text-[11.5px] font-medium backdrop-blur">
                  <span className="text-base">{tenant.logoEmoji}</span> {tenant.category}
                </span>
                <h1 className="mt-3 text-3xl font-semibold tracking-tight md:text-5xl">{tenant.name}</h1>
                <p className="mt-2 max-w-md text-sm text-foreground/80 md:text-base">{tenant.tagline}</p>
                <div className="mt-4 flex flex-wrap items-center gap-4 text-[12.5px] text-foreground/70">
                  <span className="inline-flex items-center gap-1 rounded-full bg-background/70 px-3 py-1 backdrop-blur">
                    <Star className="h-3.5 w-3.5 fill-warning text-warning" /> {tenant.rating} ({tenant.reviewCount.toLocaleString()})
                  </span>
                  <span className="inline-flex items-center gap-1"><Clock className="h-3.5 w-3.5" /> {tenant.deliveryMinutes} min delivery</span>
                  <span className="inline-flex items-center gap-1"><MapPin className="h-3.5 w-3.5" /> {tenant.address}</span>
                </div>
              </div>
              <div className="hidden text-7xl md:block md:text-9xl md:text-right">{tenant.logoEmoji}</div>
            </div>
          </div>
        </div>
      </section>

      {/* Categories */}
      <section className="mx-auto max-w-7xl px-4 pt-8 md:px-6 md:pt-10">
        <div className="mb-4 flex items-end justify-between">
          <h2 className="text-lg font-semibold tracking-tight md:text-xl">Shop by category</h2>
        </div>
        <div className="no-scrollbar -mx-4 flex gap-3 overflow-x-auto px-4 pb-1 md:mx-0 md:grid md:grid-cols-6 md:px-0">
          {categories.map((c) => (
            <Link
              key={c.id}
              to="/s/$tenant/c/$category"
              params={{ tenant: slug, category: c.slug }}
              className="group flex w-28 shrink-0 flex-col items-center gap-2 rounded-2xl border border-border/60 bg-card px-3 py-4 text-center shadow-soft transition-colors hover:border-primary/40 md:w-auto"
            >
              <span className="grid h-12 w-12 place-items-center rounded-xl bg-primary-soft text-2xl">{c.emoji}</span>
              <span className="text-[12px] font-medium leading-tight">{c.name}</span>
            </Link>
          ))}
        </div>
      </section>

      <ProductRail title="Trending now" slug={slug} products={trending} />
      <ProductRail title="Featured picks" slug={slug} products={featured} />
      <ProductRail title="Bestsellers" slug={slug} products={bestsellers} />

      <div className="h-12" />
    </PageTransition>
  );
}

function ProductRail({ title, products, slug }: { title: string; slug: string; products: import("@/lib/types").Product[] }) {
  if (!products.length) return null;
  return (
    <section className="mx-auto max-w-7xl px-4 pt-10 md:px-6">
      <FadeIn>
        <div className="mb-4 flex items-end justify-between">
          <h2 className="text-lg font-semibold tracking-tight md:text-xl">{title}</h2>
          <Link
            to="/s/$tenant/search"
            params={{ tenant: slug }}
            className="inline-flex items-center text-[13px] font-medium text-primary hover:underline"
          >
            See all <ChevronRight className="h-4 w-4" />
          </Link>
        </div>
      </FadeIn>
      <div className="grid grid-cols-2 gap-3 md:grid-cols-4 md:gap-4">
        {products.map((p) => (
          <ProductCard key={p.id} product={p} />
        ))}
      </div>
    </section>
  );
}
