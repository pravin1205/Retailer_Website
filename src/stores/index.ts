import { create } from "zustand";
import { persist } from "zustand/middleware";
import type { CartItem } from "@/lib/types";

interface CartState {
  // tenantSlug -> CartItem[]
  carts: Record<string, CartItem[]>;
  add: (tenantSlug: string, productId: string, variantId?: string, qty?: number) => void;
  remove: (tenantSlug: string, productId: string, variantId?: string) => void;
  setQty: (tenantSlug: string, productId: string, variantId: string | undefined, qty: number) => void;
  clear: (tenantSlug: string) => void;
  count: (tenantSlug: string) => number;
}

const keyOf = (productId: string, variantId?: string) => `${productId}__${variantId ?? ""}`;

export const useCartStore = create<CartState>()(
  persist(
    (set, get) => ({
      carts: {},
      add: (tenantSlug, productId, variantId, qty = 1) => {
        const carts = { ...get().carts };
        const list = [...(carts[tenantSlug] ?? [])];
        const i = list.findIndex((it) => keyOf(it.productId, it.variantId) === keyOf(productId, variantId));
        if (i >= 0) list[i] = { ...list[i], quantity: list[i].quantity + qty };
        else list.push({ productId, variantId, quantity: qty });
        carts[tenantSlug] = list;
        set({ carts });
      },
      remove: (tenantSlug, productId, variantId) => {
        const carts = { ...get().carts };
        carts[tenantSlug] = (carts[tenantSlug] ?? []).filter(
          (it) => keyOf(it.productId, it.variantId) !== keyOf(productId, variantId),
        );
        set({ carts });
      },
      setQty: (tenantSlug, productId, variantId, qty) => {
        const carts = { ...get().carts };
        const list = [...(carts[tenantSlug] ?? [])];
        const i = list.findIndex((it) => keyOf(it.productId, it.variantId) === keyOf(productId, variantId));
        if (i >= 0) {
          if (qty <= 0) list.splice(i, 1);
          else list[i] = { ...list[i], quantity: qty };
        }
        carts[tenantSlug] = list;
        set({ carts });
      },
      clear: (tenantSlug) => {
        const carts = { ...get().carts };
        delete carts[tenantSlug];
        set({ carts });
      },
      count: (tenantSlug) => (get().carts[tenantSlug] ?? []).reduce((n, it) => n + it.quantity, 0),
    }),
    { name: "marketly-cart" },
  ),
);

interface WishlistState {
  ids: Record<string, string[]>; // tenant -> productIds
  toggle: (tenantSlug: string, productId: string) => void;
  has: (tenantSlug: string, productId: string) => boolean;
}

export const useWishlistStore = create<WishlistState>()(
  persist(
    (set, get) => ({
      ids: {},
      toggle: (tenantSlug, productId) => {
        const ids = { ...get().ids };
        const list = ids[tenantSlug] ?? [];
        ids[tenantSlug] = list.includes(productId) ? list.filter((x) => x !== productId) : [productId, ...list];
        set({ ids });
      },
      has: (tenantSlug, productId) => (get().ids[tenantSlug] ?? []).includes(productId),
    }),
    { name: "marketly-wishlist" },
  ),
);

interface RecentState {
  ids: Record<string, string[]>;
  push: (tenantSlug: string, productId: string) => void;
}

export const useRecentStore = create<RecentState>()(
  persist(
    (set, get) => ({
      ids: {},
      push: (tenantSlug, productId) => {
        const ids = { ...get().ids };
        const list = (ids[tenantSlug] ?? []).filter((x) => x !== productId);
        ids[tenantSlug] = [productId, ...list].slice(0, 12);
        set({ ids });
      },
    }),
    { name: "marketly-recent" },
  ),
);

interface AuthUser {
  id: string;
  name: string;
  email: string;
  phone?: string;
  role?: "customer" | "owner" | "super_admin";
  tenantId?: string;
  tenantSlug?: string;
}

