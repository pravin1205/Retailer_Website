import { createFileRoute, Link } from "@tanstack/react-router";
import { useSuspenseQuery } from "@tanstack/react-query";
import { ArrowLeft } from "lucide-react";
import { api, qk } from "@/lib/api";
import { StoreCard } from "@/components/storefront/StoreCard";
import { PageTransition, FadeIn } from "@/components/motion/PageTransition";

export const Route = createFileRoute("/stores/")({
  head: () => ({
    meta: [
      { title: "All stores — Marketly" },
      { name: "description", content: "Browse every storefront on Marketly: grocery, organic, electronics, bakery and pharmacy." },
    ],
  }),
  loader: ({ context }) => {
    context.queryClient.ensureQueryData({ queryKey: qk.tenants, queryFn: api.listTenants });
  },
  component: StoresPage,
});

function StoresPage() {
  const { data: tenants } = useSuspenseQuery({ queryKey: qk.tenants, queryFn: api.listTenants });

  return (
    <PageTransition>
      <div className="mx-auto max-w-7xl px-4 py-8 md:px-6 md:py-12">
        <Link to="/" className="inline-flex items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground">
          <ArrowLeft className="h-4 w-4" /> Back
        </Link>
        <div className="mt-4 flex flex-wrap items-end justify-between gap-3">
          <div>
            <h1 className="text-3xl font-semibold tracking-tight md:text-4xl">All stores</h1>
            <p className="mt-1 text-sm text-muted-foreground">{tenants.length} storefronts on Marketly</p>
          </div>
          <div className="flex flex-wrap items-center gap-2">
            {tenants[0] && (
              <Link
                to="/s/$tenant/admin"
                params={{ tenant: tenants[0].slug }}
                className="inline-flex items-center gap-1.5 rounded-full border border-border bg-surface px-4 py-2 text-[12.5px] font-semibold hover:bg-surface-muted"
              >
                ✨ Owner dashboard
              </Link>
            )}
            <Link
              to="/admin"
              className="inline-flex items-center gap-1.5 rounded-full bg-foreground px-4 py-2 text-[12.5px] font-semibold text-background shadow-pop hover:opacity-90"
            >
              🛡️ Platform admin
            </Link>
          </div>
        </div>

        <div className="mt-8 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {tenants.map((t) => (
            <FadeIn key={t.slug}>
              <StoreCard tenant={t} />
            </FadeIn>
          ))}
        </div>
      </div>
    </PageTransition>
  );
}
