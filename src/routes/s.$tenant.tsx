import { createFileRoute, Outlet, useRouterState } from "@tanstack/react-router";
import { useSuspenseQuery } from "@tanstack/react-query";
import { useEffect } from "react";
import { api, qk } from "@/lib/api";
import { TenantHeader } from "@/components/layout/TenantHeader";

export const Route = createFileRoute("/s/$tenant")({
  loader: async ({ context, params }) => {
    await context.queryClient.ensureQueryData({
      queryKey: qk.tenant(params.tenant),
      queryFn: () => api.getTenant(params.tenant),
    });
    context.queryClient.ensureQueryData({
      queryKey: qk.categories(params.tenant),
      queryFn: () => api.listCategories(params.tenant),
    });
  },
  component: TenantLayout,
});

function TenantLayout() {
  const { tenant: slug } = Route.useParams();
  const pathname = useRouterState({ select: (s) => s.location.pathname });
  const isAdmin = pathname.includes("/admin");
  const { data: tenant } = useSuspenseQuery({
    queryKey: qk.tenant(slug),
    queryFn: () => api.getTenant(slug),
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
