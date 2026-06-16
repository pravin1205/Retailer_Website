import { createFileRoute, Link } from "@tanstack/react-router";
import { useSuspenseQuery } from "@tanstack/react-query";
import { Star, StarOff, ExternalLink, Settings2 } from "lucide-react";
import { toast } from "sonner";
import { api, qk } from "@/lib/api";
import { useAdminStore } from "@/stores/admin";
import type { TenantAccent } from "@/lib/types";

export const Route = createFileRoute("/admin/tenants")({
  head: () => ({ meta: [{ title: "Tenants · Platform admin" }] }),
  loader: ({ context }) => {
    context.queryClient.ensureQueryData({ queryKey: qk.tenants, queryFn: api.listTenants });
  },
  component: TenantsPage,
});

const ACCENTS: { key: TenantAccent; color: string }[] = [
  { key: "emerald", color: "oklch(0.62 0.14 158)" },
  { key: "orange", color: "oklch(0.68 0.17 50)" },
  { key: "purple", color: "oklch(0.55 0.18 290)" },
  { key: "rose", color: "oklch(0.62 0.18 15)" },
];

function TenantsPage() {
  const { data: tenants } = useSuspenseQuery({ queryKey: qk.tenants, queryFn: api.listTenants });
  const patchTenant = useAdminStore((s) => s.patchTenant);

  return (
    <div className="mx-auto max-w-7xl px-4 py-6 md:px-6 md:py-8">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight md:text-3xl">Tenants</h1>
        <p className="mt-1 text-sm text-muted-foreground">{tenants.length} active storefronts on Marketly</p>
      </div>

      <div className="mt-6 grid gap-3">
        {tenants.map((t) => (
          <div key={t.slug} className="rounded-3xl border border-border/60 bg-card p-4 shadow-soft md:p-5">
            <div className="flex flex-wrap items-center gap-4">
              <span className="grid h-14 w-14 shrink-0 place-items-center rounded-2xl text-2xl" style={{ background: t.bannerGradient }}>
                {t.logoEmoji}
              </span>
              <div className="min-w-0 flex-1">
                <div className="flex flex-wrap items-center gap-2">
                  <h3 className="text-base font-semibold">{t.name}</h3>
                  {t.featured && (
                    <span className="rounded-full bg-primary-soft px-2 py-0.5 text-[10.5px] font-semibold text-accent-foreground">
                      Featured
                    </span>
                  )}
                  <span className="rounded-full border border-border bg-surface px-2 py-0.5 text-[10.5px] text-muted-foreground">
                    {t.category}
                  </span>
                </div>
                <p className="mt-0.5 text-[12.5px] text-muted-foreground">{t.tagline}</p>
                <div className="mt-1.5 flex flex-wrap items-center gap-3 text-[11px] text-muted-foreground">
                  <span>⭐ {t.rating} ({t.reviewCount.toLocaleString("en-IN")})</span>
                  <span>· {t.deliveryMinutes} min</span>
                  <span>· min ₹{t.minOrder}</span>
                </div>
              </div>

              <div className="flex flex-wrap items-center gap-2">
                <div className="flex items-center gap-1 rounded-full border border-border bg-surface p-1">
                  {ACCENTS.map((a) => (
                    <button
                      key={a.key}
                      title={a.key}
                      onClick={() => {
                        patchTenant(t.slug, { accent: a.key });
                        toast.success(`${t.name} accent set to ${a.key}`);
                      }}
                      className={
                        "h-5 w-5 rounded-full ring-2 ring-offset-1 transition " +
                        (t.accent === a.key ? "ring-foreground" : "ring-transparent")
                      }
                      style={{ background: a.color }}
                    />
                  ))}
                </div>
                <button
                  onClick={() => {
                    patchTenant(t.slug, { featured: !t.featured });
                    toast.success(t.featured ? `${t.name} unfeatured` : `${t.name} featured`);
                  }}
                  className="inline-flex h-9 items-center gap-1.5 rounded-full border border-border bg-surface px-3 text-[12px] font-medium hover:bg-surface-muted"
                >
                  {t.featured ? <StarOff className="h-3.5 w-3.5" /> : <Star className="h-3.5 w-3.5" />}
                  {t.featured ? "Unfeature" : "Feature"}
                </button>
                <Link
                  to="/s/$tenant/admin"
                  params={{ tenant: t.slug }}
                  className="inline-flex h-9 items-center gap-1.5 rounded-full bg-foreground px-3 text-[12px] font-semibold text-background hover:opacity-90"
                >
                  <Settings2 className="h-3.5 w-3.5" /> Manage
                </Link>
                <Link
                  to="/s/$tenant"
                  params={{ tenant: t.slug }}
                  className="inline-flex h-9 items-center gap-1.5 rounded-full border border-border bg-surface px-3 text-[12px] font-medium hover:bg-surface-muted"
                >
                  <ExternalLink className="h-3.5 w-3.5" /> View
                </Link>
              </div>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
