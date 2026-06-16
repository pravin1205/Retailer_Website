import { create } from "zustand";
import { persist } from "zustand/middleware";
import { useShallow } from "zustand/shallow";
import { useMemo } from "react";
import type { Product, Category, Coupon, Tenant, Order } from "@/lib/types";
import { products as seedProducts, categories as seedCategories } from "@/lib/mock/products";
import { coupons as seedCoupons } from "@/lib/mock/reviews";
import { tenants as seedTenants } from "@/lib/mock/tenants";
import { seedOrders } from "@/lib/mock/seedOrders";

interface AdminState {
  productOverrides: Record<string, Product>;
  deletedProducts: Record<string, true>;
  newProducts: Product[];

  categoryOverrides: Record<string, Category>;
  deletedCategories: Record<string, true>;
  newCategories: Category[];

  couponOverrides: Record<string, Coupon>;
  deletedCoupons: Record<string, true>;
  newCoupons: Coupon[];

  tenantOverrides: Record<string, Partial<Tenant>>;

  historicalOrders: Order[];
  orderStatusOverrides: Record<string, Order["status"]>;

  upsertProduct: (p: Product) => void;
  deleteProduct: (id: string) => void;

  upsertCategory: (c: Category) => void;
  deleteCategory: (id: string) => void;

  upsertCoupon: (c: Coupon) => void;
  deleteCoupon: (code: string) => void;

  patchTenant: (slug: string, patch: Partial<Tenant>) => void;

  updateOrderStatus: (id: string, status: Order["status"]) => void;
}

export const useAdminStore = create<AdminState>()(
  persist(
    (set) => ({
      productOverrides: {},
      deletedProducts: {},
      newProducts: [],
      categoryOverrides: {},
      deletedCategories: {},
      newCategories: [],
      couponOverrides: {},
      deletedCoupons: {},
      newCoupons: [],
      tenantOverrides: {},
      historicalOrders: seedOrders,
      orderStatusOverrides: {},
      upsertProduct: (p) =>
        set((s) => {
          if (seedProducts.find((x) => x.id === p.id)) {
            return { productOverrides: { ...s.productOverrides, [p.id]: p } };
          }
          if (s.newProducts.find((x) => x.id === p.id)) {
            return { newProducts: s.newProducts.map((x) => (x.id === p.id ? p : x)) };
          }
          return { newProducts: [p, ...s.newProducts] };
        }),
      deleteProduct: (id) =>
        set((s) =>
          seedProducts.find((x) => x.id === id)
            ? { deletedProducts: { ...s.deletedProducts, [id]: true } }
            : { newProducts: s.newProducts.filter((x) => x.id !== id) },
        ),
      upsertCategory: (c) =>
        set((s) => {
          if (seedCategories.find((x) => x.id === c.id))
            return { categoryOverrides: { ...s.categoryOverrides, [c.id]: c } };
          if (s.newCategories.find((x) => x.id === c.id))
            return { newCategories: s.newCategories.map((x) => (x.id === c.id ? c : x)) };
          return { newCategories: [c, ...s.newCategories] };
        }),
      deleteCategory: (id) =>
        set((s) =>
          seedCategories.find((x) => x.id === id)
            ? { deletedCategories: { ...s.deletedCategories, [id]: true } }
            : { newCategories: s.newCategories.filter((x) => x.id !== id) },
        ),
      upsertCoupon: (c) =>
        set((s) => {
          if (seedCoupons.find((x) => x.code === c.code))
            return { couponOverrides: { ...s.couponOverrides, [c.code]: c } };
          if (s.newCoupons.find((x) => x.code === c.code))
            return { newCoupons: s.newCoupons.map((x) => (x.code === c.code ? c : x)) };
          return { newCoupons: [c, ...s.newCoupons] };
        }),
      deleteCoupon: (code) =>
        set((s) =>
          seedCoupons.find((x) => x.code === code)
            ? { deletedCoupons: { ...s.deletedCoupons, [code]: true } }
            : { newCoupons: s.newCoupons.filter((x) => x.code !== code) },
        ),
      patchTenant: (slug, patch) =>
        set((s) => ({
          tenantOverrides: { ...s.tenantOverrides, [slug]: { ...(s.tenantOverrides[slug] ?? {}), ...patch } },
        })),
      updateOrderStatus: (id, status) =>
        set((s) =>
          s.historicalOrders.find((o) => o.id === id)
            ? { historicalOrders: s.historicalOrders.map((o) => (o.id === id ? { ...o, status } : o)) }
            : { orderStatusOverrides: { ...s.orderStatusOverrides, [id]: status } },
        ),
    }),
    { name: "marketly-admin", version: 1 },
  ),
);

