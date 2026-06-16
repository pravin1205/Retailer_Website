import { Link, useParams, useRouterState } from "@tanstack/react-router";
import { Home, Search, ShoppingBag, ClipboardList, User } from "lucide-react";
import { useCartStore } from "@/stores";

export function MobileBottomNav() {
  const { tenant: tenantSlug } = useParams({ strict: false }) as { tenant?: string };
  const pathname = useRouterState({ select: (s) => s.location.pathname });
  const count = useCartStore((s) => (tenantSlug ? s.count(tenantSlug) : 0));

  if (!tenantSlug) return null;
  if (pathname.includes("/admin")) return null;

  const items: Array<{
    to: "/s/$tenant" | "/s/$tenant/search" | "/s/$tenant/cart" | "/s/$tenant/orders" | "/s/$tenant/account";
    label: string;
    icon: typeof Home;
    exact?: boolean;
    badge?: number;
  }> = [
    { to: "/s/$tenant", label: "Home", icon: Home, exact: true },
    { to: "/s/$tenant/search", label: "Search", icon: Search },
    { to: "/s/$tenant/cart", label: "Cart", icon: ShoppingBag, badge: count },
    { to: "/s/$tenant/orders", label: "Orders", icon: ClipboardList },
    { to: "/s/$tenant/account", label: "Account", icon: User },
  ];

  return (
    <nav
      className="fixed inset-x-0 bottom-0 z-40 border-t border-border/60 bg-background/95 pb-[env(safe-area-inset-bottom)] backdrop-blur-xl md:hidden"
      aria-label="Primary"
    >
      <ul className="mx-auto grid max-w-md grid-cols-5">
        {items.map((it) => {
          const href = it.to.replace("$tenant", tenantSlug);
          const active = it.exact ? pathname === href : pathname.startsWith(href);
          const Icon = it.icon;
          return (
            <li key={it.to}>
              <Link
                to={it.to}
                params={{ tenant: tenantSlug }}
                className={
                  "relative flex flex-col items-center gap-0.5 py-2.5 text-[10.5px] font-medium transition-colors " +
                  (active ? "text-primary" : "text-muted-foreground")
                }
                aria-current={active ? "page" : undefined}
              >
                <span className="relative">
                  <Icon className="h-5 w-5" />
                  {it.badge ? (
                    <span className="absolute -right-2 -top-1.5 grid h-4 min-w-4 place-items-center rounded-full bg-primary px-1 text-[10px] font-semibold text-primary-foreground">
                      {it.badge}
                    </span>
                  ) : null}
                </span>
                <span>{it.label}</span>
              </Link>
            </li>
          );
        })}
      </ul>
    </nav>
  );
}
