import { Link, useParams, useRouterState } from "@tanstack/react-router";
import {
  LayoutDashboard,
  ShoppingBag,
  Boxes,
  FolderTree,
  Users,
  Ticket,
  BarChart3,
  Bell,
  Settings,
  Store,
  ArrowLeft,
} from "lucide-react";
import {
  Sidebar,
  SidebarContent,
  SidebarGroup,
  SidebarGroupContent,
  SidebarGroupLabel,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarHeader,
  SidebarFooter,
  useSidebar,
} from "@/components/ui/sidebar";
import { useMergedTenant } from "@/stores/admin";

const items = [
  { title: "Overview", to: "/s/$tenant/admin", icon: LayoutDashboard, exact: true },
  { title: "Orders", to: "/s/$tenant/admin/orders", icon: ShoppingBag },
  { title: "Products", to: "/s/$tenant/admin/products", icon: Boxes },
  { title: "Categories", to: "/s/$tenant/admin/categories", icon: FolderTree },
  { title: "Customers", to: "/s/$tenant/admin/customers", icon: Users },
  { title: "Coupons", to: "/s/$tenant/admin/coupons", icon: Ticket },
  { title: "Reports", to: "/s/$tenant/admin/reports", icon: BarChart3 },
  { title: "Notifications", to: "/s/$tenant/admin/notifications", icon: Bell },
  { title: "Settings", to: "/s/$tenant/admin/settings", icon: Settings },
] as const;

export function AdminSidebar() {
  const { tenant: slug } = useParams({ strict: false }) as { tenant: string };
  const pathname = useRouterState({ select: (s) => s.location.pathname });
  const { state } = useSidebar();
  const collapsed = state === "collapsed";
  const tenant = useMergedTenant(slug);

  const isActive = (href: string, exact?: boolean) => {
    const resolved = href.replace("$tenant", slug);
    return exact ? pathname === resolved : pathname === resolved || pathname.startsWith(resolved + "/");
  };

  return (
    <Sidebar collapsible="icon" className="border-r border-border/60">
      <SidebarHeader className="border-b border-border/60 px-3 py-3">
        <Link
          to="/s/$tenant/admin"
          params={{ tenant: slug }}
          className="flex items-center gap-2.5 rounded-xl px-1.5 py-1"
        >
          <div
            className="grid h-9 w-9 shrink-0 place-items-center rounded-xl text-lg"
            style={{ background: tenant?.bannerGradient }}
            aria-hidden
          >
            {tenant?.logoEmoji ?? "🏪"}
          </div>
          {!collapsed && (
            <div className="min-w-0">
              <div className="truncate text-[13.5px] font-semibold leading-tight">
                {tenant?.name ?? "Store"}
              </div>
              <div className="text-[10.5px] text-muted-foreground">Owner dashboard</div>
            </div>
          )}
        </Link>
      </SidebarHeader>

      <SidebarContent>
        <SidebarGroup>
          {!collapsed && <SidebarGroupLabel>Manage</SidebarGroupLabel>}
          <SidebarGroupContent>
            <SidebarMenu>
              {items.map((it) => (
                <SidebarMenuItem key={it.title}>
                  <SidebarMenuButton asChild isActive={isActive(it.to, "exact" in it ? it.exact : false)} tooltip={it.title}>
                    <Link to={it.to} params={{ tenant: slug }} className="flex items-center gap-2.5">
                      <it.icon className="h-4 w-4" />
                      <span>{it.title}</span>
                    </Link>
                  </SidebarMenuButton>
                </SidebarMenuItem>
              ))}
            </SidebarMenu>
          </SidebarGroupContent>
        </SidebarGroup>
      </SidebarContent>

      <SidebarFooter className="border-t border-border/60 p-2">
        <SidebarMenu>
          <SidebarMenuItem>
            <SidebarMenuButton asChild tooltip="View storefront">
              <Link to="/s/$tenant" params={{ tenant: slug }} className="flex items-center gap-2.5">
                <Store className="h-4 w-4" />
                <span>View storefront</span>
              </Link>
            </SidebarMenuButton>
          </SidebarMenuItem>
          <SidebarMenuItem>
            <SidebarMenuButton asChild tooltip="All stores">
              <Link to="/stores" className="flex items-center gap-2.5">
                <ArrowLeft className="h-4 w-4" />
                <span>All stores</span>
              </Link>
            </SidebarMenuButton>
          </SidebarMenuItem>
        </SidebarMenu>
      </SidebarFooter>
    </Sidebar>
  );
}