// ---------- Non-reactive getters (for use inside api/mock layer) ----------

export function getMergedProducts(tenantSlug: string): Product[] {
  const s = useAdminStore.getState();
  return seedProducts
    .filter((p) => p.tenantSlug === tenantSlug && !s.deletedProducts[p.id])
    .map((p) => s.productOverrides[p.id] ?? p)
    .concat(s.newProducts.filter((p) => p.tenantSlug === tenantSlug));
}

export function getMergedProduct(id: string): Product | undefined {
  const s = useAdminStore.getState();
  if (s.deletedProducts[id]) return undefined;
  return (
    s.productOverrides[id] ??
    seedProducts.find((p) => p.id === id) ??
    s.newProducts.find((p) => p.id === id)
  );
}

export function getMergedCategories(tenantSlug: string): Category[] {
  const s = useAdminStore.getState();
  return seedCategories
    .filter((c) => c.tenantSlug === tenantSlug && !s.deletedCategories[c.id])
    .map((c) => s.categoryOverrides[c.id] ?? c)
    .concat(s.newCategories.filter((c) => c.tenantSlug === tenantSlug));
}

export function getMergedCoupons(): Coupon[] {
  const s = useAdminStore.getState();
  return seedCoupons
    .filter((c) => !s.deletedCoupons[c.code])
    .map((c) => s.couponOverrides[c.code] ?? c)
    .concat(s.newCoupons);
}

export function getMergedTenant(slug: string): Tenant | undefined {
  const s = useAdminStore.getState();
  const base = seedTenants.find((t) => t.slug === slug);
  if (!base) return undefined;
  return { ...base, ...(s.tenantOverrides[slug] ?? {}) };
}

// ---------- Reactive hooks ----------

export function useTenantProducts(tenantSlug: string): Product[] {
  const { overrides, deleted, newOnes } = useAdminStore(
    useShallow((s) => ({
      overrides: s.productOverrides,
      deleted: s.deletedProducts,
      newOnes: s.newProducts,
    })),
  );
  return useMemo(
    () =>
      seedProducts
        .filter((p) => p.tenantSlug === tenantSlug && !deleted[p.id])
        .map((p) => overrides[p.id] ?? p)
        .concat(newOnes.filter((p) => p.tenantSlug === tenantSlug)),
    [tenantSlug, overrides, deleted, newOnes],
  );
}

export function useTenantCategories(tenantSlug: string): Category[] {
  const { overrides, deleted, newOnes } = useAdminStore(
    useShallow((s) => ({
      overrides: s.categoryOverrides,
      deleted: s.deletedCategories,
      newOnes: s.newCategories,
    })),
  );
  return useMemo(
    () =>
      seedCategories
        .filter((c) => c.tenantSlug === tenantSlug && !deleted[c.id])
        .map((c) => overrides[c.id] ?? c)
        .concat(newOnes.filter((c) => c.tenantSlug === tenantSlug)),
    [tenantSlug, overrides, deleted, newOnes],
  );
}

export function useAllCoupons(): Coupon[] {
  const { overrides, deleted, newOnes } = useAdminStore(
    useShallow((s) => ({
      overrides: s.couponOverrides,
      deleted: s.deletedCoupons,
      newOnes: s.newCoupons,
    })),
  );
  return useMemo(
    () =>
      seedCoupons
        .filter((c) => !deleted[c.code])
        .map((c) => overrides[c.code] ?? c)
        .concat(newOnes),
    [overrides, deleted, newOnes],
  );
}

export function useMergedTenant(slug: string): Tenant | undefined {
  const overrides = useAdminStore((s) => s.tenantOverrides[slug]);
  return useMemo(() => {
    const base = seedTenants.find((t) => t.slug === slug);
    if (!base) return undefined;
    return { ...base, ...(overrides ?? {}) };
  }, [slug, overrides]);
}
