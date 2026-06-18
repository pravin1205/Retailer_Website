import { createFileRoute, Link } from "@tanstack/react-router";
import { Bell, CreditCard, Gift, MapPin, User, LogOut, ChevronRight } from "lucide-react";
import { useAuthStore } from "@/stores";
import { PageTransition } from "@/components/motion/PageTransition";
import { Button } from "@/components/ui/button";

export const Route = createFileRoute("/s/$tenant/account")({
  head: ({ params }) => ({ meta: [{ title: `Account · ${params.tenant}` }] }),
  component: AccountPage,
});

function AccountPage() {
  const { tenant: slug } = Route.useParams();
  const user = useAuthStore((s) => s.user);
  const logout = useAuthStore((s) => s.logout);

  return (
    <PageTransition>
      <div className="mx-auto max-w-3xl px-4 py-6 md:px-6 md:py-10">
        <div className="rounded-3xl border border-border/60 bg-card p-5 shadow-card md:p-6">
          {user ? (
            <div className="flex items-center gap-4">
              <div className="grid h-14 w-14 place-items-center rounded-2xl bg-primary-soft text-2xl font-semibold text-accent-foreground">
                {user.name.charAt(0).toUpperCase()}
              </div>
              <div className="min-w-0 flex-1">
                <div className="truncate text-lg font-semibold">{user.name}</div>
                <div className="truncate text-sm text-muted-foreground">{user.email}</div>
              </div>
              <Button variant="ghost" size="sm" onClick={logout}><LogOut className="h-4 w-4" /> Sign out</Button>
            </div>
          ) : (
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div>
                <h2 className="text-lg font-semibold">Welcome to Marketly</h2>
                <p className="text-sm text-muted-foreground">Sign in to save addresses, track orders & earn loyalty points.</p>
              </div>
              <div className="flex gap-2">
                <Button asChild className="rounded-full">
                  <Link to="/onboarding/customer" search={{ tenant: slug }}>Join with mobile</Link>
                </Button>
                <Button asChild variant="outline" className="rounded-full">
                  <Link to="/auth/login">Sign in</Link>
                </Button>
              </div>
            </div>
          )}
        </div>

        {/* Loyalty */}
        <div className="mt-5 rounded-3xl border border-border/60 p-5 shadow-card" style={{ background: "linear-gradient(135deg, var(--color-primary-soft), oklch(0.96 0.05 60))" }}>
          <div className="flex items-center gap-3">
            <Gift className="h-6 w-6 text-primary" />
            <div>
              <div className="text-sm font-semibold">Marketly Rewards</div>
              <div className="text-[12.5px] text-muted-foreground">You've earned <strong className="text-foreground">240</strong> points · ₹120 off your next order</div>
            </div>
          </div>
        </div>

        {/* Sections */}
        <ul className="mt-5 divide-y divide-border overflow-hidden rounded-3xl border border-border/60 bg-card shadow-soft">
          {[
            { icon: MapPin, label: "Saved addresses", sub: "Add home, office and more" },
            { icon: CreditCard, label: "Payment methods", sub: "UPI, cards, wallets" },
            { icon: Bell, label: "Notifications", sub: "Order updates & offers" },
            { icon: User, label: "Profile details", sub: "Name, email, phone" },
          ].map((item) => (
            <li key={item.label}>
              <button className="flex w-full items-center gap-3 px-5 py-4 text-left hover:bg-surface-muted/60">
                <span className="grid h-10 w-10 place-items-center rounded-xl bg-surface-muted text-primary"><item.icon className="h-4 w-4" /></span>
                <span className="flex-1">
                  <span className="block text-sm font-medium">{item.label}</span>
                  <span className="block text-[12px] text-muted-foreground">{item.sub}</span>
                </span>
                <ChevronRight className="h-4 w-4 text-muted-foreground" />
              </button>
            </li>
          ))}
        </ul>

        <div className="mt-5 text-center text-[11.5px] text-muted-foreground">
          Shopping at <Link to="/s/$tenant" params={{ tenant: slug }} className="text-primary hover:underline">this store</Link> · <Link to="/stores" className="text-primary hover:underline">browse all stores</Link>
        </div>
      </div>
    </PageTransition>
  );
}
