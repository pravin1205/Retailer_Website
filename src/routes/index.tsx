import { createFileRoute, Link } from "@tanstack/react-router";
import { useSuspenseQuery } from "@tanstack/react-query";
import { ArrowRight, Clock, ShieldCheck, Sparkles, Store } from "lucide-react";
import { api, qk } from "@/lib/api";
import { StoreCard } from "@/components/storefront/StoreCard";
import { PageTransition, FadeIn } from "@/components/motion/PageTransition";

export const Route = createFileRoute("/")({
  head: () => ({
    meta: [
      { title: "Marketly — Shop your neighborhood, delivered" },
      { name: "description", content: "Marketly is a multi-tenant retail platform: grocery, organic, electronics, bakery and pharmacy stores in one place." },
      { property: "og:title", content: "Marketly — Shop your neighborhood, delivered" },
      { property: "og:description", content: "Multi-tenant retail platform. Local stores, premium experience." },
    ],
  }),
  loader: ({ context }) => {
    context.queryClient.ensureQueryData({ queryKey: qk.tenants, queryFn: api.listTenants });
  },
  component: HomePage,
});

function HomePage() {
  const { data: tenants } = useSuspenseQuery({ queryKey: qk.tenants, queryFn: api.listTenants });
  const featured = tenants.filter((t) => t.featured);
  const others = tenants.filter((t) => !t.featured);

  return (
    <PageTransition>
      <header className="sticky top-0 z-30 border-b border-border/60 bg-background/85 backdrop-blur-xl">
        <div className="mx-auto flex max-w-7xl items-center justify-between px-4 py-3.5 md:px-6">
          <Link to="/" className="flex items-center gap-2">
            <span className="grid h-9 w-9 place-items-center rounded-2xl bg-primary text-primary-foreground font-bold">M</span>
            <span className="text-base font-semibold tracking-tight">Marketly</span>
          </Link>
          <nav className="hidden items-center gap-7 text-[13.5px] text-muted-foreground md:flex">
            <Link to="/stores" className="hover:text-foreground">Stores</Link>
            <a href="#features" className="hover:text-foreground">How it works</a>
            <a href="#sellers" className="hover:text-foreground">For sellers</a>
          </nav>
          <Link
            to="/stores"
            className="inline-flex items-center gap-1.5 rounded-full bg-foreground px-4 py-2 text-[13px] font-medium text-background hover:opacity-90"
          >
            Browse stores
            <ArrowRight className="h-3.5 w-3.5" />
          </Link>
        </div>
      </header>

      <main>
        {/* Hero */}
        <section className="relative overflow-hidden">
          <div className="pointer-events-none absolute inset-0 -z-10">
            <div className="absolute -left-32 top-10 h-80 w-80 rounded-full bg-primary-soft blur-3xl" />
            <div className="absolute -right-32 top-40 h-96 w-96 rounded-full opacity-60 blur-3xl" style={{ background: "oklch(0.95 0.05 60)" }} />
          </div>
          <div className="mx-auto max-w-7xl px-4 pb-12 pt-12 md:px-6 md:pb-20 md:pt-20">
            <div className="grid items-center gap-10 md:grid-cols-2">
              <div>
                <span className="inline-flex items-center gap-1.5 rounded-full border border-border bg-surface px-3 py-1 text-[11.5px] font-medium text-muted-foreground">
                  <Sparkles className="h-3 w-3 text-primary" /> One platform, every neighborhood store
                </span>
                <h1 className="mt-5 text-4xl font-semibold leading-[1.05] tracking-tight md:text-6xl">
                  Your favorite shops,<br />
                  <span className="text-primary">delivered in minutes.</span>
                </h1>
                <p className="mt-5 max-w-md text-[15px] leading-relaxed text-muted-foreground md:text-base">
                  Marketly connects you to grocery, organic, electronics, bakery and pharmacy stores — each with their own
                  brand, prices and delivery promise. Shop them all from one premium app.
                </p>
                <div className="mt-7 flex flex-wrap items-center gap-3">
                  <Link
                    to="/stores"
                    className="inline-flex items-center gap-2 rounded-full bg-primary px-6 py-3 text-sm font-semibold text-primary-foreground shadow-pop hover:opacity-90"
                  >
                    Start shopping
                    <ArrowRight className="h-4 w-4" />
                  </Link>
                  <Link
                    to="/s/$tenant"
                    params={{ tenant: "freshmart" }}
                    className="inline-flex items-center gap-2 rounded-full border border-border bg-surface px-6 py-3 text-sm font-semibold text-foreground hover:bg-surface-muted"
                  >
                    Try FreshMart demo
                  </Link>
                </div>
                <div className="mt-8 grid max-w-md grid-cols-3 gap-3 text-[12px] text-muted-foreground">
                  {[{ icon: Clock, text: "10-min delivery" }, { icon: Store, text: "5+ verified stores" }, { icon: ShieldCheck, text: "Safe payments" }].map((s) => (
                    <div key={s.text} className="rounded-2xl border border-border/60 bg-surface px-3 py-2.5">
                      <s.icon className="mb-1 h-4 w-4 text-primary" />
                      <div className="font-medium text-foreground">{s.text}</div>
                    </div>
                  ))}
                </div>
              </div>

              {/* Visual collage */}
              <div className="relative hidden md:block">
                <div className="relative h-[460px]">
                  {featured.map((t, i) => (
                    <Link
                      key={t.slug}
                      to="/s/$tenant"
                      params={{ tenant: t.slug }}
                      className="absolute overflow-hidden rounded-3xl border border-border/60 bg-card shadow-pop transition-transform hover:-translate-y-1"
                      style={{
                        width: 240,
                        height: 280,
                        top: [10, 120, 40][i],
                        left: [0, 180, 360][i],
                        transform: `rotate(${[-3, 1.5, -1][i]}deg)`,
                      }}
                    >
                      <div className="grid h-2/3 place-items-center text-6xl" style={{ background: t.bannerGradient }}>
                        {t.logoEmoji}
                      </div>
                      <div className="p-3">
                        <div className="text-sm font-semibold">{t.name}</div>
                        <div className="text-[11.5px] text-muted-foreground">{t.category} · {t.deliveryMinutes} min</div>
                      </div>
                    </Link>
                  ))}
                </div>
              </div>
            </div>
          </div>
        </section>

        {/* Featured */}
        <section className="mx-auto max-w-7xl px-4 pb-10 md:px-6 md:pb-16">
          <FadeIn>
            <div className="mb-6 flex items-end justify-between">
              <div>
                <h2 className="text-2xl font-semibold tracking-tight md:text-3xl">Featured stores</h2>
                <p className="mt-1 text-sm text-muted-foreground">Hand-picked storefronts on Marketly.</p>
              </div>
              <Link to="/stores" className="hidden text-sm font-medium text-primary hover:underline md:inline">View all →</Link>
            </div>
          </FadeIn>
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {featured.map((t) => (
              <FadeIn key={t.slug}>
                <StoreCard tenant={t} />
              </FadeIn>
            ))}
          </div>
        </section>

        {/* More */}
        <section className="mx-auto max-w-7xl px-4 pb-16 md:px-6 md:pb-24">
          <FadeIn>
            <h2 className="mb-6 text-xl font-semibold tracking-tight md:text-2xl">More stores in your area</h2>
          </FadeIn>
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {others.map((t) => (
              <FadeIn key={t.slug}>
                <StoreCard tenant={t} />
              </FadeIn>
            ))}
          </div>
        </section>

        {/* For sellers */}
        <section id="sellers" className="border-t border-border/60 bg-surface-muted/60">
          <div className="mx-auto grid max-w-7xl items-center gap-10 px-4 py-16 md:grid-cols-2 md:px-6 md:py-24">
            <div>
              <span className="text-[11.5px] font-semibold uppercase tracking-wider text-primary">For store owners</span>
              <h2 className="mt-3 text-3xl font-semibold tracking-tight md:text-4xl">Run your store on Marketly.</h2>
              <p className="mt-4 max-w-md text-[15px] leading-relaxed text-muted-foreground">
                Bring your inventory, branding and customers. We handle the storefront, search, checkout and logistics —
                so you can focus on what you sell best.
              </p>
              <a
                href="#"
                className="mt-6 inline-flex items-center gap-2 rounded-full border border-border bg-background px-5 py-2.5 text-sm font-semibold hover:bg-surface"
              >
                Become a partner <ArrowRight className="h-4 w-4" />
              </a>
            </div>
            <div className="grid grid-cols-2 gap-3">
              {["Inventory & catalog", "Live orders", "Loyalty & offers", "Sales analytics"].map((f, i) => (
                <div key={f} className="rounded-2xl border border-border/60 bg-card p-5 shadow-soft" style={{ marginTop: i % 2 === 0 ? 0 : 24 }}>
                  <div className="grid h-9 w-9 place-items-center rounded-xl bg-primary-soft text-primary">{["📦","⚡","🎁","📈"][i]}</div>
                  <h3 className="mt-3 text-sm font-semibold">{f}</h3>
                  <p className="mt-1 text-[12.5px] text-muted-foreground">Built-in, ready on day one.</p>
                </div>
              ))}
            </div>
          </div>
        </section>

        <footer className="border-t border-border/60 bg-background">
          <div className="mx-auto flex max-w-7xl flex-col items-start justify-between gap-6 px-4 py-10 text-sm text-muted-foreground md:flex-row md:items-center md:px-6">
            <div className="flex items-center gap-2">
              <span className="grid h-8 w-8 place-items-center rounded-xl bg-primary text-primary-foreground font-bold">M</span>
              <span className="font-medium text-foreground">Marketly</span>
              <span>· © 2026</span>
            </div>
            <div className="flex flex-wrap gap-6 text-[13px]">
              <Link to="/stores" className="hover:text-foreground">Stores</Link>
              <Link to="/admin" className="hover:text-foreground">Platform admin</Link>
              <a href="#sellers" className="hover:text-foreground">Sellers</a>
              <a href="#" className="hover:text-foreground">Contact</a>
            </div>
          </div>
        </footer>
      </main>
    </PageTransition>
  );
}
