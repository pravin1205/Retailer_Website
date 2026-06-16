import { createFileRoute, Outlet, Link } from "@tanstack/react-router";
import { useSuspenseQuery } from "@tanstack/react-query";
import { useState } from "react";
import { ShieldCheck, Store } from "lucide-react";
import { api, qk } from "@/lib/api";
import { useAuthStore } from "@/stores";
import { SidebarProvider, SidebarInset } from "@/components/ui/sidebar";
import { AdminSidebar } from "@/components/admin/AdminSidebar";
import { AdminTopbar } from "@/components/admin/AdminTopbar";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { toast } from "sonner";

export const Route = createFileRoute("/s/$tenant/admin")({
  head: ({ params }) => ({ meta: [{ title: `Dashboard · ${params.tenant}` }] }),
  loader: ({ context, params }) =>
    context.queryClient.ensureQueryData({
      queryKey: qk.tenant(params.tenant),
      queryFn: () => api.getTenant(params.tenant),
    }),
  component: AdminLayout,
});

function AdminLayout() {
  const { tenant: slug } = Route.useParams();
  const { data: tenant } = useSuspenseQuery({ queryKey: qk.tenant(slug), queryFn: () => api.getTenant(slug) });
  const user = useAuthStore((s) => s.user);

  if (!tenant) return null;

  if (user?.role !== "owner") {
    return <OwnerSignInWall tenantName={tenant.name} />;
  }

  return (
    <SidebarProvider>
      <AdminSidebar />
      <SidebarInset className="bg-surface-muted">
        <AdminTopbar />
        <div className="flex-1">
          <Outlet />
        </div>
      </SidebarInset>
    </SidebarProvider>
  );
}

function OwnerSignInWall({ tenantName }: { tenantName: string }) {
  const { tenant: slug } = Route.useParams();
  const loginAs = useAuthStore((s) => s.loginAs);
  const [email, setEmail] = useState("owner@marketly.in");

  return (
    <div className="grid min-h-dvh place-items-center px-4 py-10">
      <div className="w-full max-w-md rounded-3xl border border-border/60 bg-card p-7 shadow-card">
        <div className="grid h-12 w-12 place-items-center rounded-2xl bg-primary-soft text-primary">
          <ShieldCheck className="h-5 w-5" />
        </div>
        <h1 className="mt-5 text-xl font-semibold tracking-tight">Owner sign in</h1>
        <p className="mt-1 text-sm text-muted-foreground">
          Manage <span className="font-medium text-foreground">{tenantName}</span> — orders, inventory, customers and more.
        </p>
        <form
          className="mt-5 space-y-3"
          onSubmit={(e) => {
            e.preventDefault();
            loginAs(email, "owner");
            toast.success("Signed in as store owner");
          }}
        >
          <div>
            <Label htmlFor="oe" className="mb-1.5 block text-[12.5px] font-medium">Email</Label>
            <Input id="oe" type="email" value={email} onChange={(e) => setEmail(e.target.value)} />
          </div>
          <Button type="submit" className="h-11 w-full rounded-full text-sm font-semibold">
            Enter dashboard
          </Button>
        </form>
        <div className="mt-5 flex items-center justify-between text-[11.5px] text-muted-foreground">
          <Link to="/s/$tenant" params={{ tenant: slug }} className="inline-flex items-center gap-1 hover:text-foreground">
            <Store className="h-3.5 w-3.5" /> Back to storefront
          </Link>
          <Link to="/auth/login" className="text-primary hover:underline">Customer login</Link>
        </div>
        <p className="mt-4 rounded-xl bg-surface-muted px-3 py-2 text-[11px] text-muted-foreground">
          Demo: any email works. Owner role is mocked on the client.
        </p>
      </div>
    </div>
  );
}
