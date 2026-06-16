import { Link } from "@tanstack/react-router";
import { Star, Heart, Plus, Check } from "lucide-react";
import { motion } from "framer-motion";
import type { Product } from "@/lib/types";
import { formatCurrency } from "@/lib/format";
import { useCartStore, useWishlistStore } from "@/stores";
import { cn } from "@/lib/utils";
import { toast } from "sonner";

interface Props {
  product: Product;
  className?: string;
}

export function ProductCard({ product, className }: Props) {
  const inCart = useCartStore((s) => (s.carts[product.tenantSlug] ?? []).some((it) => it.productId === product.id));
  const add = useCartStore((s) => s.add);
  const wish = useWishlistStore((s) => s.has(product.tenantSlug, product.id));
  const toggleWish = useWishlistStore((s) => s.toggle);

  const discount = Math.round(((product.mrp - product.price) / product.mrp) * 100);

  return (
    <motion.article
      whileHover={{ y: -3 }}
      transition={{ type: "spring", stiffness: 300, damping: 24 }}
      className={cn(
        "group relative flex flex-col overflow-hidden rounded-3xl border border-border/60 bg-card shadow-card transition-shadow hover:shadow-pop",
        className,
      )}
    >
      <Link
        to="/s/$tenant/p/$productId"
        params={{ tenant: product.tenantSlug, productId: product.id }}
        className="relative block aspect-square overflow-hidden"
        style={{ background: product.imageBg }}
      >
        <motion.span
          className="absolute inset-0 grid place-items-center text-6xl md:text-7xl"
          whileHover={{ scale: 1.08 }}
          transition={{ type: "spring", stiffness: 220, damping: 20 }}
          aria-hidden
        >
          {product.imageEmoji}
        </motion.span>
        {discount > 0 && (
          <span className="absolute left-3 top-3 rounded-full bg-foreground/90 px-2 py-0.5 text-[10.5px] font-semibold text-background">
            {discount}% OFF
          </span>
        )}
        <button
          onClick={(e) => {
            e.preventDefault();
            toggleWish(product.tenantSlug, product.id);
          }}
          className="absolute right-3 top-3 grid h-9 w-9 place-items-center rounded-full bg-background/90 text-foreground shadow-soft transition-colors hover:bg-background"
          aria-label={wish ? "Remove from wishlist" : "Add to wishlist"}
        >
          <Heart className={cn("h-4 w-4", wish && "fill-destructive text-destructive")} />
        </button>
      </Link>

      <div className="flex flex-1 flex-col gap-2 p-3.5">
        <div className="flex items-center gap-1 text-[11px] text-muted-foreground">
          <Star className="h-3 w-3 fill-warning text-warning" />
          <span className="font-medium text-foreground">{product.rating.toFixed(1)}</span>
          <span>({product.reviewCount})</span>
          {product.unit && <span className="ml-auto">{product.unit}</span>}
        </div>
        <Link
          to="/s/$tenant/p/$productId"
          params={{ tenant: product.tenantSlug, productId: product.id }}
          className="line-clamp-2 text-[13.5px] font-medium leading-snug hover:text-primary"
        >
          {product.name}
        </Link>
        {product.brand && <div className="text-[11px] text-muted-foreground">{product.brand}</div>}
        <div className="mt-auto flex items-end justify-between gap-2 pt-1">
          <div className="flex flex-col">
            <span className="text-[15px] font-semibold leading-none">{formatCurrency(product.price)}</span>
            {product.mrp > product.price && (
              <span className="text-[11px] text-muted-foreground line-through">{formatCurrency(product.mrp)}</span>
            )}
          </div>
          <motion.button
            whileTap={{ scale: 0.93 }}
            onClick={() => {
              add(product.tenantSlug, product.id);
              toast.success(`Added ${product.name}`, { duration: 1400 });
            }}
            className={cn(
              "inline-flex h-9 items-center gap-1 rounded-full border px-3 text-[12px] font-semibold transition-colors",
              inCart
                ? "border-primary bg-primary text-primary-foreground"
                : "border-primary/30 bg-primary-soft text-accent-foreground hover:border-primary hover:bg-primary hover:text-primary-foreground",
            )}
            aria-label={inCart ? "Added to cart" : "Add to cart"}
          >
            {inCart ? <Check className="h-3.5 w-3.5" /> : <Plus className="h-3.5 w-3.5" />}
            {inCart ? "Added" : "Add"}
          </motion.button>
        </div>
      </div>
    </motion.article>
  );
}

export function ProductCardSkeleton() {
  return (
    <div className="flex flex-col overflow-hidden rounded-3xl border border-border/60 bg-card">
      <div className="aspect-square animate-pulse bg-surface-muted" />
      <div className="flex flex-col gap-2 p-3.5">
        <div className="h-3 w-1/3 animate-pulse rounded bg-surface-muted" />
        <div className="h-4 w-4/5 animate-pulse rounded bg-surface-muted" />
        <div className="h-3 w-1/2 animate-pulse rounded bg-surface-muted" />
        <div className="mt-2 h-9 w-full animate-pulse rounded-full bg-surface-muted" />
      </div>
    </div>
  );
}
