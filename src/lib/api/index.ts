/**
 * Marketly API client — replaces the old mock implementation.
 *
 * All functions call the real Spring Boot backend via the Vite dev proxy
 * (configured in vite.config.ts to forward /api/v1/* → http://localhost:8080).
 *
 * The http-client axios instance handles:
 *   - Bearer token injection
 *   - 401 → silent token refresh
 *   - Response envelope unwrapping ({ success, data } → data)
 *
 * Query keys (qk) are unchanged so all existing route loaders
 * and useSuspenseQuery calls continue to work without modification.
 */
import { http, withTenant } from "./http-client";
import type { Product, Tenant, Category, Review, Coupon, Order } from "../types";

// ── Tenant resolution helper ───────────────────────────────────────────────────
// The backend identifies tenants by UUID (X-Tenant-ID header), but our routes
// use the tenant slug. We fetch the tenant first to get its ID, then cache it.
const tenantIdCache = new Map<string, string>();

async function resolveTenantId(slug: string): Promise<string> {
  if (tenantIdCache.has(slug)) return tenantIdCache.get(slug)!;
  const tenant = await http.get<{ id: string }>(`/tenants/${slug}`);
  const id = (tenant as unknown as { id: string }).id ?? slug;
  tenantIdCache.set(slug, id);
  return id;
}

// ── Type adapters ─────────────────────────────────────────────────────────────
// The backend returns camelCase fields that differ slightly from the frontend
// types. These mappers bridge that gap without changing any existing component.

function adaptTenant(raw: Record<string, unknown>): Tenant {
  const settings = (raw.settings as Record<string, unknown>) ?? {};
  return {
    slug:            String(raw.slug ?? ""),
    name:            String(raw.name ?? ""),
    tagline:         String(raw.tagline ?? ""),
    description:     String(raw.description ?? ""),
    category:        String(raw.category ?? ""),
    accent:          (raw.accentColor ?? raw.accent ?? "emerald") as Tenant["accent"],
    logoEmoji:       String(raw.logoEmoji ?? raw.logoUrl ?? "🏪"),
    bannerGradient:  String(raw.bannerGradient ?? raw.bannerUrl ?? "linear-gradient(135deg, #f0fdf4, #dcfce7)"),
    rating:          Number(raw.rating ?? 4.5),
    reviewCount:     Number(raw.reviewCount ?? 0),
    deliveryMinutes: Number(settings.deliveryMinutes ?? raw.deliveryMinutes ?? 30),
    deliveryRadiusKm:Number(settings.deliveryRadiusKm ?? raw.deliveryRadiusKm ?? 5),
    minOrder:        Number(settings.minOrderValue ?? raw.minOrder ?? 99),
    hours:           String(settings.hours ?? raw.hours ?? "9 AM – 9 PM"),
    phone:           String(settings.phone ?? raw.phone ?? ""),
    address:         String(settings.address ?? raw.address ?? ""),
    featured:        Boolean(raw.featured),
  };
}

function adaptCategory(raw: Record<string, unknown>, tenantSlug: string): Category {
  return {
    id:         String(raw.id ?? ""),
    tenantSlug,
    name:       String(raw.name ?? ""),
    slug:       String(raw.slug ?? ""),
    emoji:      String(raw.emoji ?? raw.imageUrl ?? "📦"),
  };
}

