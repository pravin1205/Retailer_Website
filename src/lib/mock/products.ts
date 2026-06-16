import type { Category, Product } from "../types";

export const categories: Category[] = [
  // FreshMart
  { id: "fm-c1", tenantSlug: "freshmart", name: "Fruits & Veggies", slug: "fruits-veggies", emoji: "🍎" },
  { id: "fm-c2", tenantSlug: "freshmart", name: "Dairy & Eggs", slug: "dairy-eggs", emoji: "🥛" },
  { id: "fm-c3", tenantSlug: "freshmart", name: "Bakery", slug: "bakery", emoji: "🍞" },
  { id: "fm-c4", tenantSlug: "freshmart", name: "Snacks", slug: "snacks", emoji: "🍿" },
  { id: "fm-c5", tenantSlug: "freshmart", name: "Beverages", slug: "beverages", emoji: "🥤" },
  { id: "fm-c6", tenantSlug: "freshmart", name: "Household", slug: "household", emoji: "🧺" },
  // Organic Basket
  { id: "ob-c1", tenantSlug: "organicbasket", name: "Fresh Produce", slug: "produce", emoji: "🥦" },
  { id: "ob-c2", tenantSlug: "organicbasket", name: "Dairy", slug: "dairy", emoji: "🧀" },
  { id: "ob-c3", tenantSlug: "organicbasket", name: "Pantry", slug: "pantry", emoji: "🌾" },
  { id: "ob-c4", tenantSlug: "organicbasket", name: "Wellness", slug: "wellness", emoji: "🍵" },
  // ElectroWorld
  { id: "ew-c1", tenantSlug: "electroworld", name: "Audio", slug: "audio", emoji: "🎧" },
  { id: "ew-c2", tenantSlug: "electroworld", name: "Mobiles", slug: "mobiles", emoji: "📱" },
  { id: "ew-c3", tenantSlug: "electroworld", name: "Laptops", slug: "laptops", emoji: "💻" },
  { id: "ew-c4", tenantSlug: "electroworld", name: "Smart Home", slug: "smart-home", emoji: "💡" },
  { id: "ew-c5", tenantSlug: "electroworld", name: "Accessories", slug: "accessories", emoji: "🔌" },
  // Petite Patisserie
  { id: "pp-c1", tenantSlug: "petitepatisserie", name: "Breads", slug: "breads", emoji: "🥖" },
  { id: "pp-c2", tenantSlug: "petitepatisserie", name: "Pastries", slug: "pastries", emoji: "🥐" },
  { id: "pp-c3", tenantSlug: "petitepatisserie", name: "Cakes", slug: "cakes", emoji: "🎂" },
  // Care Pharmacy
  { id: "cp-c1", tenantSlug: "carepharmacy", name: "Wellness", slug: "wellness", emoji: "💊" },
  { id: "cp-c2", tenantSlug: "carepharmacy", name: "Personal Care", slug: "personal-care", emoji: "🧴" },
  { id: "cp-c3", tenantSlug: "carepharmacy", name: "First Aid", slug: "first-aid", emoji: "🩹" },
];

const bg = {
  green: "oklch(0.96 0.04 158)",
  lime: "oklch(0.96 0.05 130)",
  yellow: "oklch(0.96 0.05 90)",
  orange: "oklch(0.95 0.05 60)",
  rose: "oklch(0.96 0.04 15)",
  blue: "oklch(0.95 0.04 240)",
  purple: "oklch(0.96 0.03 290)",
  cream: "oklch(0.97 0.025 80)",
  mint: "oklch(0.95 0.04 170)",
};

let pid = 0;
const p = (
  tenantSlug: string,
  categorySlug: string,
  data: Omit<Product, "id" | "tenantSlug" | "categorySlug" | "imageBg" | "stock" | "rating" | "reviewCount" | "mrp"> & {
    bg: keyof typeof bg;
    mrp?: number;
    stock?: number;
    rating?: number;
    reviewCount?: number;
  },
): Product => ({
  id: `p-${++pid}`,
  tenantSlug,
  categorySlug,
  name: data.name,
  brand: data.brand,
  description: data.description,
  price: data.price,
  mrp: data.mrp ?? Math.round(data.price * 1.18),
  unit: data.unit,
  imageEmoji: data.imageEmoji,
  imageBg: bg[data.bg],
  stock: data.stock ?? 24,
  rating: data.rating ?? 4.4 + Math.random() * 0.5,
  reviewCount: data.reviewCount ?? 40 + Math.floor(Math.random() * 800),
  variants: data.variants,
  tags: data.tags,
});

