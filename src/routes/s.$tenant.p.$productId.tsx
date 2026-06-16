import { createFileRoute, Link, notFound } from "@tanstack/react-router";
import { useSuspenseQuery } from "@tanstack/react-query";
import { useEffect, useMemo, useState } from "react";
import { ArrowLeft, Check, Heart, Minus, Plus, Star, Truck, ShieldCheck, RotateCcw } from "lucide-react";
import { api, qk } from "@/lib/api";
import { formatCurrency, formatDate } from "@/lib/format";
import { ProductCard } from "@/components/storefront/ProductCard";
import { PageTransition } from "@/components/motion/PageTransition";
import { useCartStore, useWishlistStore, useRecentStore } from "@/stores";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import { toast } from "sonner";

export const Route = createFileRoute("/s/$tenant/p/$productId")({
  head: ({ params }) => ({
    meta: [
      { title: `Product · ${params.tenant}` },
      { name: "description", content: "Product detail" },
    ],
  }),
  loader: async ({ context, params }) => {
    const product = await context.queryClient.ensureQueryData({
      queryKey: qk.product(params.productId),
      queryFn: () => api.getProduct(params.productId),
    });
    if (!product) throw notFound();
    context.queryClient.ensureQueryData({
      queryKey: qk.reviews(params.productId),
      queryFn: () => api.listReviews(params.productId),
    });
    context.queryClient.ensureQueryData({
      queryKey: qk.products(params.tenant, { categorySlug: product.categorySlug }),
      queryFn: () => api.listProducts(params.tenant, { categorySlug: product.categorySlug }),
    });
  },
  component: ProductPage,
});

