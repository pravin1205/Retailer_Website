import { Link, useParams } from "@tanstack/react-router";
import { Search, ShoppingBag, Heart, User, MapPin, Clock } from "lucide-react";
import { useSuspenseQuery } from "@tanstack/react-query";
import { api, qk } from "@/lib/api";
import { useCartStore } from "@/stores";
import { motion, AnimatePresence } from "framer-motion";
import { Button } from "@/components/ui/button";

export function TenantHeader() {
  const { tenant: tenantSlug } = useParams({ strict: false }) as { tenant: string };
  const { data: tenant } = useSuspenseQuery({
    queryKey: qk.tenant(tenantSlug),
    queryFn: () => api.getTenant(tenantSlug),
  });
  const count = useCartStore((s) => s.count(tenantSlug));

  if (!tenant) return null;

  return (
    <header className="sticky top-0 z-40 border-b border-border/60 bg-background/85 backdrop-blur-xl">
      <div className="mx-auto flex max-w-7xl items-center gap-3 px-4 py-3 md:gap-6 md:px-6 md:py-4">
        <Link
          to="/s/$tenant"
          params={{ tenant: tenantSlug }}
          className="flex min-w-0 items-center gap-2.5"
        >
          <div
            className="grid h-10 w-10 shrink-0 place-items-center rounded-2xl text-xl"
            style={{ background: tenant.bannerGradient }}
            aria-hidden
          >
            {tenant.logoEmoji}
          </div>
          <div className="min-w-0">
            <div className="truncate text-[15px] font-semibold leading-tight">{tenant.name}</div>
            <div className="hidden items-center gap-1 text-[11px] text-muted-foreground sm:flex">
              <MapPin className="h-3 w-3" />
              <span className="truncate">{tenant.address.split(",")[0]}</span>
              <span className="mx-1">·</span>
              <Clock className="h-3 w-3" />
              <span>{tenant.deliveryMinutes} min</span>
            </div>
          </div>
        </Link>

        <Link
          to="/s/$tenant/search"
          params={{ tenant: tenantSlug }}
          className="group hidden flex-1 items-center gap-2 rounded-2xl border border-border bg-surface-muted px-4 py-2.5 text-sm text-muted-foreground transition-colors hover:border-primary/40 hover:bg-surface md:flex"
        >
          <Search className="h-4 w-4" />
          <span className="truncate">Search for products, brands and more</span>
        </Link>

        <div className="ml-auto flex items-center gap-1.5">
          <Link
            to="/s/$tenant/search"
            params={{ tenant: tenantSlug }}
            className="grid h-10 w-10 place-items-center rounded-full text-foreground/70 hover:bg-surface-muted md:hidden"
            aria-label="Search"
          >
            <Search className="h-5 w-5" />
          </Link>
          <Link
            to="/s/$tenant/wishlist"
            params={{ tenant: tenantSlug }}
            className="hidden h-10 w-10 place-items-center rounded-full text-foreground/70 hover:bg-surface-muted md:grid"
            aria-label="Wishlist"
          >
            <Heart className="h-5 w-5" />
          </Link>
          <Link
            to="/s/$tenant/account"
            params={{ tenant: tenantSlug }}
            className="hidden h-10 w-10 place-items-center rounded-full text-foreground/70 hover:bg-surface-muted md:grid"
            aria-label="Account"
          >
            <User className="h-5 w-5" />
          </Link>
          <Button asChild size="sm" className="rounded-full px-4 shadow-soft">
            <Link to="/s/$tenant/cart" params={{ tenant: tenantSlug }}>
              <ShoppingBag className="h-4 w-4" />
              <span className="hidden sm:inline">Cart</span>
              <AnimatePresence>
                {count > 0 && (
                  <motion.span
                    key={count}
                    initial={{ scale: 0.6, opacity: 0 }}
                    animate={{ scale: 1, opacity: 1 }}
                    exit={{ scale: 0.6, opacity: 0 }}
                    transition={{ type: "spring", stiffness: 500, damping: 28 }}
                    className="ml-0.5 inline-flex h-5 min-w-5 items-center justify-center rounded-full bg-primary-foreground px-1.5 text-[11px] font-semibold text-primary"
                  >
                    {count}
                  </motion.span>
                )}
              </AnimatePresence>
            </Link>
          </Button>
        </div>
      </div>
    </header>
  );
}
