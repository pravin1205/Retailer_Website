import { createFileRoute } from "@tanstack/react-router";
import { useEffect } from "react";
import { usePlatformNotifications, useNotifStore } from "@/stores/notification";
import { NotificationList } from "@/components/notifications/NotificationList";

export const Route = createFileRoute("/admin/notifications")({
  head: () => ({ meta: [{ title: "Notifications · Platform admin" }] }),
  component: PlatformNotifications,
});

function PlatformNotifications() {
  const { list, unread } = usePlatformNotifications();
  const markRead = useNotifStore((s) => s.markAllRead);

  useEffect(() => {
    markRead("platform");
  }, [markRead]);

  return (
    <div className="mx-auto max-w-3xl px-4 py-6 md:px-6 md:py-8">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight md:text-3xl">Platform notifications</h1>
        <p className="mt-1 text-sm text-muted-foreground">
          {list.length} events · {unread} new
        </p>
      </div>
      <div className="mt-6">
        <NotificationList items={list} />
      </div>
    </div>
  );
}
