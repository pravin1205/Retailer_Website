import { Link } from "@tanstack/react-router";
import { memo } from "react";
import type { Notification } from "@/stores/notification";

function timeAgo(iso: string): string {
  const diff = Date.now() - +new Date(iso);
  const m = Math.round(diff / 60_000);
  if (m < 1) return "just now";
  if (m < 60) return `${m}m ago`;
  const h = Math.round(m / 60);
  if (h < 24) return `${h}h ago`;
  return `${Math.round(h / 24)}d ago`;
}

function Row({ n }: { n: Notification }) {
  const body = (
    <span className="flex items-start gap-3 rounded-2xl border border-border/60 bg-card p-4 shadow-soft transition hover:shadow-pop">
      <span className="grid h-10 w-10 shrink-0 place-items-center rounded-xl bg-primary-soft text-lg">{n.emoji}</span>
      <span className="min-w-0 flex-1">
        <span className="block text-[13px] font-semibold">{n.title}</span>
        {n.body && <span className="block text-[12px] text-muted-foreground">{n.body}</span>}
        <span className="mt-1 block text-[11px] text-muted-foreground/80">{timeAgo(n.at)}</span>
      </span>
    </span>
  );
  return n.href ? (
    <Link to={n.href.to} params={n.href.params as never} className="block">{body}</Link>
  ) : (
    <span className="block">{body}</span>
  );
}

const MemoRow = memo(Row);

export function NotificationList({ items }: { items: Notification[] }) {
  if (items.length === 0) {
    return (
      <div className="rounded-2xl border border-dashed border-border p-10 text-center text-sm text-muted-foreground">
        Nothing here yet — activity will land in real-time.
      </div>
    );
  }
  return (
    <ul className="space-y-2.5">
      {items.map((n) => (
        <li key={n.id}>
          <MemoRow n={n} />
        </li>
      ))}
    </ul>
  );
}
