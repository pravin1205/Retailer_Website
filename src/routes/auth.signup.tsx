import { createFileRoute, Link, useNavigate } from "@tanstack/react-router";
import { useState } from "react";
import { useAuthStore } from "@/stores";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { PageTransition } from "@/components/motion/PageTransition";
import { toast } from "sonner";

export const Route = createFileRoute("/auth/signup")({
  head: () => ({ meta: [{ title: "Sign up — Marketly" }] }),
  component: SignupPage,
});

function SignupPage() {
  const navigate   = useNavigate();
  const register   = useAuthStore((s) => s.register);
  const loading    = useAuthStore((s) => s.loading);
  const storeError = useAuthStore((s) => s.error);
  const clearError = useAuthStore((s) => s.clearError);

  const [name,     setName]     = useState("");
  const [email,    setEmail]    = useState("");
  const [password, setPassword] = useState("");
  const [error,    setError]    = useState<string | null>(null);

  const displayError = error ?? storeError;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    clearError();
    if (password.length < 8) {
      setError("Password must be at least 8 characters.");
      return;
    }
    try {
      await register(name, email, password);
      toast.success(`Welcome, ${name || email.split("@")[0]}!`);
      navigate({ to: "/" });
    } catch (err: unknown) {
      const msg = (err as { message?: string })?.message ?? "Registration failed.";
      setError(msg);
    }
  };

  return (
    <PageTransition>
      <div className="grid min-h-dvh place-items-center px-4 py-10">
        <div className="w-full max-w-sm rounded-3xl border border-border/60 bg-card p-7 shadow-card">
          <Link to="/" className="grid h-11 w-11 place-items-center rounded-2xl bg-primary text-primary-foreground text-lg font-bold">M</Link>
          <h1 className="mt-5 text-2xl font-semibold tracking-tight">Create your account</h1>
          <p className="mt-1 text-sm text-muted-foreground">Join Marketly in seconds</p>

          {displayError && (
            <div className="mt-4 rounded-2xl border border-destructive/30 bg-destructive/10 px-4 py-3 text-sm text-destructive">
              {displayError}
            </div>
          )}

          <form className="mt-6 space-y-3" onSubmit={handleSubmit}>
            <div>
              <Label htmlFor="name" className="mb-1.5 block text-[12.5px] font-medium">Name</Label>
              <Input
                id="name"
                required
                autoComplete="name"
                value={name}
                onChange={(e) => setName(e.target.value)}
                placeholder="Your name"
              />
            </div>
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
                minLength={8}
                autoComplete="new-password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="At least 8 characters"
              />
            </div>
            <Button
              type="submit"
              disabled={loading}
              className="h-11 w-full rounded-full text-sm font-semibold"
            >
              {loading ? "Creating account…" : "Create account"}
            </Button>
          </form>

          <p className="mt-5 text-center text-[12.5px] text-muted-foreground">
            Already a member?{" "}
            <Link to="/auth/login" className="font-medium text-primary hover:underline">Sign in</Link>
          </p>
        </div>
      </div>
    </PageTransition>
  );
}