export const products: Product[] = [
  // FRESHMART — fruits-veggies
  p("freshmart", "fruits-veggies", { name: "Royal Gala Apples", brand: "Farm Fresh", description: "Crisp, sweet apples from Himachal orchards.", price: 189, unit: "1 kg", imageEmoji: "🍎", bg: "rose", tags: ["trending", "bestseller"] }),
  p("freshmart", "fruits-veggies", { name: "Cavendish Bananas", brand: "Farm Fresh", description: "Naturally ripened, perfect for smoothies.", price: 59, unit: "6 pcs", imageEmoji: "🍌", bg: "yellow", tags: ["trending"] }),
  p("freshmart", "fruits-veggies", { name: "Hass Avocados", brand: "Imported", description: "Buttery texture, ready to eat in 2 days.", price: 249, unit: "2 pcs", imageEmoji: "🥑", bg: "green", tags: ["featured"] }),
  p("freshmart", "fruits-veggies", { name: "Cherry Tomatoes", brand: "Hydro Farms", description: "Sweet, vine-ripened cherry tomatoes.", price: 89, unit: "250 g", imageEmoji: "🍅", bg: "rose" }),
  p("freshmart", "fruits-veggies", { name: "Baby Spinach", brand: "Greenleaf", description: "Tender baby spinach leaves, triple-washed.", price: 65, unit: "200 g", imageEmoji: "🥬", bg: "green", tags: ["new"] }),
  p("freshmart", "fruits-veggies", { name: "Sweet Corn", brand: "Farm Fresh", description: "Juicy sweet corn cobs.", price: 49, unit: "2 pcs", imageEmoji: "🌽", bg: "yellow" }),
  // dairy-eggs
  p("freshmart", "dairy-eggs", { name: "Full Cream Milk", brand: "Dairy Pure", description: "Farm-fresh full cream milk, pasteurized.", price: 68, unit: "1 L", imageEmoji: "🥛", bg: "blue", tags: ["bestseller"] }),
  p("freshmart", "dairy-eggs", { name: "Brown Eggs", brand: "Happy Hens", description: "Free-range brown eggs, pack of 12.", price: 119, unit: "12 pcs", imageEmoji: "🥚", bg: "cream", tags: ["trending"] }),
  p("freshmart", "dairy-eggs", { name: "Greek Yogurt", brand: "Epigamia", description: "Thick, creamy unsweetened Greek yogurt.", price: 95, unit: "400 g", imageEmoji: "🍶", bg: "cream" }),
  p("freshmart", "dairy-eggs", { name: "Amul Butter", brand: "Amul", description: "Classic salted butter for breakfast.", price: 58, unit: "100 g", imageEmoji: "🧈", bg: "yellow" }),
  // bakery
  p("freshmart", "bakery", { name: "Sourdough Loaf", brand: "Daily Bake", description: "Slow-fermented artisan sourdough.", price: 159, unit: "400 g", imageEmoji: "🍞", bg: "cream", tags: ["featured"] }),
  p("freshmart", "bakery", { name: "Multigrain Bread", brand: "Daily Bake", description: "7-grain loaf, soft and wholesome.", price: 75, unit: "350 g", imageEmoji: "🍞", bg: "orange" }),
  p("freshmart", "bakery", { name: "Butter Croissants", brand: "Daily Bake", description: "Flaky, all-butter croissants.", price: 149, unit: "4 pcs", imageEmoji: "🥐", bg: "cream" }),
  // snacks
  p("freshmart", "snacks", { name: "Salted Almonds", brand: "Nutty Co.", description: "Lightly salted California almonds.", price: 299, unit: "200 g", imageEmoji: "🥜", bg: "orange" }),
  p("freshmart", "snacks", { name: "Dark Chocolate 70%", brand: "Cacao", description: "Single-origin dark chocolate bar.", price: 179, unit: "100 g", imageEmoji: "🍫", bg: "cream", tags: ["bestseller"] }),
  p("freshmart", "snacks", { name: "Sea Salt Chips", brand: "Crunch", description: "Hand-cooked kettle chips.", price: 99, unit: "150 g", imageEmoji: "🥔", bg: "yellow" }),
  // beverages
  p("freshmart", "beverages", { name: "Cold Brew Coffee", brand: "Sleepy Owl", description: "Smooth cold brew, ready to drink.", price: 199, unit: "200 ml", imageEmoji: "☕", bg: "cream", tags: ["new", "trending"] }),
  p("freshmart", "beverages", { name: "Sparkling Water", brand: "Qua", description: "Naturally carbonated mineral water.", price: 89, unit: "750 ml", imageEmoji: "💧", bg: "blue" }),
  p("freshmart", "beverages", { name: "Orange Juice", brand: "Tropicana", description: "100% pure orange juice, no added sugar.", price: 149, unit: "1 L", imageEmoji: "🍊", bg: "orange" }),
  // household
  p("freshmart", "household", { name: "Dish Soap Refill", brand: "Vim", description: "Lemon-fresh dish soap.", price: 165, unit: "750 ml", imageEmoji: "🧼", bg: "yellow" }),
  p("freshmart", "household", { name: "Bamboo Paper Towels", brand: "EcoLeaf", description: "Sustainable bamboo paper towels, 4-pack.", price: 249, unit: "4 rolls", imageEmoji: "🧻", bg: "cream" }),

  // ORGANIC BASKET
  p("organicbasket", "produce", { name: "Organic Kale", brand: "Farm Direct", description: "Certified organic curly kale.", price: 119, unit: "200 g", imageEmoji: "🥬", bg: "green", tags: ["featured"] }),
  p("organicbasket", "produce", { name: "Heirloom Tomatoes", brand: "Farm Direct", description: "Mixed heirloom tomatoes, vine-ripened.", price: 189, unit: "500 g", imageEmoji: "🍅", bg: "rose", tags: ["trending"] }),
  p("organicbasket", "produce", { name: "Rainbow Carrots", brand: "Farm Direct", description: "Sweet, multi-color carrots.", price: 99, unit: "500 g", imageEmoji: "🥕", bg: "orange" }),
  p("organicbasket", "produce", { name: "Microgreens Mix", brand: "Urban Farm", description: "Pea, radish & sunflower microgreens.", price: 149, unit: "50 g", imageEmoji: "🌱", bg: "green", tags: ["new"] }),
  p("organicbasket", "dairy", { name: "A2 Cow Ghee", brand: "Pure Indian", description: "Hand-churned A2 cultured ghee.", price: 749, unit: "500 ml", imageEmoji: "🫙", bg: "yellow", tags: ["bestseller"] }),
  p("organicbasket", "dairy", { name: "Farm Paneer", brand: "Pure Indian", description: "Fresh paneer made daily.", price: 189, unit: "200 g", imageEmoji: "🧀", bg: "cream" }),
  p("organicbasket", "pantry", { name: "Cold-Pressed Coconut Oil", brand: "Coco", description: "Virgin cold-pressed coconut oil.", price: 449, unit: "500 ml", imageEmoji: "🥥", bg: "cream" }),
  p("organicbasket", "pantry", { name: "Pink Himalayan Salt", brand: "Saltworks", description: "Hand-mined pink salt.", price: 199, unit: "1 kg", imageEmoji: "🧂", bg: "rose" }),
  p("organicbasket", "pantry", { name: "Quinoa", brand: "Whole Grain", description: "Organic white quinoa.", price: 379, unit: "500 g", imageEmoji: "🌾", bg: "cream", tags: ["featured"] }),
  p("organicbasket", "wellness", { name: "Matcha Powder", brand: "Kyoto", description: "Ceremonial-grade matcha.", price: 899, unit: "50 g", imageEmoji: "🍵", bg: "mint", tags: ["trending"] }),
  p("organicbasket", "wellness", { name: "Raw Forest Honey", brand: "Beewise", description: "Unprocessed multi-floral honey.", price: 549, unit: "500 g", imageEmoji: "🍯", bg: "yellow" }),

  // ELECTROWORLD
  p("electroworld", "audio", { name: "AirPulse Pro", brand: "Sonix", description: "Active noise-cancelling true wireless earbuds with 36h battery.", price: 12499, mrp: 14999, imageEmoji: "🎧", bg: "purple", variants: [{ id: "v1", label: "Midnight Black" }, { id: "v2", label: "Pearl White" }], tags: ["bestseller", "featured"] }),
  p("electroworld", "audio", { name: "Studio Over-Ears", brand: "Auralis", description: "Audiophile-grade open-back headphones.", price: 18999, mrp: 21999, imageEmoji: "🎧", bg: "blue" }),
  p("electroworld", "audio", { name: "Portable BT Speaker", brand: "BoomCo", description: "IP67 waterproof, 24h playback.", price: 4499, mrp: 5499, imageEmoji: "🔊", bg: "purple", tags: ["trending"] }),
  p("electroworld", "mobiles", { name: "Pixel-X 14 Pro", brand: "Pixel", description: "6.7\" AMOLED, triple camera, 256GB.", price: 89999, mrp: 99999, imageEmoji: "📱", bg: "blue", variants: [{ id: "v1", label: "128 GB" }, { id: "v2", label: "256 GB", priceDelta: 10000 }, { id: "v3", label: "512 GB", priceDelta: 25000 }], tags: ["new", "featured"] }),
  p("electroworld", "mobiles", { name: "OnePlus Nord 5", brand: "OnePlus", description: "Snapdragon 7s, 120Hz display.", price: 29999, mrp: 34999, imageEmoji: "📱", bg: "rose" }),
  p("electroworld", "laptops", { name: "MacAir 15 (M3)", brand: "Apple", description: "Apple M3, 16GB unified memory, 512GB SSD.", price: 134999, mrp: 144999, imageEmoji: "💻", bg: "cream", tags: ["bestseller"] }),
  p("electroworld", "laptops", { name: "ThinkBook 14", brand: "Lenovo", description: "Intel Core Ultra 7, 16GB, 1TB SSD.", price: 79999, mrp: 89999, imageEmoji: "💻", bg: "blue" }),
  p("electroworld", "smart-home", { name: "Smart Bulb 4-pack", brand: "Lumio", description: "16M color RGB, voice-controlled.", price: 2999, mrp: 3999, imageEmoji: "💡", bg: "yellow", tags: ["trending"] }),
  p("electroworld", "smart-home", { name: "Robot Vacuum X1", brand: "Roomi", description: "LiDAR mapping, self-emptying.", price: 34999, mrp: 39999, imageEmoji: "🤖", bg: "purple" }),
  p("electroworld", "accessories", { name: "USB-C 100W Charger", brand: "Powly", description: "GaN tech, 3-port fast charger.", price: 2499, mrp: 2999, imageEmoji: "🔌", bg: "green" }),
  p("electroworld", "accessories", { name: "MagSafe Power Bank", brand: "Powly", description: "10,000 mAh magnetic wireless.", price: 3999, mrp: 4999, imageEmoji: "🔋", bg: "mint", tags: ["new"] }),

  // PETITE PATISSERIE
  p("petitepatisserie", "breads", { name: "Country Sourdough", brand: "House", description: "48-hour fermented sourdough boule.", price: 320, unit: "800 g", imageEmoji: "🍞", bg: "cream", tags: ["bestseller", "featured"] }),
  p("petitepatisserie", "breads", { name: "Baguette", brand: "House", description: "Classic French baguette, baked twice daily.", price: 180, unit: "1 pc", imageEmoji: "🥖", bg: "orange" }),
  p("petitepatisserie", "pastries", { name: "Almond Croissant", brand: "House", description: "Filled with frangipane, dusted with sugar.", price: 220, unit: "1 pc", imageEmoji: "🥐", bg: "cream", tags: ["trending"] }),
  p("petitepatisserie", "pastries", { name: "Pain au Chocolat", brand: "House", description: "Buttery laminated pastry with dark chocolate.", price: 180, unit: "1 pc", imageEmoji: "🥐", bg: "orange" }),
  p("petitepatisserie", "cakes", { name: "Burnt Basque Cheesecake", brand: "House", description: "Caramelized top, custardy center.", price: 1200, unit: "500 g", imageEmoji: "🍰", bg: "cream", tags: ["featured"] }),
  p("petitepatisserie", "cakes", { name: "Pistachio Tres Leches", brand: "House", description: "Three-milk cake with crushed pistachios.", price: 1450, unit: "600 g", imageEmoji: "🎂", bg: "mint" }),

  // CARE PHARMACY
  p("carepharmacy", "wellness", { name: "Vitamin C 1000mg", brand: "Wellness+", description: "Sustained-release tablets, 60 pack.", price: 449, unit: "60 tab", imageEmoji: "💊", bg: "orange", tags: ["bestseller"] }),
  p("carepharmacy", "wellness", { name: "Multivitamin Daily", brand: "Wellness+", description: "Complete A-Z multivitamin.", price: 549, unit: "90 tab", imageEmoji: "💊", bg: "rose" }),
  p("carepharmacy", "wellness", { name: "Omega-3 Fish Oil", brand: "OceanPure", description: "Triple-strength 1000mg softgels.", price: 749, unit: "60 cap", imageEmoji: "🐟", bg: "blue", tags: ["trending"] }),
  p("carepharmacy", "personal-care", { name: "Gentle Face Wash", brand: "Cetaphil", description: "For sensitive skin, fragrance-free.", price: 399, unit: "250 ml", imageEmoji: "🧴", bg: "blue" }),
  p("carepharmacy", "personal-care", { name: "SPF 50 Sunscreen", brand: "Re'equil", description: "Lightweight broad-spectrum sunscreen.", price: 649, unit: "100 ml", imageEmoji: "🧴", bg: "yellow", tags: ["featured"] }),
  p("carepharmacy", "first-aid", { name: "Adhesive Bandages", brand: "Care", description: "Assorted sizes, pack of 50.", price: 149, unit: "50 pcs", imageEmoji: "🩹", bg: "rose" }),
  p("carepharmacy", "first-aid", { name: "Digital Thermometer", brand: "Omron", description: "Fast 10-second reading.", price: 399, unit: "1 pc", imageEmoji: "🌡️", bg: "blue" }),
];
