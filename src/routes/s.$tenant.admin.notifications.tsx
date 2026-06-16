import { createFileRoute } from "@tanstack/react-router";
import { useEffect } from "react";
import { useTenantNotifications, useNotifStore } from "@/stores/notifications";
import { NotificationList } from "@/components/notifications/NotificationList";

export const Route = createFileRoute("/s/$tenant/admin/notifications")({
  head: ({ params }) => ({ meta: [{ title: `Notifications · ${params.tenant}` }] }),
  component: TenantNotifications,
});

function TenantNotifications() {
  const { tenant: slug } = Route.useParams();
  const { list, unread } = useTenantNotifications(slug);
  const markRead = useNotifStore((s) => s.markAllRead);

  useEffect(() => {
    markRead(slug);
  }, [markRead, slug]);

  return (
    <div className="mx-auto max-w-3xl px-4 py-6 md:px-6 md:py-8">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight md:text-3xl">Notifications</h1>
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
