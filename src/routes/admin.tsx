import { createFileRoute, Outlet, Link } from "@tanstack/react-router";
import { useState } from "react";
import { ShieldCheck, Home } from "lucide-react";
import { useAuthStore } from "@/stores";
import { SidebarProvider, SidebarInset } from "@/components/ui/sidebar";
import { SuperAdminSidebar } from "@/components/superadmin/SuperAdminSidebar";
import { SuperAdminTopbar } from "@/components/superadmin/SuperAdminTopbar";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { toast } from "sonner";

export const Route = createFileRoute("/admin")({
  head: () => ({ meta: [{ title: "Platform admin · Marketly" }] }),
  component: SuperAdminLayout,
});

function SuperAdminLayout() {
  const user = useAuthStore((s) => s.user);

  if (user?.role !== "super_admin") {
    return <SuperAdminSignInWall />;
  }

  return (
    <SidebarProvider>
      <SuperAdminSidebar />
      <SidebarInset className="bg-surface-muted">
        <SuperAdminTopbar />
        <div className="flex-1">
          <Outlet />
        </div>
      </SidebarInset>
    </SidebarProvider>
  );
}

function SuperAdminSignInWall() {
  const loginAs = useAuthStore((s) => s.loginAs);
  const [email, setEmail] = useState("admin@marketly.in");

  return (
    <div className="grid min-h-dvh place-items-center px-4 py-10">
      <div className="w-full max-w-md rounded-3xl border border-border/60 bg-card p-7 shadow-card">
        <div className="grid h-12 w-12 place-items-center rounded-2xl bg-foreground text-background">
          <ShieldCheck className="h-5 w-5" />
        </div>
        <h1 className="mt-5 text-xl font-semibold tracking-tight">Platform admin</h1>
        <p className="mt-1 text-sm text-muted-foreground">
          Monitor every storefront, every order, every tenant on Marketly.
        </p>
        <form
          className="mt-5 space-y-3"
          onSubmit={(e) => {
            e.preventDefault();
            loginAs(email, "super_admin");
            toast.success("Signed in as platform admin");
          }}
        >
          <div>
            <Label htmlFor="se" className="mb-1.5 block text-[12.5px] font-medium">Email</Label>
            <Input id="se" type="email" value={email} onChange={(e) => setEmail(e.target.value)} />
          </div>
          <Button type="submit" className="h-11 w-full rounded-full bg-foreground text-sm font-semibold text-background hover:opacity-90">
            Enter platform admin
          </Button>
        </form>
        <Link to="/" className="mt-5 inline-flex items-center gap-1 text-[11.5px] text-muted-foreground hover:text-foreground">
          <Home className="h-3.5 w-3.5" /> Back to marketplace
        </Link>
        <p className="mt-4 rounded-xl bg-surface-muted px-3 py-2 text-[11px] text-muted-foreground">
          Demo: any email works. Role is mocked client-side.
        </p>
      </div>
    </div>
  );
}
