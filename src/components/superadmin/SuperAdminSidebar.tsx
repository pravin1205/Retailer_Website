import { Link, useRouterState } from "@tanstack/react-router";
import { LayoutDashboard, Building2, ShoppingBag, BarChart3, Bell, ArrowLeft } from "lucide-react";
import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarGroup,
  SidebarGroupContent,
  SidebarGroupLabel,
  SidebarHeader,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  useSidebar,
} from "@/components/ui/sidebar";

const items = [
  { title: "Overview", to: "/admin", icon: LayoutDashboard, exact: true },
  { title: "Tenants", to: "/admin/tenants", icon: Building2 },
  { title: "Orders", to: "/admin/orders", icon: ShoppingBag },
  { title: "Analytics", to: "/admin/analytics", icon: BarChart3 },
  { title: "Notifications", to: "/admin/notifications", icon: Bell },
] as const;

export function SuperAdminSidebar() {
  const pathname = useRouterState({ select: (s) => s.location.pathname });
  const { state } = useSidebar();
  const collapsed = state === "collapsed";

  const isActive = (href: string, exact?: boolean) =>
    exact ? pathname === href : pathname === href || pathname.startsWith(href + "/");

  return (
    <Sidebar collapsible="icon" className="border-r border-border/60">
      <SidebarHeader className="border-b border-border/60 px-3 py-3">
        <Link to="/admin" className="flex items-center gap-2.5 rounded-xl px-1.5 py-1">
          <div className="grid h-9 w-9 shrink-0 place-items-center rounded-xl bg-foreground text-background font-bold">
            M
          </div>
          {!collapsed && (
            <div className="min-w-0">
              <div className="truncate text-[13.5px] font-semibold leading-tight">Marketly</div>
              <div className="text-[10.5px] text-muted-foreground">Platform admin</div>
            </div>
          )}
        </Link>
      </SidebarHeader>

      <SidebarContent>
        <SidebarGroup>
          {!collapsed && <SidebarGroupLabel>Platform</SidebarGroupLabel>}
          <SidebarGroupContent>
            <SidebarMenu>
              {items.map((it) => (
                <SidebarMenuItem key={it.title}>
                  <SidebarMenuButton asChild isActive={isActive(it.to, "exact" in it ? it.exact : false)} tooltip={it.title}>
                    <Link to={it.to} className="flex items-center gap-2.5">
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
            <SidebarMenuButton asChild tooltip="Marketplace home">
              <Link to="/" className="flex items-center gap-2.5">
                <ArrowLeft className="h-4 w-4" />
                <span>Marketplace</span>
              </Link>
            </SidebarMenuButton>
          </SidebarMenuItem>
        </SidebarMenu>
      </SidebarFooter>
    </Sidebar>
  );
}
