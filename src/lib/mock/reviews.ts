import type { Coupon, Review } from "../types";

export const reviews: Review[] = [
  { id: "r1", productId: "p-1", author: "Ananya S.", rating: 5, date: "2026-05-12", body: "Apples were crisp and sweet. Will reorder!" },
  { id: "r2", productId: "p-1", author: "Rohan K.", rating: 4, date: "2026-05-08", body: "Good quality, packaging could be better." },
  { id: "r3", productId: "p-1", author: "Meera P.", rating: 5, date: "2026-04-30", body: "Delivered in 10 minutes. Impressed." },
];

export const coupons: Coupon[] = [
  { code: "FRESH10", description: "10% off your first order", percent: 10, minOrder: 299 },
  { code: "FIRST50", description: "Flat ₹50 off above ₹499", flat: 50, minOrder: 499 },
  { code: "WEEKEND15", description: "15% off on weekend orders", percent: 15, minOrder: 599 },
];
