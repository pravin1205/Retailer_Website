import { createFileRoute, Outlet, useRouterState } from "@tanstack/react-router";
import { useQuery } from "@tanstack/react-query";
import { useEffect } from "react";
import { api, qk } from "@/lib/api";
import { TenantHeader } from "@/components/layout/TenantHeader";

export const Route = createFileRoute("/s/$tenant")({
  loader: async ({ context, params }) => {
    // prefetch tenant — errors are caught so a bad slug shows the error boundary
    // rather than breaking navigation entirely
    await context.queryClient.ensureQueryData({
      queryKey: qk.tenant(params.tenant),
      queryFn: () => api.getTenant(params.tenant),
    }).catch(() => undefined);
    // categories are best-effort — don't block navigation if unavailable
    context.queryClient.ensureQueryData({
      queryKey: qk.categories(params.tenant),
      queryFn: () => api.listCategories(params.tenant),
    }).catch(() => undefined);
  },
  component: TenantLayout,
});

function TenantLayout() {
  const { tenant: slug } = Route.useParams();
  const pathname = useRouterState({ select: (s) => s.location.pathname });
  const isAdmin = pathname.includes("/admin");
  const { data: tenant } = useQuery({
    queryKey: qk.tenant(slug),
    queryFn: () => api.getTenant(slug),
    retry: false,
  });

  useEffect(() => {
    if (tenant) {
      document.documentElement.setAttribute("data-tenant-accent", tenant.accent);
    }
    return () => {
      document.documentElement.removeAttribute("data-tenant-accent");
    };
  }, [tenant]);

  if (!tenant) return null;

  if (isAdmin) {
    return (
      <div className="min-h-dvh bg-surface-muted">
        <Outlet />
      </div>
    );
  }

  return (
    <div className="min-h-dvh bg-background">
      <TenantHeader />
      <main>
        <Outlet />
      </main>
    </div>
  );
}
