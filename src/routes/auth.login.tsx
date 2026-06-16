import { createFileRoute, Link, useNavigate } from "@tanstack/react-router";
import { useState } from "react";
import { useAuthStore } from "@/stores";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { PageTransition } from "@/components/motion/PageTransition";
import { toast } from "sonner";

export const Route = createFileRoute("/auth/login")({
  head: () => ({ meta: [{ title: "Sign in — Marketly" }] }),
  component: LoginPage,
});

function LoginPage() {
  const navigate   = useNavigate();
  const login      = useAuthStore((s) => s.login);
  const loading    = useAuthStore((s) => s.loading);
  const storeError = useAuthStore((s) => s.error);
  const clearError = useAuthStore((s) => s.clearError);

  const [email,    setEmail]    = useState("");
  const [password, setPassword] = useState("");
  const [error,    setError]    = useState<string | null>(null);

  const displayError = error ?? storeError;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    clearError();
    try {
      await login(email, password);
      toast.success("Signed in");
      navigate({ to: "/" });
    } catch (err: unknown) {
      const msg = (err as { message?: string })?.message ?? "Invalid email or password.";
      setError(msg);
    }
  };

  return (
    <PageTransition>
      <div className="grid min-h-dvh place-items-center px-4 py-10">
        <div className="w-full max-w-sm rounded-3xl border border-border/60 bg-card p-7 shadow-card">
          <Link to="/" className="grid h-11 w-11 place-items-center rounded-2xl bg-primary text-primary-foreground text-lg font-bold">M</Link>
          <h1 className="mt-5 text-2xl font-semibold tracking-tight">Welcome back</h1>
          <p className="mt-1 text-sm text-muted-foreground">Sign in to your Marketly account</p>

          {displayError && (
            <div className="mt-4 rounded-2xl border border-destructive/30 bg-destructive/10 px-4 py-3 text-sm text-destructive">
              {displayError}
            </div>
          )}

          <form className="mt-6 space-y-3" onSubmit={handleSubmit}>
            <div>
              <Label htmlFor="email" className="mb-1.5 block text-[12.5px] font-medium">Email</Label>
              <Input
                id="email"
                type="email"
                required
                autoComplete="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="you@email.com"
              />
            </div>
            <div>
              <Label htmlFor="password" className="mb-1.5 block text-[12.5px] font-medium">Password</Label>
              <Input
                id="password"
                type="password"
                required
                autoComplete="current-password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="••••••••"
              />
            </div>
            <Button
              type="submit"
              disabled={loading}
              className="h-11 w-full rounded-full text-sm font-semibold"
            >
              {loading ? "Signing in…" : "Sign in"}
            </Button>
          </form>

          <p className="mt-5 text-center text-[12.5px] text-muted-foreground">
            New to Marketly?{" "}
            <Link to="/auth/signup" className="font-medium text-primary hover:underline">Create an account</Link>
          </p>
        </div>
      </div>
    </PageTransition>
  );
}
