import { useEffect, useState } from "react";
import { Download, X } from "lucide-react";

type BIPEvent = Event & { prompt: () => Promise<void>; userChoice: Promise<{ outcome: string }> };

export function PWAProvider() {
  const [deferred, setDeferred] = useState<BIPEvent | null>(null);
  const [dismissed, setDismissed] = useState(false);

  useEffect(() => {
    if (typeof window === "undefined") return;
    if (import.meta.env.DEV) {
      // In dev, unregister any stale SW so HMR isn't intercepted.
      navigator.serviceWorker?.getRegistrations().then((rs) => rs.forEach((r) => r.unregister())).catch(() => undefined);
      return;
    }
    if (!("serviceWorker" in navigator)) return;
    navigator.serviceWorker.register("/sw.js").catch(() => undefined);
  }, []);

  useEffect(() => {
    const onPrompt = (e: Event) => {
      e.preventDefault();
      setDeferred(e as BIPEvent);
    };
    window.addEventListener("beforeinstallprompt", onPrompt);
    return () => window.removeEventListener("beforeinstallprompt", onPrompt);
  }, []);

  if (!deferred || dismissed) return null;

  return (
    <div className="fixed inset-x-3 bottom-20 z-50 mx-auto max-w-sm rounded-2xl border border-border/60 bg-card p-3 shadow-pop md:bottom-4 md:left-auto md:right-4">
      <div className="flex items-start gap-3">
        <div className="grid h-10 w-10 place-items-center rounded-xl bg-primary text-primary-foreground">
          <Download className="h-4 w-4" />
        </div>
        <div className="min-w-0 flex-1">
          <div className="text-[13px] font-semibold">Install Marketly</div>
          <div className="text-[11.5px] text-muted-foreground">Add to home screen for one-tap access and offline carts.</div>
          <div className="mt-2 flex gap-2">
            <button
              onClick={async () => {
                await deferred.prompt();
                await deferred.userChoice.catch(() => undefined);
                setDeferred(null);
              }}
              className="inline-flex h-8 items-center rounded-full bg-primary px-3 text-[12px] font-semibold text-primary-foreground hover:opacity-90"
            >
              Install
            </button>
            <button
              onClick={() => setDismissed(true)}
              className="inline-flex h-8 items-center rounded-full border border-border bg-surface px-3 text-[12px] font-medium hover:bg-surface-muted"
            >
              Not now
            </button>
          </div>
        </div>
        <button onClick={() => setDismissed(true)} aria-label="Dismiss" className="grid h-7 w-7 place-items-center rounded-full hover:bg-surface-muted">
          <X className="h-3.5 w-3.5" />
        </button>
      </div>
    </div>
  );
}