function adaptProduct(raw: Record<string, unknown>, tenantSlug: string): Product {
  const inv = (raw.inventory as Record<string, unknown>) ?? {};
  return {
    id:           String(raw.id ?? ""),
    tenantSlug,
    categorySlug: String((raw.category as Record<string, unknown>)?.slug ?? raw.categorySlug ?? ""),
    name:         String(raw.name ?? ""),
    brand:        raw.brand ? String(raw.brand) : undefined,
    description:  String(raw.description ?? ""),
    price:        Number(raw.price ?? 0),
    mrp:          Number(raw.mrp ?? raw.price ?? 0),
    unit:         raw.unit ? String(raw.unit) : undefined,
    imageEmoji:   String(raw.imageEmoji ?? "📦"),
    imageBg:      String(raw.imageBg ?? "#f3f4f6"),
    stock:        Number(inv.quantityAvailable ?? inv.quantityOnHand ?? raw.stock ?? 0),
    rating:       Number(raw.rating ?? 4.0),
    reviewCount:  Number(raw.reviewCount ?? 0),
    tags:         Array.isArray(raw.tags) ? (raw.tags as string[]) : undefined,
    variants:     Array.isArray(raw.variants)
      ? (raw.variants as Record<string, unknown>[]).map((v) => ({
          id:         String(v.id ?? ""),
          label:      String(v.label ?? ""),
          priceDelta: Number(v.priceDelta ?? 0),
        }))
      : undefined,
  };
}

// ── API functions ─────────────────────────────────────────────────────────────