function ProductPage() {
  const { tenant: slug, productId } = Route.useParams();
  const { data: product } = useSuspenseQuery({ queryKey: qk.product(productId), queryFn: () => api.getProduct(productId) });
  const { data: reviews } = useSuspenseQuery({ queryKey: qk.reviews(productId), queryFn: () => api.listReviews(productId) });
  const categorySlug = product?.categorySlug ?? "";
  const { data: related } = useSuspenseQuery({
    queryKey: qk.products(slug, { categorySlug }),
    queryFn: () => api.listProducts(slug, { categorySlug }),
  });

  const add = useCartStore((s) => s.add);
  const wish = useWishlistStore((s) => (product ? s.has(slug, product.id) : false));
  const toggleWish = useWishlistStore((s) => s.toggle);
  const pushRecent = useRecentStore((s) => s.push);
  const [qty, setQty] = useState(1);
  const [variantId, setVariantId] = useState<string | undefined>(product?.variants?.[0]?.id);

  useEffect(() => {
    if (product) pushRecent(slug, product.id);
  }, [product, slug, pushRecent]);

  const finalPrice = useMemo(() => {
    if (!product) return 0;
    const v = product.variants?.find((x) => x.id === variantId);
    return product.price + (v?.priceDelta ?? 0);
  }, [product, variantId]);

  if (!product) return null;
  const discount = Math.round(((product.mrp - product.price) / product.mrp) * 100);
  const others = related.filter((p) => p.id !== product.id).slice(0, 4);

  return (
    <PageTransition>
      <div className="mx-auto max-w-7xl px-4 py-6 md:px-6 md:py-10">
        <Link to="/s/$tenant" params={{ tenant: slug }} className="inline-flex items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground">
          <ArrowLeft className="h-4 w-4" /> Continue shopping
        </Link>

        <div className="mt-6 grid gap-8 md:grid-cols-2 md:gap-12">
          {/* Gallery */}
          <div>
            <div
              className="relative grid aspect-square place-items-center overflow-hidden rounded-3xl border border-border/60 shadow-card"
              style={{ background: product.imageBg }}
            >
              <span className="text-[10rem]" aria-hidden>{product.imageEmoji}</span>
              {discount > 0 && (
                <span className="absolute left-4 top-4 rounded-full bg-foreground/90 px-2.5 py-1 text-xs font-semibold text-background">{discount}% OFF</span>
              )}
            </div>
            <div className="mt-3 grid grid-cols-4 gap-2">
              {[product.imageEmoji, "✨", "📦", "💚"].map((e, i) => (
                <div key={i} className="grid aspect-square place-items-center rounded-2xl border border-border/60 text-2xl" style={{ background: product.imageBg, opacity: i === 0 ? 1 : 0.7 }}>{e}</div>
              ))}
            </div>
          </div>

          {/* Info */}
          <div>
            {product.brand && <div className="text-[12px] font-medium uppercase tracking-wide text-muted-foreground">{product.brand}</div>}
            <h1 className="mt-1 text-2xl font-semibold tracking-tight md:text-3xl">{product.name}</h1>
            <div className="mt-2 flex items-center gap-3 text-sm">
              <span className="inline-flex items-center gap-1 rounded-full bg-success/10 px-2.5 py-1 text-[12px] font-semibold text-success">
                <Star className="h-3 w-3 fill-current" /> {product.rating.toFixed(1)}
              </span>
              <span className="text-muted-foreground">{product.reviewCount.toLocaleString()} ratings</span>
              {product.unit && <span className="text-muted-foreground">· {product.unit}</span>}
            </div>

            <div className="mt-5 flex items-end gap-3">
              <span className="text-3xl font-semibold">{formatCurrency(finalPrice)}</span>
              {product.mrp > product.price && (
                <>
                  <span className="text-base text-muted-foreground line-through">{formatCurrency(product.mrp)}</span>
                  <span className="text-[13px] font-semibold text-success">{discount}% off</span>
                </>
              )}
            </div>
            <p className="mt-1 text-[12.5px] text-muted-foreground">Inclusive of all taxes</p>

            {product.variants && product.variants.length > 0 && (
              <div className="mt-6">
                <div className="mb-2 text-sm font-semibold">Choose option</div>
                <div className="flex flex-wrap gap-2">
                  {product.variants.map((v) => (
                    <button
                      key={v.id}
                      onClick={() => setVariantId(v.id)}
                      className={cn(
                        "inline-flex h-10 items-center gap-2 rounded-full border px-4 text-sm transition-colors",
                        variantId === v.id
                          ? "border-primary bg-primary-soft text-accent-foreground"
                          : "border-border bg-surface hover:border-primary/40",
                      )}
                    >
                      {variantId === v.id && <Check className="h-3.5 w-3.5" />} {v.label}
                      {v.priceDelta ? <span className="text-xs text-muted-foreground">(+{formatCurrency(v.priceDelta)})</span> : null}
                    </button>
                  ))}
                </div>
              </div>
            )}

            <p className="mt-6 text-[14.5px] leading-relaxed text-muted-foreground">{product.description}</p>

            {/* Add to cart bar */}
            <div className="mt-6 flex items-center gap-3">
              <div className="inline-flex items-center rounded-full border border-border bg-surface">
                <button onClick={() => setQty((q) => Math.max(1, q - 1))} className="grid h-11 w-11 place-items-center rounded-full hover:bg-surface-muted" aria-label="Decrease quantity">
                  <Minus className="h-4 w-4" />
                </button>
                <span className="w-8 text-center text-sm font-semibold">{qty}</span>
                <button onClick={() => setQty((q) => Math.min(product.stock, q + 1))} className="grid h-11 w-11 place-items-center rounded-full hover:bg-surface-muted" aria-label="Increase quantity">
                  <Plus className="h-4 w-4" />
                </button>
              </div>
              <Button
                className="h-11 flex-1 rounded-full text-sm font-semibold"
                onClick={() => {
                  add(slug, product.id, variantId, qty);
                  toast.success(`${qty} × ${product.name} added to cart`);
                }}
              >
                Add to cart · {formatCurrency(finalPrice * qty)}
              </Button>
              <button
                onClick={() => toggleWish(slug, product.id)}
                className={cn(
                  "grid h-11 w-11 place-items-center rounded-full border border-border bg-surface",
                  wish && "border-destructive/30 bg-destructive/10",
                )}
                aria-label={wish ? "Remove from wishlist" : "Add to wishlist"}
              >
                <Heart className={cn("h-5 w-5", wish && "fill-destructive text-destructive")} />
              </button>
            </div>

            {/* Trust badges */}
            <div className="mt-7 grid grid-cols-3 gap-3 text-[11.5px]">
              {[{ icon: Truck, label: "Free delivery" }, { icon: ShieldCheck, label: "Secure payment" }, { icon: RotateCcw, label: "Easy returns" }].map((t) => (
                <div key={t.label} className="flex items-center gap-2 rounded-2xl border border-border/60 bg-card px-3 py-2.5">
                  <t.icon className="h-4 w-4 text-primary" />
                  <span className="font-medium">{t.label}</span>
                </div>
              ))}
            </div>
          </div>
        </div>

        {/* Reviews */}
        <section className="mt-14">
          <h2 className="text-xl font-semibold tracking-tight">Reviews</h2>
          {reviews.length === 0 ? (
            <p className="mt-3 text-sm text-muted-foreground">No reviews yet. Be the first to share your thoughts.</p>
          ) : (
            <ul className="mt-4 grid gap-3 md:grid-cols-2">
              {reviews.map((r) => (
                <li key={r.id} className="rounded-2xl border border-border/60 bg-card p-4 shadow-soft">
                  <div className="flex items-center justify-between">
                    <span className="text-sm font-semibold">{r.author}</span>
                    <span className="inline-flex items-center gap-1 rounded-full bg-success/10 px-2 py-0.5 text-[11px] font-semibold text-success">
                      <Star className="h-3 w-3 fill-current" /> {r.rating}
                    </span>
                  </div>
                  <p className="mt-1.5 text-[13.5px] text-muted-foreground">{r.body}</p>
                  <p className="mt-2 text-[11px] text-muted-foreground">{formatDate(r.date)}</p>
                </li>
              ))}
            </ul>
          )}
        </section>

        {/* Related */}
        {others.length > 0 && (
          <section className="mt-14">
            <h2 className="mb-4 text-xl font-semibold tracking-tight">You may also like</h2>
            <div className="grid grid-cols-2 gap-3 md:grid-cols-4 md:gap-4">
              {others.map((p) => (
                <ProductCard key={p.id} product={p} />
              ))}
            </div>
          </section>
        )}
      </div>
    </PageTransition>
  );
}
