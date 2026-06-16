import { createFileRoute, Link } from "@tanstack/react-router";
import { useQueries, useSuspenseQuery } from "@tanstack/react-query";
import { useMemo, useState } from "react";
import { Minus, Plus, Trash2, ShoppingBag, Tag, Check } from "lucide-react";
import { api, qk } from "@/lib/api";
import { useCartStore } from "@/stores";
import { formatCurrency } from "@/lib/format";
import { PageTransition } from "@/components/motion/PageTransition";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { toast } from "sonner";
import { motion, AnimatePresence } from "framer-motion";

export const Route = createFileRoute("/s/$tenant/cart")({
  head: ({ params }) => ({ meta: [{ title: `Cart · ${params.tenant}` }] }),
  loader: ({ context }) => {
    context.queryClient.ensureQueryData({ queryKey: qk.coupons, queryFn: api.listCoupons });
  },
  component: CartPage,
});

function CartPage() {
  const { tenant: slug } = Route.useParams();
  const items = useCartStore((s) => s.carts[slug] ?? []);
  const setQty = useCartStore((s) => s.setQty);
  const remove = useCartStore((s) => s.remove);
  const { data: tenant } = useSuspenseQuery({ queryKey: qk.tenant(slug), queryFn: () => api.getTenant(slug) });
  const { data: coupons } = useSuspenseQuery({ queryKey: qk.coupons, queryFn: api.listCoupons });

  const queries = useQueries({
    queries: items.map((it) => ({ queryKey: qk.product(it.productId), queryFn: () => api.getProduct(it.productId) })),
  });
  const products = queries.map((q) => q.data).filter(Boolean) as import("@/lib/types").Product[];

  const [coupon, setCoupon] = useState("");
  const [appliedCoupon, setAppliedCoupon] = useState<string | null>(null);

  const subtotal = useMemo(() => {
    return items.reduce((sum, it) => {
      const p = products.find((x) => x.id === it.productId);
      if (!p) return sum;
      const variant = p.variants?.find((v) => v.id === it.variantId);
      return sum + (p.price + (variant?.priceDelta ?? 0)) * it.quantity;
    }, 0);
  }, [items, products]);

  const matchedCoupon = appliedCoupon ? coupons.find((c) => c.code === appliedCoupon) : null;
  const discount = useMemo(() => {
    if (!matchedCoupon) return 0;
    if (matchedCoupon.minOrder && subtotal < matchedCoupon.minOrder) return 0;
    if (matchedCoupon.percent) return Math.round((subtotal * matchedCoupon.percent) / 100);
    if (matchedCoupon.flat) return matchedCoupon.flat;
    return 0;
  }, [matchedCoupon, subtotal]);
  const delivery = subtotal > 0 && subtotal < (tenant?.minOrder ?? 0) ? 29 : subtotal === 0 ? 0 : 0;
  const total = subtotal - discount + delivery;

  const applyCoupon = () => {
    const c = coupons.find((x) => x.code.toLowerCase() === coupon.toLowerCase());
    if (!c) { toast.error("Coupon not found"); return; }
    if (c.minOrder && subtotal < c.minOrder) { toast.error(`Minimum order ₹${c.minOrder} required`); return; }
    setAppliedCoupon(c.code);
    toast.success(`Coupon ${c.code} applied`);
  };

  if (items.length === 0) {
    return (
      <PageTransition>
        <div className="mx-auto max-w-md px-4 py-20 text-center">
          <div className="mx-auto grid h-20 w-20 place-items-center rounded-3xl bg-primary-soft text-4xl">🛒</div>
          <h1 className="mt-6 text-2xl font-semibold tracking-tight">Your cart is empty</h1>
          <p className="mt-2 text-sm text-muted-foreground">Add some essentials to get going.</p>
          <Button asChild className="mt-6 rounded-full">
            <Link to="/s/$tenant" params={{ tenant: slug }}>Start shopping</Link>
          </Button>
        </div>
      </PageTransition>
    );
  }

  return (
    <PageTransition>
      <div className="mx-auto max-w-7xl px-4 py-6 md:px-6 md:py-10">
        <h1 className="text-2xl font-semibold tracking-tight md:text-3xl">Your cart</h1>
        <p className="mt-1 text-sm text-muted-foreground">{items.length} item{items.length === 1 ? "" : "s"} from {tenant?.name}</p>

        <div className="mt-8 grid gap-6 lg:grid-cols-[1fr_380px]">
          {/* Items */}
          <ul className="space-y-3">
            <AnimatePresence initial={false}>
              {items.map((it) => {
                const p = products.find((x) => x.id === it.productId);
                if (!p) return null;
                const variant = p.variants?.find((v) => v.id === it.variantId);
                const unit = p.price + (variant?.priceDelta ?? 0);
                return (
                  <motion.li
                    key={`${it.productId}-${it.variantId ?? ""}`}
                    layout
                    initial={{ opacity: 0, y: 6 }}
                    animate={{ opacity: 1, y: 0 }}
                    exit={{ opacity: 0, x: -20 }}
                    className="flex gap-3 rounded-3xl border border-border/60 bg-card p-3 shadow-soft md:p-4"
                  >
                    <div className="grid h-20 w-20 shrink-0 place-items-center rounded-2xl text-3xl md:h-24 md:w-24 md:text-4xl" style={{ background: p.imageBg }}>
                      {p.imageEmoji}
                    </div>
                    <div className="flex min-w-0 flex-1 flex-col">
                      <div className="flex items-start justify-between gap-2">
                        <div className="min-w-0">
                          <Link to="/s/$tenant/p/$productId" params={{ tenant: slug, productId: p.id }} className="line-clamp-1 text-[14.5px] font-medium hover:text-primary">{p.name}</Link>
                          <div className="text-[12px] text-muted-foreground">{p.brand}{variant ? ` · ${variant.label}` : p.unit ? ` · ${p.unit}` : ""}</div>
                        </div>
                        <button onClick={() => remove(slug, it.productId, it.variantId)} className="grid h-8 w-8 place-items-center rounded-full text-muted-foreground hover:bg-destructive/10 hover:text-destructive" aria-label="Remove item">
                          <Trash2 className="h-4 w-4" />
                        </button>
                      </div>
                      <div className="mt-auto flex items-center justify-between gap-2 pt-2">
                        <div className="inline-flex items-center rounded-full border border-border">
                          <button onClick={() => setQty(slug, it.productId, it.variantId, it.quantity - 1)} className="grid h-9 w-9 place-items-center rounded-full hover:bg-surface-muted" aria-label="Decrease"><Minus className="h-3.5 w-3.5" /></button>
                          <span className="w-7 text-center text-sm font-semibold">{it.quantity}</span>
                          <button onClick={() => setQty(slug, it.productId, it.variantId, it.quantity + 1)} className="grid h-9 w-9 place-items-center rounded-full hover:bg-surface-muted" aria-label="Increase"><Plus className="h-3.5 w-3.5" /></button>
                        </div>
                        <div className="text-right">
                          <div className="text-[15px] font-semibold">{formatCurrency(unit * it.quantity)}</div>
                          {p.mrp > p.price && <div className="text-[11.5px] text-muted-foreground line-through">{formatCurrency(p.mrp * it.quantity)}</div>}
                        </div>
                      </div>
                    </div>
                  </motion.li>
                );
              })}
            </AnimatePresence>
          </ul>

          {/* Summary */}
          <aside className="lg:sticky lg:top-24 lg:self-start">
            <div className="rounded-3xl border border-border/60 bg-card p-5 shadow-card">
              <h2 className="text-base font-semibold">Order summary</h2>

              <div className="mt-4 flex gap-2">
                <Input
                  placeholder="Coupon code"
                  value={coupon}
                  onChange={(e) => setCoupon(e.target.value)}
                  className="rounded-full"
                />
                <Button variant="outline" onClick={applyCoupon} className="rounded-full">
                  {appliedCoupon === coupon ? <Check className="h-4 w-4" /> : <Tag className="h-4 w-4" />} Apply
                </Button>
              </div>
              <div className="mt-2 flex flex-wrap gap-1.5 text-[11px]">
                {coupons.slice(0, 3).map((c) => (
                  <button key={c.code} onClick={() => { setCoupon(c.code); setAppliedCoupon(c.code); toast.success(`${c.code} applied`); }} className="rounded-full border border-dashed border-primary/40 bg-primary-soft px-2 py-1 font-mono font-semibold text-accent-foreground hover:bg-primary hover:text-primary-foreground">
                    {c.code}
                  </button>
                ))}
              </div>

              <dl className="mt-5 space-y-2 text-sm">
                <Row label="Subtotal" value={formatCurrency(subtotal)} />
                {discount > 0 && <Row label="Discount" value={`− ${formatCurrency(discount)}`} accent="success" />}
                <Row label="Delivery" value={delivery ? formatCurrency(delivery) : "Free"} />
                <div className="my-3 border-t border-border" />
                <Row label="Total" value={formatCurrency(total)} bold />
              </dl>

              <Button asChild className="mt-5 h-12 w-full rounded-full text-sm font-semibold shadow-pop">
                <Link to="/s/$tenant/checkout" params={{ tenant: slug }}>
                  <ShoppingBag className="h-4 w-4" /> Proceed to checkout
                </Link>
              </Button>
              <p className="mt-3 text-center text-[11px] text-muted-foreground">Safe & secure payments</p>
            </div>
          </aside>
        </div>
      </div>
    </PageTransition>
  );
}

function Row({ label, value, bold, accent }: { label: string; value: string; bold?: boolean; accent?: "success" }) {
  return (
    <div className={`flex items-center justify-between ${bold ? "text-base font-semibold" : "text-muted-foreground"}`}>
      <dt>{label}</dt>
      <dd className={accent === "success" ? "text-success font-semibold" : bold ? "text-foreground" : "text-foreground"}>{value}</dd>
    </div>
  );
}
