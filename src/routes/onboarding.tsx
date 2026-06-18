import { createFileRoute, Outlet, Link } from "@tanstack/react-router";

export const Route = createFileRoute("/onboarding")({
  component: OnboardingLayout,
});

function OnboardingLayout() {
  return (
    <div className="min-h-dvh bg-surface-muted/40">
      {/* Minimal header — matches auth pages */}
      <header className="border-b border-border/60 bg-background/90 backdrop-blur-sm">
        <div className="mx-auto flex max-w-3xl items-center justify-between px-4 py-3.5 md:px-6">
          <Link to="/" className="flex items-center gap-2">
            <span className="grid h-8 w-8 place-items-center rounded-xl bg-primary text-primary-foreground text-sm font-bold">
              M
            </span>
            <span className="text-sm font-semibold tracking-tight">Marketly</span>
          </Link>
          <Link to="/stores" className="text-[12.5px] text-muted-foreground hover:text-foreground">
            Browse stores →
          </Link>
        </div>
      </header>

      <main className="mx-auto max-w-3xl px-4 py-8 md:px-6 md:py-12">
        <Outlet />
      </main>
    </div>
  );
}
