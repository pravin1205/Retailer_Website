import { Link, useNavigate, useParams } from "@tanstack/react-router";
import { ExternalLink, LogOut, Search } from "lucide-react";
import { SidebarTrigger } from "@/components/ui/sidebar";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { useAuthStore } from "@/stores";
import { useMergedTenant } from "@/stores/admin";
import { api } from "@/lib/api";
import { useQuery } from "@tanstack/react-query";
import { NotificationBell } from "@/components/notifications/NotificationBell";
import { useTenantNotifications, useNotifStore } from "@/stores/notifications";

export function AdminTopbar() {
  const { tenant: slug } = useParams({ strict: false }) as { tenant: string };
  const navigate = useNavigate();
  const tenant = useMergedTenant(slug);
  const user = useAuthStore((s) => s.user);
  const logout = useAuthStore((s) => s.logout);
  const { list, unread } = useTenantNotifications(slug);
  const markRead = useNotifStore((s) => s.markAllRead);

  const { data: tenants = [] } = useQuery({
    queryKey: ["tenants-switcher"],
    queryFn: () => api.listTenants(),
    staleTime: 5 * 60_000,
  });

  return (
    <header className="sticky top-0 z-30 flex h-14 items-center gap-3 border-b border-border/60 bg-background/95 px-3 backdrop-blur-xl md:px-5">
      <SidebarTrigger className="-ml-1" />

      {/* Tenant switcher */}
      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          <button className="hidden items-center gap-2 rounded-full border border-border bg-surface px-3 py-1.5 text-[12.5px] font-medium hover:bg-surface-muted md:flex">
            <span className="grid h-5 w-5 place-items-center rounded-md text-[11px]" style={{ background: tenant?.bannerGradient }}>
              {tenant?.logoEmoji}
            </span>
            {tenant?.name}
            <span className="text-muted-foreground">▾</span>
          </button>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="start" className="w-64">
          <DropdownMenuLabel>Switch store</DropdownMenuLabel>
          <DropdownMenuSeparator />
          {tenants.map((t) => (
            <DropdownMenuItem
              key={t.slug}
              onClick={() => navigate({ to: "/s/$tenant/admin", params: { tenant: t.slug } })}
            >
              <span className="grid h-6 w-6 place-items-center rounded-md text-[12px]" style={{ background: t.bannerGradient }}>
                {t.logoEmoji}
              </span>
              <span className="flex-1 truncate text-[13px] font-medium">{t.name}</span>
              {t.slug === slug && <span className="text-[10px] text-primary">●</span>}
            </DropdownMenuItem>
          ))}
        </DropdownMenuContent>
      </DropdownMenu>

      {/* Search (placeholder) */}
      <div className="ml-2 hidden flex-1 lg:flex">
        <div className="relative w-full max-w-md">
          <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
          <input
            placeholder="Search orders, products, customers…"
            className="h-9 w-full rounded-full border border-border bg-surface-muted pl-9 pr-3 text-[13px] outline-none transition focus:border-primary/50 focus:bg-surface"
          />
        </div>
      </div>

      <div className="ml-auto flex items-center gap-1.5">
        <Button asChild variant="ghost" size="sm" className="hidden gap-1.5 rounded-full text-[12.5px] md:inline-flex">
          <Link to="/s/$tenant" params={{ tenant: slug }}>
            <ExternalLink className="h-3.5 w-3.5" />
            View store
          </Link>
        </Button>

        <NotificationBell
          unread={unread}
          items={list.slice(0, 6)}
          title={`${tenant?.name ?? "Store"} activity`}
          onOpen={() => markRead(slug)}
          allHref={{ to: "/s/$tenant/admin/notifications", params: { tenant: slug } }}
        />

        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <button className="grid h-9 w-9 place-items-center rounded-full bg-primary-soft text-[12.5px] font-semibold text-accent-foreground hover:opacity-90" aria-label="Account">
              {(user?.name ?? "O").charAt(0).toUpperCase()}
            </button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end" className="w-56">
            <DropdownMenuLabel>
              <div className="text-[12.5px]">{user?.name ?? "Owner"}</div>
              <div className="text-[11px] font-normal text-muted-foreground">{user?.email}</div>
            </DropdownMenuLabel>
            <DropdownMenuSeparator />
            <DropdownMenuItem asChild>
              <Link to="/s/$tenant/admin/settings" params={{ tenant: slug }}>Store settings</Link>
            </DropdownMenuItem>
            <DropdownMenuItem asChild>
              <Link to="/s/$tenant" params={{ tenant: slug }}>View storefront</Link>
            </DropdownMenuItem>
            <DropdownMenuSeparator />
            <DropdownMenuItem
              onClick={() => {
                logout();
                navigate({ to: "/auth/login" });
              }}
            >
              <LogOut className="h-3.5 w-3.5" /> Sign out
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </div>
    </header>
  );
}
