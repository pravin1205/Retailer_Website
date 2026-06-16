import type { Order } from "../types";
import { products } from "./products";

const STATUSES: Order["status"][] = ["placed", "packing", "out_for_delivery", "delivered", "delivered", "delivered", "cancelled"];
const NAMES = [
  "Aarav Mehta", "Diya Sharma", "Ishaan Kapoor", "Saanvi Gupta", "Vivaan Iyer",
  "Aanya Reddy", "Kabir Patel", "Myra Joshi", "Aditya Rao", "Anika Bansal",
  "Reyansh Verma", "Aarohi Nair", "Aryan Singh", "Kiara Das", "Vihaan Khanna",
];
const ADDR = [
  "DLF Cyber City", "Sector 18 Noida", "Indiranagar Bangalore", "Bandra West Mumbai",
  "Koramangala Bangalore", "HSR Layout", "Powai Mumbai", "Saket Delhi",
];

function mulberry32(seed: number) {
  return function () {
    let t = (seed += 0x6d2b79f5);
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

function buildForTenant(slug: string, count: number, startSeed: number): Order[] {
  const rnd = mulberry32(startSeed);
  const pool = products.filter((p) => p.tenantSlug === slug);
  if (pool.length === 0) return [];
  const out: Order[] = [];
  const now = Date.now();
  for (let i = 0; i < count; i++) {
    const itemsCount = 1 + Math.floor(rnd() * 4);
    const items: Order["items"] = [];
    let subtotal = 0;
    for (let j = 0; j < itemsCount; j++) {
      const p = pool[Math.floor(rnd() * pool.length)];
      const qty = 1 + Math.floor(rnd() * 3);
      items.push({ productId: p.id, name: p.name, quantity: qty, price: p.price, imageEmoji: p.imageEmoji, imageBg: p.imageBg });
      subtotal += p.price * qty;
    }
    const delivery = subtotal > 999 ? 0 : 29;
    const total = subtotal + delivery;
    const daysAgo = Math.floor(rnd() * 14);
    const hoursAgo = Math.floor(rnd() * 24);
    const placedAt = new Date(now - daysAgo * 86400_000 - hoursAgo * 3600_000).toISOString();
    const status = daysAgo > 2 ? "delivered" : STATUSES[Math.floor(rnd() * STATUSES.length)];
    const name = NAMES[Math.floor(rnd() * NAMES.length)];
    out.push({
      id: `seed_${slug}_${i}`,
      tenantSlug: slug,
      placedAt,
      status,
      items,
      subtotal,
      discount: 0,
      delivery,
      total,
      address: {
        id: "a",
        label: "Home",
        line1: `Flat ${10 + Math.floor(rnd() * 200)}`,
        line2: ADDR[Math.floor(rnd() * ADDR.length)],
        city: "Delhi",
        pincode: `1100${10 + Math.floor(rnd() * 90)}`,
      },
      slot: "Today, 5:00–6:00 PM",
      payment: ["upi", "card", "cod"][Math.floor(rnd() * 3)] as Order["payment"],
      customer: {
        name,
        email: name.toLowerCase().replace(/\s+/g, ".") + "@example.com",
        phone: `+91 9${(800000000 + Math.floor(rnd() * 99999999)).toString().slice(0, 9)}`,
      },
    });
  }
  return out.sort((a, b) => +new Date(b.placedAt) - +new Date(a.placedAt));
}

export const seedOrders: Order[] = [
  ...buildForTenant("freshmart", 32, 101),
  ...buildForTenant("organicbasket", 18, 202),
  ...buildForTenant("electroworld", 14, 303),
  ...buildForTenant("petitepatisserie", 10, 404),
  ...buildForTenant("carepharmacy", 12, 505),
];