export const api = {
  // ── Tenants ────────────────────────────────────────────────────────────────

  async listTenants(): Promise<Tenant[]> {
    try {
      const res = await http.get<{ data: Record<string, unknown>[] }>("/tenants?size=50");
      const items = (res as unknown as { data: Record<string, unknown>[] }).data ?? [];
      return items.map(adaptTenant);
    } catch {
      // Fall back to mock if backend is unavailable
      const { tenants } = await import("../mock/tenants");
      return tenants;
    }
  },

  async getTenant(slug: string): Promise<Tenant | undefined> {
    try {
      const raw = (await http.get(`/tenants/${slug}`)) as unknown as Record<string, unknown>;
      const tenant = adaptTenant(raw);
      tenantIdCache.set(slug, String(raw.id ?? slug));
      return tenant;
    } catch {
      const { tenants } = await import("../mock/tenants");
      return tenants.find((t) => t.slug === slug);
    }
  },

  // ── Categories ─────────────────────────────────────────────────────────────

  async listCategories(tenantSlug: string): Promise<Category[]> {
    try {
      const tenantId = await resolveTenantId(tenantSlug);
      const res = await http.get<Record<string, unknown>[]>("/categories", withTenant(tenantId));
      const items = Array.isArray(res) ? res : [];
      return items.map((r) => adaptCategory(r as Record<string, unknown>, tenantSlug));
    } catch {
      const { categories } = await import("../mock/products");
      return categories.filter((c) => c.tenantSlug === tenantSlug);
    }
  },

  // ── Products ───────────────────────────────────────────────────────────────

  async listProducts(
    tenantSlug: string,
    opts?: { categorySlug?: string; tag?: string; search?: string },
  ): Promise<Product[]> {
    try {
      const tenantId = await resolveTenantId(tenantSlug);
      const params = new URLSearchParams({ size: "100" });
      if (opts?.search)      params.set("search", opts.search);
      if (opts?.categorySlug) params.set("categorySlug", opts.categorySlug);
      if (opts?.tag)          params.set("tag", opts.tag);

      const res = await http.get<{ data: Record<string, unknown>[] }>(
        `/products?${params}`,
        withTenant(tenantId),
      );
      const items = (res as unknown as { data: Record<string, unknown>[] }).data ?? (Array.isArray(res) ? res : []);
      return (items as Record<string, unknown>[]).map((r) => adaptProduct(r, tenantSlug));
    } catch {
      const { products } = await import("../mock/products");
      let list = products.filter((p) => p.tenantSlug === tenantSlug);
      if (opts?.categorySlug) list = list.filter((p) => p.categorySlug === opts.categorySlug);
      if (opts?.tag)          list = list.filter((p) => p.tags?.includes(opts.tag!));
      if (opts?.search) {
        const q = opts.search.toLowerCase();
        list = list.filter((p) => p.name.toLowerCase().includes(q) || p.brand?.toLowerCase().includes(q));
      }
      return list;
    }
  },

  async getProduct(id: string): Promise<Product | undefined> {
    try {
      // We need tenantSlug for the adapter but don't have it here.
      // Extract from the cache or use a placeholder — the component
      // already has the tenant context from its parent route.
      const raw = (await http.get(`/products/${id}`)) as unknown as Record<string, unknown>;
      const tenantSlug = String(raw.tenantSlug ?? "");
      return adaptProduct(raw, tenantSlug);
    } catch {
      const { products } = await import("../mock/products");
      return products.find((p) => p.id === id);
    }
  },

  // ── Reviews (no backend service yet — remain mock) ─────────────────────────

  async listReviews(productId: string): Promise<Review[]> {
    const { reviews } = await import("../mock/reviews");
    return reviews.filter((r) => r.productId === productId);
  },

  // ── Coupons ────────────────────────────────────────────────────────────────

  async listCoupons(tenantSlug: string): Promise<Coupon[]> {
    try {
      const tenantId = await resolveTenantId(tenantSlug);
      const res = await http.get<Record<string, unknown>[]>("/coupons", withTenant(tenantId));
      const items = Array.isArray(res) ? res : [];
      return items.map((r) => ({
        code:        String(r.code ?? ""),
        description: String(r.description ?? ""),
        percent:     r.type === "PERCENT" ? Number(r.value) : undefined,
        flat:        r.type === "FLAT"    ? Number(r.value) : undefined,
        minOrder:    r.minOrderValue      ? Number(r.minOrderValue) : undefined,
        disabled:    !r.active,
      }));
    } catch {
      const { coupons } = await import("../mock/reviews");
      return coupons.filter((c) => !c.disabled);
    }
  },

  // ── Search autocomplete ────────────────────────────────────────────────────

  async searchAutocomplete(tenantSlug: string, q: string): Promise<Product[]> {
    if (!q) return [];
    try {
      const tenantId = await resolveTenantId(tenantSlug);
      const res = await http.get<{ data: Record<string, unknown>[] }>(
        `/products?search=${encodeURIComponent(q)}&size=6`,
        withTenant(tenantId),
      );
      const items = (res as unknown as { data: Record<string, unknown>[] }).data ?? (Array.isArray(res) ? res : []);
      return (items as Record<string, unknown>[]).map((r) => adaptProduct(r, tenantSlug));
    } catch {
      const { products } = await import("../mock/products");
      const query = q.toLowerCase();
      return products
        .filter((p) => p.tenantSlug === tenantSlug)
        .filter((p) => p.name.toLowerCase().includes(query) || p.brand?.toLowerCase().includes(query))
        .slice(0, 6);
    }
  },

  // ── Orders (used by checkout and order list) ───────────────────────────────

  async createOrder(tenantSlug: string, payload: {
    cartId?: string;
    addressId?: string;
    deliverySlot?: string;
    paymentMethod: string;
    notes?: string;
    deliveryAddress?: Record<string, string>;
  }): Promise<Order> {
    const tenantId = await resolveTenantId(tenantSlug);
    const raw = await http.post<Record<string, unknown>>("/orders", payload, withTenant(tenantId));
    return adaptOrder(raw as unknown as Record<string, unknown>, tenantSlug);
  },

  async listOrders(tenantSlug: string): Promise<Order[]> {
    try {
      const tenantId = await resolveTenantId(tenantSlug);
      const res = await http.get<{ data: Record<string, unknown>[] }>(
        "/orders?size=50",
        withTenant(tenantId),
      );
      const items = (res as unknown as { data: Record<string, unknown>[] }).data ?? (Array.isArray(res) ? res : []);
      return (items as Record<string, unknown>[]).map((r) => adaptOrder(r, tenantSlug));
    } catch {
      return [];
    }
  },

  async getOrder(orderId: string, tenantSlug: string): Promise<Order> {
    const tenantId = await resolveTenantId(tenantSlug);
    const raw = await http.get<Record<string, unknown>>(`/orders/${orderId}`, withTenant(tenantId));
    return adaptOrder(raw as unknown as Record<string, unknown>, tenantSlug);
  },

  // ── Cart (server-side cart sync) ───────────────────────────────────────────

  async addToCart(tenantSlug: string, productId: string, variantId?: string, quantity = 1) {
    const tenantId = await resolveTenantId(tenantSlug);
    return http.post("/cart/items", { productId, variantId, quantity }, withTenant(tenantId));
  },

  async removeFromCart(tenantSlug: string, itemId: string) {
    const tenantId = await resolveTenantId(tenantSlug);
    return http.delete(`/cart/items/${itemId}`, withTenant(tenantId));
  },

  async getCart(tenantSlug: string) {
    const tenantId = await resolveTenantId(tenantSlug);
    return http.get("/cart", withTenant(tenantId));
  },
};

