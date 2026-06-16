import { createFileRoute } from "@tanstack/react-router";
import { useMemo, useState } from "react";
import { Download, Search } from "lucide-react";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { useTenantOrders } from "@/hooks/use-tenant-orders";
import { formatCurrency, formatDateTime } from "@/lib/format";
import { downloadCsv, toCsv } from "@/lib/csv";

export const Route = createFileRoute("/s/$tenant/admin/customers")({
  head: ({ params }) => ({ meta: [{ title: `Customers · ${params.tenant}` }] }),
  component: CustomersPage,
});

interface Customer {
  key: string;
  name: string;
  email?: string;
  phone?: string;
  orders: number;
  ltv: number;
  lastOrder: string;
}

function CustomersPage() {
  const { tenant: slug } = Route.useParams();
  const orders = useTenantOrders(slug);
  const [q, setQ] = useState("");

  const customers = useMemo<Customer[]>(() => {
    const map = new Map<string, Customer>();
    orders.forEach((o) => {
      const key = o.customer?.email ?? `${o.customer?.name ?? "Guest"}-${o.address.pincode}`;
      const cur = map.get(key) ?? {
        key,
        name: o.customer?.name ?? "Guest",
        email: o.customer?.email,
        phone: o.customer?.phone,
        orders: 0,
        ltv: 0,
        lastOrder: o.placedAt,
      };
      cur.orders += 1;
      cur.ltv += o.total;
      if (+new Date(o.placedAt) > +new Date(cur.lastOrder)) cur.lastOrder = o.placedAt;
      map.set(key, cur);
    });
    return Array.from(map.values()).sort((a, b) => b.ltv - a.ltv);
  }, [orders]);

  const filtered = q
    ? customers.filter((c) => c.name.toLowerCase().includes(q.toLowerCase()) || c.email?.toLowerCase().includes(q.toLowerCase()))
    : customers;

  return (
    <div className="mx-auto max-w-7xl px-4 py-6 md:px-6 md:py-8">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight md:text-3xl">Customers</h1>
          <p className="mt-1 text-sm text-muted-foreground">{customers.length} customers · {formatCurrency(customers.reduce((s, c) => s + c.ltv, 0))} total LTV</p>
        </div>
        <Button variant="outline" size="sm" className="rounded-full" onClick={() => downloadCsv(`customers-${slug}.csv`, toCsv(filtered as unknown as Record<string, unknown>[]))}>
          <Download className="h-3.5 w-3.5" /> Export
        </Button>
      </div>

      <div className="relative mt-5 w-full md:max-w-sm">
        <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
        <Input value={q} onChange={(e) => setQ(e.target.value)} placeholder="Search customers…" className="pl-9" />
      </div>

      <div className="mt-5 overflow-hidden rounded-2xl border border-border/60 bg-card shadow-soft">
        <table className="w-full text-[13px]">
          <thead>
            <tr className="border-b border-border bg-surface-muted/60 text-left text-[11.5px] font-medium uppercase tracking-wide text-muted-foreground">
              <th className="px-4 py-3">Customer</th>
              <th className="px-4 py-3 hidden md:table-cell">Contact</th>
              <th className="px-4 py-3 text-right">Orders</th>
              <th className="px-4 py-3 text-right">LTV</th>
              <th className="px-4 py-3 hidden md:table-cell">Last order</th>
            </tr>
          </thead>
          <tbody>
            {filtered.map((c) => (
              <tr key={c.key} className="border-b border-border last:border-0 hover:bg-surface-muted/40">
                <td className="px-4 py-3">
                  <div className="flex items-center gap-3">
                    <span className="grid h-9 w-9 place-items-center rounded-full bg-primary-soft text-[12px] font-semibold text-accent-foreground">
                      {c.name.charAt(0).toUpperCase()}
                    </span>
                    <div>
                      <div className="font-medium">{c.name}</div>
                      <div className="md:hidden text-[11px] text-muted-foreground">{c.email ?? "—"}</div>
                    </div>
                  </div>
                </td>
                <td className="px-4 py-3 hidden md:table-cell text-muted-foreground">
                  <div>{c.email ?? "—"}</div>
                  <div className="text-[11px]">{c.phone ?? ""}</div>
                </td>
                <td className="px-4 py-3 text-right tabular-nums">{c.orders}</td>
                <td className="px-4 py-3 text-right font-semibold tabular-nums">{formatCurrency(c.ltv)}</td>
                <td className="px-4 py-3 hidden md:table-cell text-muted-foreground">{formatDateTime(c.lastOrder)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
