import { Link, useNavigate } from "@tanstack/react-router";
import { LogOut, Search } from "lucide-react";
import { SidebarTrigger } from "@/components/ui/sidebar";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { useAuthStore } from "@/stores";
import { NotificationBell } from "@/components/notifications/NotificationBell";
import { usePlatformNotifications, useNotifStore } from "@/stores/notification";

export function SuperAdminTopbar() {
  const navigate = useNavigate();
  const user = useAuthStore((s) => s.user);
  const logout = useAuthStore((s) => s.logout);
  const { list, unread } = usePlatformNotifications();
  const markRead = useNotifStore((s) => s.markAllRead);

  return (
    <header className="sticky top-0 z-30 flex h-14 items-center gap-3 border-b border-border/60 bg-background/95 px-3 backdrop-blur-xl md:px-5">
      <SidebarTrigger className="-ml-1" />

      <span className="hidden items-center gap-2 rounded-full border border-border bg-surface px-3 py-1.5 text-[12.5px] font-medium md:flex">
        <span className="grid h-1.5 w-1.5 place-items-center rounded-full bg-success" />
        Platform admin
      </span>

      <div className="ml-2 hidden flex-1 lg:flex">
        <div className="relative w-full max-w-md">
          <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
          <input
            placeholder="Search tenants, orders, customers…"
            className="h-9 w-full rounded-full border border-border bg-surface-muted pl-9 pr-3 text-[13px] outline-none transition focus:border-primary/50 focus:bg-surface"
          />
        </div>
      </div>

      <div className="ml-auto flex items-center gap-1.5">
        <NotificationBell
          unread={unread}
          items={list.slice(0, 6)}
          title="Platform activity"
          onOpen={() => markRead("platform")}
          allHref={{ to: "/admin/notifications" }}
        />

        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <button className="grid h-9 w-9 place-items-center rounded-full bg-foreground text-[12.5px] font-semibold text-background hover:opacity-90" aria-label="Account">
              {(user?.name ?? "A").charAt(0).toUpperCase()}
            </button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end" className="w-56">
            <DropdownMenuLabel>
              <div className="text-[12.5px]">{user?.name ?? "Admin"}</div>
              <div className="text-[11px] font-normal text-muted-foreground">{user?.email}</div>
            </DropdownMenuLabel>
            <DropdownMenuSeparator />
            <DropdownMenuItem asChild>
              <Link to="/">Back to marketplace</Link>
            </DropdownMenuItem>
            <DropdownMenuSeparator />
            <DropdownMenuItem
              onClick={() => {
                logout();
                navigate({ to: "/" });
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
