export type TenantAccent = "emerald" | "orange" | "purple" | "rose";

export interface Tenant {
  slug: string;
  name: string;
  tagline: string;
  description: string;
  category: string; // "Grocery", "Electronics", etc.
  accent: TenantAccent;
  logoEmoji: string;
  bannerGradient: string; // CSS gradient
  rating: number;
  reviewCount: number;
  deliveryMinutes: number;
  deliveryRadiusKm: number;
  minOrder: number;
  hours: string;
  phone: string;
  address: string;
  featured?: boolean;
}

export interface Category {
  id: string;
  tenantSlug: string;
  name: string;
  slug: string;
  emoji: string;
}

export interface ProductVariant {
  id: string;
  label: string;
  priceDelta?: number;
}

export interface Product {
  id: string;
  tenantSlug: string;
  categorySlug: string;
  name: string;
  brand?: string;
  description: string;
  price: number;
  mrp: number; // strikethrough
  unit?: string; // "500g", "1L"
  imageEmoji: string; // placeholder visual
  imageBg: string; // soft bg hex/oklch
  stock: number;
  rating: number;
  reviewCount: number;
  variants?: ProductVariant[];
  tags?: string[]; // "trending", "featured", "new", "bestseller"
}

export interface Review {
  id: string;
  productId: string;
  author: string;
  rating: number;
  date: string;
  body: string;
}

export interface CartItem {
  productId: string;
  variantId?: string;
  quantity: number;
}

export interface Address {
  id: string;
  label: string; // "Home", "Office"
  line1: string;
  line2?: string;
  city: string;
  pincode: string;
}

export interface Order {
  id: string;
  tenantSlug: string;
  placedAt: string;
  status: "placed" | "packing" | "out_for_delivery" | "delivered" | "cancelled";
  items: { productId: string; name: string; quantity: number; price: number; imageEmoji: string; imageBg: string }[];
  subtotal: number;
  discount: number;
  delivery: number;
  total: number;
  address: Address;
  slot: string;
  payment: "cod" | "upi" | "card";
  customer?: { name: string; email?: string; phone?: string };
}

export interface Coupon {
  code: string;
  description: string;
  percent?: number;
  flat?: number;
  minOrder?: number;
  disabled?: boolean;
}