// ── Order adapter ─────────────────────────────────────────────────────────────

function adaptOrder(raw: Record<string, unknown>, tenantSlug: string): Order {
  const addr = (raw.deliveryAddress as Record<string, string>) ?? {};
  const items = Array.isArray(raw.items) ? (raw.items as Record<string, unknown>[]) : [];

  const statusMap: Record<string, Order["status"]> = {
    PLACED:            "placed",
    CONFIRMED:         "placed",
    PACKING:           "packing",
    OUT_FOR_DELIVERY:  "out_for_delivery",
    DELIVERED:         "delivered",
    CANCELLED:         "cancelled",
    RETURNED:          "cancelled",
  };

  return {
    id:         String(raw.orderNumber ?? raw.id ?? ""),
    tenantSlug,
    placedAt:   String(raw.placedAt ?? new Date().toISOString()),
    status:     statusMap[String(raw.status ?? "PLACED")] ?? "placed",
    items:      items.map((it) => ({
      productId:  String(it.productId ?? ""),
      name:       String(it.productName ?? it.name ?? ""),
      quantity:   Number(it.quantity ?? 1),
      price:      Number(it.unitPrice ?? it.price ?? 0),
      imageEmoji: String(it.imageEmoji ?? "📦"),
      imageBg:    String(it.imageBg ?? "#f3f4f6"),
    })),
    subtotal:  Number(raw.subtotal ?? 0),
    discount:  Number(raw.discountAmount ?? 0),
    delivery:  Number(raw.deliveryCharge ?? 0),
    total:     Number(raw.totalAmount ?? raw.total ?? 0),
    address: {
      id:     String(addr.id ?? "addr"),
      label:  String(addr.label ?? "Home"),
      line1:  String(addr.line1 ?? ""),
      line2:  addr.line2 ? String(addr.line2) : undefined,
      city:   String(addr.city ?? ""),
      pincode:String(addr.pincode ?? ""),
    },
    slot:    String(raw.deliverySlot ?? raw.slot ?? ""),
    payment: (() => {
      const m = String(raw.paymentMethod ?? "COD").toUpperCase();
      if (m === "COD") return "cod";
      if (m === "UPI") return "upi";
      return "card";
    })(),
  };
}

// ── Query keys (unchanged — all route loaders continue to work) ────────────────

export const qk = {
  tenants:     ["tenants"] as const,
  tenant:      (slug: string)              => ["tenant", slug] as const,
  categories:  (slug: string)              => ["categories", slug] as const,
  products:    (slug: string, opts?: object) => ["products", slug, opts ?? {}] as const,
  product:     (id: string)               => ["product", id] as const,
  reviews:     (productId: string)        => ["reviews", productId] as const,
  coupons:     ["coupons"] as const,
  autocomplete:(slug: string, q: string)  => ["autocomplete", slug, q] as const,
  orders:      (slug: string)             => ["orders", slug] as const,
  order:       (id: string, slug: string) => ["order", id, slug] as const,
};