interface AuthState {
  user: AuthUser | null;
  accessToken: string | null;
  refreshToken: string | null;
  loading: boolean;
  error: string | null;
  /** Sign in with email + password. tenantSlug scopes the JWT to a store. */
  login: (email: string, password: string, tenantSlug?: string) => Promise<void>;
  /** Register new account. */
  register: (name: string, email: string, password: string, tenantSlug?: string) => Promise<void>;
  /**
   * Hydrate auth state from a completed OTP verification response.
   * Replaces the old fake loginAs() for OTP-based flows.
   */
  loginFromOtp: (result: import("@/lib/types").OtpVerifyResult, tenantSlug?: string) => void;
  /** Legacy helper kept for admin demo sign-in wall only. */
  loginAs: (email: string, role: "customer" | "owner" | "super_admin") => void;
  logout: () => Promise<void>;
  clearError: () => void;
}

import { tokenStorage } from "@/lib/api/http-client";
import axios from "axios";

export const useAuthStore = create<AuthState>()(
  persist(
    (set, get) => ({
      user: null,
      accessToken: null,
      refreshToken: null,
      loading: false,
      error: null,

      login: async (email, password, tenantSlug) => {
        set({ loading: true, error: null });
        try {
          const { data } = await axios.post("/api/v1/auth/login", {
            email, password, tenantSlug,
          });
          const res = data.data ?? data;
          const u = res.user ?? {};
          const roles: string[] = u.roles ?? [];
          const role: AuthUser["role"] =
            roles.includes("SUPER_ADMIN")   ? "super_admin" :
            roles.includes("TENANT_OWNER")  ? "owner"       :
            roles.includes("STORE_MANAGER") ? "owner"       : "customer";

          tokenStorage.setAccess(res.accessToken);
          tokenStorage.setRefresh(res.refreshToken);

          set({
            user: {
              id:         u.id ?? "",
              name:       (u.email as string)?.split("@")[0] ?? "User",
              email:      u.email ?? email,
              role,
              tenantId:   u.tenantId,
              tenantSlug: u.tenantSlug,
            },
            accessToken:  res.accessToken,
            refreshToken: res.refreshToken,
            loading: false,
          });
        } catch (err: unknown) {
          const message = (err as { message?: string })?.message ?? "Sign in failed";
          set({ loading: false, error: message });
          throw err;
        }
      },

      register: async (name, email, password, tenantSlug) => {
        set({ loading: true, error: null });
        try {
          await axios.post("/api/v1/auth/register", {
            email, password,
            firstName: name.split(" ")[0],
            lastName:  name.split(" ").slice(1).join(" ") || undefined,
            tenantSlug,
          });
          // Auto-login after registration
          await get().login(email, password, tenantSlug);
        } catch (err: unknown) {
          const message = (err as { message?: string })?.message ?? "Registration failed";
          set({ loading: false, error: message });
          throw err;
        }
      },

      loginFromOtp: (result, tenantSlug) => {
        const roles: string[] = result.user?.roles ?? [];
        const role: AuthUser["role"] =
          roles.includes("SUPER_ADMIN")   ? "super_admin" :
          roles.includes("TENANT_OWNER")  ? "owner"       :
          roles.includes("STORE_MANAGER") ? "owner"       : "customer";

        tokenStorage.setAccess(result.accessToken);
        tokenStorage.setRefresh(result.refreshToken);

        set({
          user: {
            id:         result.user?.id ?? "",
            name:       result.user?.phone ?? "User",
            email:      result.user?.phone + "@otp.marketly.internal",
            phone:      result.user?.phone,
            role,
            tenantSlug: tenantSlug ?? result.user?.tenantSlug ?? undefined,
          },
          accessToken:  result.accessToken,
          refreshToken: result.refreshToken,
        });
      },

      loginAs: (email, role) => {
        // Demo / admin sign-in wall bypass — no real token
        set({
          user: { id: "demo", name: email.split("@")[0] || "Guest", email, role },
          accessToken: null, refreshToken: null,
        });
      },

      logout: async () => {
        const rt = get().refreshToken;
        try {
          if (rt) await axios.post("/api/v1/auth/logout", { refreshToken: rt });
        } catch { /* ignore */ }
        tokenStorage.clearAll();
        set({ user: null, accessToken: null, refreshToken: null });
      },

      clearError: () => set({ error: null }),
    }),
    {
      name: "marketly-auth",
      partialize: (s) => ({
        user:         s.user,
        accessToken:  s.accessToken,
        refreshToken: s.refreshToken,
      }),
    },
  ),
);

