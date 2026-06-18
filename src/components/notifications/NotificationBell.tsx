import { Link } from "@tanstack/react-router";
import { Bell } from "lucide-react";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import type { Notification } from "@/stores/notification";

interface Props {
  unread: number;
  items: Notification[];
  title?: string;
  onOpen?: () => void;
  allHref?: { to: string; params?: Record<string, string> };
}

export function NotificationBell({ unread, items, title = "Notifications", onOpen, allHref }: Props) {
  return (
    <Popover
      onOpenChange={(open) => {
        if (open) onOpen?.();
      }}
    >
      <PopoverTrigger asChild>
        <button
          className="relative grid h-9 w-9 place-items-center rounded-full text-foreground/70 hover:bg-surface-muted"
          aria-label={`${title}${unread ? ` (${unread} unread)` : ""}`}
        >
          <Bell className="h-4 w-4" />
          {unread > 0 && (
            <span className="absolute right-1 top-1 grid h-4 min-w-4 place-items-center rounded-full bg-primary px-1 text-[9.5px] font-bold leading-none text-primary-foreground">
              {unread > 9 ? "9+" : unread}
            </span>
          )}
        </button>
      </PopoverTrigger>
      <PopoverContent align="end" className="w-80 p-0">
        <div className="flex items-center justify-between border-b border-border px-4 py-3">
          <div>
            <div className="text-sm font-semibold">{title}</div>
            <div className="text-[11px] text-muted-foreground">{unread > 0 ? `${unread} new` : "All caught up"}</div>
          </div>
        </div>
        <ul className="max-h-80 divide-y divide-border overflow-auto">
          {items.length === 0 && (
            <li className="px-4 py-8 text-center text-[12.5px] text-muted-foreground">No notifications</li>
          )}
          {items.map((n) => {
            const body = (
              <span className="flex items-start gap-3 px-4 py-3 hover:bg-surface-muted">
                <span className="grid h-8 w-8 shrink-0 place-items-center rounded-lg bg-primary-soft text-[14px]">
                  {n.emoji}
                </span>
                <span className="min-w-0 flex-1">
                  <span className="block truncate text-[12.5px] font-medium">{n.title}</span>
                  {n.body && <span className="block truncate text-[11px] text-muted-foreground">{n.body}</span>}
                  <span className="mt-0.5 block text-[10.5px] text-muted-foreground/80">
                    {timeAgo(n.at)}
                  </span>
                </span>
              </span>
            );
            return (
              <li key={n.id}>
                {n.href ? (
                  <Link to={n.href.to} params={n.href.params as never}>{body}</Link>
                ) : (
                  body
                )}
              </li>
            );
          })}
        </ul>
        {allHref && items.length > 0 && (
          <div className="border-t border-border p-2">
            <Link
              to={allHref.to}
              params={allHref.params as never}
              className="block rounded-lg px-3 py-2 text-center text-[12px] font-medium text-primary hover:bg-surface-muted"
            >
              View all
            </Link>
          </div>
        )}
      </PopoverContent>
    </Popover>
  );
}

function timeAgo(iso: string): string {
  const diff = Date.now() - +new Date(iso);
  const m = Math.round(diff / 60_000);
  if (m < 1) return "just now";
  if (m < 60) return `${m}m ago`;
  const h = Math.round(m / 60);
  if (h < 24) return `${h}h ago`;
  const d = Math.round(h / 24);
  return `${d}d ago`;
}
