import { createFileRoute, useNavigate, Link } from "@tanstack/react-router";
import { useQueries, useSuspenseQuery } from "@tanstack/react-query";
import { useMemo, useState } from "react";
import { Check, CreditCard, Wallet, Banknote, ArrowLeft } from "lucide-react";
import { api, qk } from "@/lib/api";
import { useCartStore, useOrdersStore, useAuthStore } from "@/stores";
import { formatCurrency } from "@/lib/format";
import { useQueryClient } from "@tanstack/react-query";
import { PageTransition } from "@/components/motion/PageTransition";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { cn } from "@/lib/utils";
import { toast } from "sonner";
import type { Address, Order } from "@/lib/types";

export const Route = createFileRoute("/s/$tenant/checkout")({
  head: ({ params }) => ({ meta: [{ title: `Checkout · ${params.tenant}` }] }),
  component: CheckoutPage,
});

const SLOTS = ["Today, 5:00–6:00 PM", "Today, 7:00–8:00 PM", "Tomorrow, 9:00–10:00 AM", "Tomorrow, 12:00–1:00 PM"];

function CheckoutPage() {
  const { tenant: slug } = Route.useParams();
  const navigate = useNavigate();
  const items = useCartStore((s) => s.carts[slug] ?? []);
  const clear = useCartStore((s) => s.clear);
  const addOrder = useOrdersStore((s) => s.add);
  const user = useAuthStore((s) => s.user);

  const queryClient = useQueryClient();
  const { data: tenant } = useSuspenseQuery({ queryKey: qk.tenant(slug), queryFn: () => api.getTenant(slug) });
  const queries = useQueries({
    queries: items.map((it) => ({ queryKey: qk.product(it.productId), queryFn: () => api.getProduct(it.productId) })),
  });
  const products = queries.map((q) => q.data).filter(Boolean) as import("@/lib/types").Product[];

  const subtotal = useMemo(() => items.reduce((s, it) => {
    const p = products.find((x) => x.id === it.productId);
    if (!p) return s;
    const v = p.variants?.find((x) => x.id === it.variantId);
    return s + (p.price + (v?.priceDelta ?? 0)) * it.quantity;
  }, 0), [items, products]);
  const delivery = subtotal > 0 && subtotal < (tenant?.minOrder ?? 0) ? 29 : 0;
  const total = subtotal + delivery;

  const [address, setAddress] = useState<Address>({ id: "a1", label: "Home", line1: "", line2: "", city: "New Delhi", pincode: "" });
  const [slot, setSlot] = useState(SLOTS[0]);
  const [payment, setPayment] = useState<Order["payment"]>("upi");
  const [placing, setPlacing] = useState(false);

  if (items.length === 0) {
    return (
      <div className="mx-auto max-w-md px-4 py-20 text-center">
        <h1 className="text-xl font-semibold">Your cart is empty</h1>
        <Button asChild className="mt-4 rounded-full"><Link to="/s/$tenant" params={{ tenant: slug }}>Continue shopping</Link></Button>
      </div>
    );
  }

  const place = async () => {
    if (!address.line1 || !address.pincode) { toast.error("Please add a delivery address"); return; }
    setPlacing(true);
    try {
      let order: Order;

      if (user) {
        // Authenticated: call the real backend
        order = await api.createOrder(slug, {
          deliverySlot:    slot,
          paymentMethod:   payment.toUpperCase(),
          notes:           undefined,
          deliveryAddress: {
            label:   address.label,
            line1:   address.line1,
            line2:   address.line2 ?? "",
            city:    address.city,
            pincode: address.pincode,
          },
        });
        // Invalidate orders query so the list refreshes
        queryClient.invalidateQueries({ queryKey: qk.orders(slug) });
      } else {
        // Guest: build local order (no backend call)
        order = {
          id:         `ord_${Date.now().toString(36).toUpperCase()}`,
          tenantSlug: slug,
          placedAt:   new Date().toISOString(),
          status:     "placed",
          items:      items.map((it) => {
            const p = products.find((x) => x.id === it.productId)!;
            const v = p.variants?.find((x) => x.id === it.variantId);
            return { productId: p.id, name: p.name + (v ? ` (${v.label})` : ""), quantity: it.quantity, price: p.price + (v?.priceDelta ?? 0), imageEmoji: p.imageEmoji, imageBg: p.imageBg };
          }),
          subtotal, discount: 0, delivery, total,
          address, slot, payment,
        };
        addOrder(order);
      }

      clear(slug);
      toast.success("Order placed!");
      navigate({ to: "/s/$tenant/orders/$id", params: { tenant: slug, id: order.id } });
    } catch (err: unknown) {
      const msg = (err as { message?: string })?.message ?? "Failed to place order. Please try again.";
      toast.error(msg);
    } finally {
      setPlacing(false);
    }
  };

  return (
    <PageTransition>
      <div className="mx-auto max-w-5xl px-4 py-6 md:px-6 md:py-10">
        <Link to="/s/$tenant/cart" params={{ tenant: slug }} className="inline-flex items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground">
          <ArrowLeft className="h-4 w-4" /> Back to cart
        </Link>
        <h1 className="mt-3 text-2xl font-semibold tracking-tight md:text-3xl">Checkout</h1>

        <div className="mt-8 grid gap-6 lg:grid-cols-[1fr_360px]">
          <div className="space-y-5">
            {/* Address */}
            <Card title="Delivery address">
              <div className="grid gap-3 md:grid-cols-2">
                <Field label="Address line 1" required>
                  <Input value={address.line1} onChange={(e) => setAddress({ ...address, line1: e.target.value })} placeholder="House no, street" />
                </Field>
                <Field label="Address line 2">
                  <Input value={address.line2} onChange={(e) => setAddress({ ...address, line2: e.target.value })} placeholder="Landmark" />
                </Field>
                <Field label="City"><Input value={address.city} onChange={(e) => setAddress({ ...address, city: e.target.value })} /></Field>
                <Field label="Pincode" required>
                  <Input value={address.pincode} onChange={(e) => setAddress({ ...address, pincode: e.target.value })} placeholder="110001" />
                </Field>
              </div>
            </Card>

            {/* Slot */}
            <Card title="Delivery slot">
              <div className="grid gap-2 md:grid-cols-2">
                {SLOTS.map((s) => (
                  <button
                    key={s}
                    onClick={() => setSlot(s)}
                    className={cn(
                      "flex items-center justify-between rounded-2xl border px-4 py-3 text-left text-sm transition-colors",
                      slot === s ? "border-primary bg-primary-soft" : "border-border bg-surface hover:border-primary/40",
                    )}
                  >
                    <span>{s}</span>
                    {slot === s && <Check className="h-4 w-4 text-primary" />}
                  </button>
                ))}
              </div>
            </Card>

            {/* Payment */}
            <Card title="Payment method">
              <div className="grid gap-2 md:grid-cols-3">
                {([
                  { id: "upi", label: "UPI", icon: Wallet, sub: "Pay via any UPI app" },
                  { id: "card", label: "Card", icon: CreditCard, sub: "Credit / debit card" },
                  { id: "cod", label: "Cash on delivery", icon: Banknote, sub: "Pay when it arrives" },
                ] as const).map((opt) => (
                  <button
                    key={opt.id}
                    onClick={() => setPayment(opt.id)}
                    className={cn(
                      "rounded-2xl border p-4 text-left transition-colors",
                      payment === opt.id ? "border-primary bg-primary-soft" : "border-border bg-surface hover:border-primary/40",
                    )}
                  >
                    <opt.icon className="h-5 w-5 text-primary" />
                    <div className="mt-2 text-sm font-semibold">{opt.label}</div>
                    <div className="text-[11.5px] text-muted-foreground">{opt.sub}</div>
                  </button>
                ))}
              </div>
            </Card>
          </div>

          {/* Summary */}
          <aside className="lg:sticky lg:top-24 lg:self-start">
            <div className="rounded-3xl border border-border/60 bg-card p-5 shadow-card">
              <h2 className="text-base font-semibold">{tenant?.name}</h2>
              <p className="text-[12px] text-muted-foreground">{items.length} items</p>
              <ul className="mt-4 space-y-2 text-sm">
                {items.map((it) => {
                  const p = products.find((x) => x.id === it.productId);
                  if (!p) return null;
                  return (
                    <li key={`${it.productId}-${it.variantId ?? ""}`} className="flex items-center justify-between">
                      <span className="line-clamp-1 text-muted-foreground">{p.name} × {it.quantity}</span>
                      <span>{formatCurrency(p.price * it.quantity)}</span>
                    </li>
                  );
                })}
              </ul>
              <div className="mt-4 space-y-2 border-t border-border pt-4 text-sm">
                <div className="flex justify-between text-muted-foreground"><span>Subtotal</span><span className="text-foreground">{formatCurrency(subtotal)}</span></div>
                <div className="flex justify-between text-muted-foreground"><span>Delivery</span><span className="text-foreground">{delivery ? formatCurrency(delivery) : "Free"}</span></div>
                <div className="flex justify-between border-t border-border pt-3 text-base font-semibold"><span>Total</span><span>{formatCurrency(total)}</span></div>
              </div>
              <Button onClick={place} disabled={placing} className="mt-5 h-12 w-full rounded-full text-sm font-semibold shadow-pop">
                {placing ? "Placing order…" : `Place order · ${formatCurrency(total)}`}
              </Button>
              {!user && <p className="mt-3 text-center text-[11px] text-muted-foreground">Guest checkout · <Link to="/auth/login" className="text-primary hover:underline">sign in</Link></p>}
            </div>
          </aside>
        </div>
      </div>
    </PageTransition>
  );
}

function Card({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section className="rounded-3xl border border-border/60 bg-card p-5 shadow-card">
      <h2 className="mb-4 text-base font-semibold">{title}</h2>
      {children}
    </section>
  );
}
function Field({ label, required, children }: { label: string; required?: boolean; children: React.ReactNode }) {
  return (
    <div>
      <Label className="mb-1.5 block text-[12.5px] font-medium">{label}{required && <span className="text-destructive"> *</span>}</Label>
      {children}
    </div>
  );
}