// ── Onboarding store ──────────────────────────────────────────────────────────

import type { SellerOnboardingState, TenantAccent } from "@/lib/types";

const DEFAULT_SELLER_STATE: SellerOnboardingState = {
  step:       1,
  phone:      "",
  tenantSlug: null,
  businessForm: { name: "", slug: "", category: "", businessType: "", description: "", email: "", tagline: "" },
  addressForm:  { line1: "", line2: "", city: "", state: "", pincode: "", lat: "", lng: "", deliveryRadius: 5 },
  brandingForm: { logoEmoji: "🏪", bannerGradient: "linear-gradient(135deg, oklch(0.95 0.04 158), oklch(0.88 0.08 158))", accent: "emerald", tagline: "" },
  kycForm:      { aadhaar: "", pan: "", gst: "", documentUrls: [], storeImageUrl: "" },
};

interface OnboardingStoreState {
  seller: SellerOnboardingState;
  customerStep: number;
  customerPhone: string;
  customerTenantSlug: string | null;
  updateSeller: (updates: Partial<SellerOnboardingState>) => void;
  updateSellerBusiness: (updates: Partial<SellerOnboardingState["businessForm"]>) => void;
  updateSellerAddress:  (updates: Partial<SellerOnboardingState["addressForm"]>)  => void;
  updateSellerBranding: (updates: Partial<SellerOnboardingState["brandingForm"]>) => void;
  updateSellerKyc:      (updates: Partial<SellerOnboardingState["kycForm"]>)      => void;
  resetSeller: () => void;
  setCustomerStep:      (step: number) => void;
  setCustomerPhone:     (phone: string) => void;
  setCustomerTenantSlug:(slug: string | null) => void;
}

export const useOnboardingStore = create<OnboardingStoreState>()(
  persist(
    (set, get) => ({
      seller:             DEFAULT_SELLER_STATE,
      customerStep:       1,
      customerPhone:      "",
      customerTenantSlug: null,

      updateSeller: (updates) =>
        set((s) => ({ seller: { ...s.seller, ...updates } })),

      updateSellerBusiness: (updates) =>
        set((s) => ({ seller: { ...s.seller, businessForm: { ...s.seller.businessForm, ...updates } } })),

      updateSellerAddress: (updates) =>
        set((s) => ({ seller: { ...s.seller, addressForm: { ...s.seller.addressForm, ...updates } } })),

      updateSellerBranding: (updates) =>
        set((s) => ({ seller: { ...s.seller, brandingForm: { ...s.seller.brandingForm, ...updates } } })),

      updateSellerKyc: (updates) =>
        set((s) => ({ seller: { ...s.seller, kycForm: { ...s.seller.kycForm, ...updates } } })),

      resetSeller: () => set({ seller: DEFAULT_SELLER_STATE }),

      setCustomerStep:       (step)  => set({ customerStep: step }),
      setCustomerPhone:      (phone) => set({ customerPhone: phone }),
      setCustomerTenantSlug: (slug)  => set({ customerTenantSlug: slug }),
    }),
    {
      name: "marketly-onboarding",
      // Only persist seller draft — customer steps are ephemeral (OTP expires in 10 min)
      partialize: (s) => ({ seller: s.seller }),
    },
  ),
);

interface OrdersState {
  orders: import("@/lib/types").Order[];
  add: (order: import("@/lib/types").Order) => void;
}

export const useOrdersStore = create<OrdersState>()(
  persist(
    (set) => ({
      orders: [],
      add: (order) => set((s) => ({ orders: [order, ...s.orders] })),
    }),
    { name: "marketly-orders" },
  ),
);
