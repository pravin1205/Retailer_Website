import { createFileRoute } from "@tanstack/react-router";
import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Download, Search, RefreshCw, Users } from "lucide-react";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { api } from "@/lib/api";
import { formatCurrency, formatDateTime } from "@/lib/format";
import { downloadCsv, toCsv } from "@/lib/csv";

export const Route = createFileRoute("/s/$tenant/admin/customers")({
  head: ({ params }) => ({ meta: [{ title: `Customers · ${params.tenant}` }] }),
  component: CustomersPage,
});

function CustomersPage() {
  const { tenant: slug } = Route.useParams();
  const [q, setQ] = useState("");

  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ["admin-customers", slug, q],
    queryFn: () => api.listCustomers(slug, { search: q || undefined, size: 100 }),
    retry: 1,
  });

  const customers = data?.items ?? [];

  const tierBadge = (tier: string) => {
    const map: Record<string, string> = {
      BRONZE:   "bg-amber-100 text-amber-800",
      SILVER:   "bg-slate-100 text-slate-700",
      GOLD:     "bg-yellow-100 text-yellow-800",
      PLATINUM: "bg-purple-100 text-purple-800",
    };
    return map[tier] ?? "bg-surface-muted text-muted-foreground";
  };

  const displayName = (c: typeof customers[0]) => {
    const full = [c.firstName, c.lastName].filter(Boolean).join(" ");
    return full || c.phone || c.email || "Guest";
  };

  const handleExport = () => {
    downloadCsv(
      `customers-${slug}.csv`,
      toCsv(
        customers.map((c) => ({
          name:          displayName(c),
          email:         c.email ?? "",
          phone:         c.phone ?? "",
          tier:          c.tier,
          loyaltyPoints: c.loyaltyPoints,
          totalOrders:   c.totalOrders,
          totalSpent:    c.totalSpent,
          joinedAt:      c.createdAt,
        })) as unknown as Record<string, unknown>[],
      ),
    );
  };

  return (
    <div className="mx-auto max-w-7xl px-4 py-6 md:px-6 md:py-8">
      {/* Header */}
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight md:text-3xl">Customers</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            {isLoading ? "Loading…" : `${customers.length} customer${customers.length !== 1 ? "s" : ""}`}
            {data && data.totalElements > customers.length ? ` (showing ${customers.length} of ${data.totalElements})` : ""}
          </p>
        </div>
        <div className="flex gap-2">
          <Button variant="outline" size="sm" className="rounded-full" onClick={() => refetch()}>
            <RefreshCw className={`h-3.5 w-3.5 ${isLoading ? "animate-spin" : ""}`} />
          </Button>
          <Button variant="outline" size="sm" className="rounded-full" onClick={handleExport} disabled={customers.length === 0}>
            <Download className="h-3.5 w-3.5" /> Export
          </Button>
        </div>
      </div>

      {/* Search */}
      <div className="relative mt-5 w-full md:max-w-sm">
        <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
        <Input
          value={q}
          onChange={(e) => setQ(e.target.value)}
          placeholder="Search by name, email or phone…"
          className="pl-9"
        />
      </div>

      {/* Error */}
      {isError && (
        <div className="mt-5 rounded-2xl border border-destructive/30 bg-destructive/10 px-4 py-3 text-sm text-destructive">
          Failed to load customers. Make sure you are signed in as the store owner.
          <button onClick={() => refetch()} className="ml-2 underline">Retry</button>
        </div>
      )}

      {/* Empty state */}
      {!isLoading && !isError && customers.length === 0 && (
        <div className="mt-16 flex flex-col items-center text-center">
          <div className="grid h-16 w-16 place-items-center rounded-3xl bg-surface-muted text-3xl">
            <Users className="h-8 w-8 text-muted-foreground" />
          </div>
          <h2 className="mt-4 text-base font-semibold">No customers yet</h2>
          <p className="mt-1 text-sm text-muted-foreground">
            Customers will appear here once they register through your store URL.
          </p>
        </div>
      )}

      {/* Table */}
      {customers.length > 0 && (
        <div className="mt-5 overflow-hidden rounded-2xl border border-border/60 bg-card shadow-soft">
          <table className="w-full text-[13px]">
            <thead>
              <tr className="border-b border-border bg-surface-muted/60 text-left text-[11.5px] font-medium uppercase tracking-wide text-muted-foreground">
                <th className="px-4 py-3">Customer</th>
                <th className="px-4 py-3 hidden md:table-cell">Contact</th>
                <th className="px-4 py-3 hidden sm:table-cell">Tier</th>
                <th className="px-4 py-3 text-right">Points</th>
                <th className="px-4 py-3 text-right hidden md:table-cell">Orders</th>
                <th className="px-4 py-3 text-right">Spent</th>
                <th className="px-4 py-3 hidden lg:table-cell">Joined</th>
              </tr>
            </thead>
            <tbody>
              {customers.map((c) => {
                const name = displayName(c);
                return (
                  <tr key={c.id} className="border-b border-border last:border-0 hover:bg-surface-muted/40">
                    {/* Name */}
                    <td className="px-4 py-3">
                      <div className="flex items-center gap-3">
                        <span className="grid h-9 w-9 shrink-0 place-items-center rounded-full bg-primary-soft text-[12px] font-semibold text-accent-foreground">
                          {name.charAt(0).toUpperCase()}
                        </span>
                        <div>
                          <div className="font-medium">{name}</div>
                          <div className="md:hidden text-[11px] text-muted-foreground">{c.phone ?? c.email ?? "—"}</div>
                        </div>
                      </div>
                    </td>
                    {/* Contact */}
                    <td className="px-4 py-3 hidden md:table-cell text-muted-foreground">
                      {c.email && !(c.email.endsWith("@otp.marketly.internal")) && (
                        <div>{c.email}</div>
                      )}
                      <div className="text-[11px]">{c.phone ?? ""}</div>
                    </td>
                    {/* Tier */}
                    <td className="px-4 py-3 hidden sm:table-cell">
                      <span className={`rounded-full px-2 py-0.5 text-[11px] font-medium ${tierBadge(c.tier)}`}>
                        {c.tier}
                      </span>
                    </td>
                    {/* Points */}
                    <td className="px-4 py-3 text-right tabular-nums">{c.loyaltyPoints.toLocaleString()}</td>
                    {/* Orders */}
                    <td className="px-4 py-3 text-right tabular-nums hidden md:table-cell">{c.totalOrders}</td>
                    {/* Spent */}
                    <td className="px-4 py-3 text-right font-semibold tabular-nums">{formatCurrency(c.totalSpent)}</td>
                    {/* Joined */}
                    <td className="px-4 py-3 hidden lg:table-cell text-muted-foreground">
                      {c.createdAt ? formatDateTime(c.createdAt) : "—"}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
