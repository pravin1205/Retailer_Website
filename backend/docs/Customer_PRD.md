# Marketly — Customer Experience
# Product Requirements Document (PRD)

**Version:** 1.0  
**Date:** June 2026  
**Prepared by:** Product Team — Marketly  
**Document Type:** Product Requirements Document  
**Audience:** Engineering, Design, QA, Product

---

## Table of Contents

1. [Document Purpose & Scope](#1-document-purpose--scope)
2. [Platform Overview](#2-platform-overview)
3. [Design Principles](#3-design-principles)
4. [Feature F-01 — Onboarding & Account Registration](#4-feature-f-01--onboarding--account-registration)
5. [Feature F-02 — Customer Profile & Address Management](#5-feature-f-02--customer-profile--address-management)
6. [Feature F-03 — Store Discovery & Home Feed](#6-feature-f-03--store-discovery--home-feed)
7. [Feature F-04 — Product Search & Filtering](#7-feature-f-04--product-search--filtering)
8. [Feature F-05 — Product Listing Page](#8-feature-f-05--product-listing-page)
9. [Feature F-06 — Product Detail Page](#9-feature-f-06--product-detail-page)
10. [Feature F-07 — Cart Management](#10-feature-f-07--cart-management)
11. [Feature F-08 — Checkout Flow](#11-feature-f-08--checkout-flow)
12. [Feature F-09 — Payment](#12-feature-f-09--payment)
13. [Feature F-10 — Order Status & Tracking](#13-feature-f-10--order-status--tracking)
14. [Feature F-11 — Order History & Reorder](#14-feature-f-11--order-history--reorder)
15. [Feature F-12 — Ratings & Reviews](#15-feature-f-12--ratings--reviews)
16. [Feature F-13 — Issue Reporting & Refunds](#16-feature-f-13--issue-reporting--refunds)
17. [Feature F-14 — Offers, Coupons & Deals](#17-feature-f-14--offers-coupons--deals)
18. [Feature F-15 — Loyalty Points & Tier System](#18-feature-f-15--loyalty-points--tier-system)
19. [Feature F-16 — Store Subscription / Pass](#19-feature-f-16--store-subscription--pass)
20. [Feature F-17 — Referral Program](#20-feature-f-17--referral-program)
21. [Feature F-18 — Wishlist](#21-feature-f-18--wishlist)
22. [Feature F-19 — Subscribe & Save (Auto-Reorder)](#22-feature-f-19--subscribe--save-auto-reorder)
23. [Feature F-20 — Personalisation & Smart Recommendations](#23-feature-f-20--personalisation--smart-recommendations)
24. [Feature F-21 — Notifications & Communication](#24-feature-f-21--notifications--communication)
25. [Feature F-22 — Customer Support](#25-feature-f-22--customer-support)
26. [Feature F-23 — WhatsApp Ordering (Text & Voice)](#26-feature-f-23--whatsapp-ordering-text--voice)
27. [Feature F-24 — Progressive Web App (PWA)](#27-feature-f-24--progressive-web-app-pwa)
28. [Feature F-25 — Accessibility & Inclusivity](#28-feature-f-25--accessibility--inclusivity)
29. [Delivery Scope Clarification](#29-delivery-scope-clarification)
30. [Feature Priority Matrix](#30-feature-priority-matrix)

---

## 1. Document Purpose & Scope

### Purpose

This Product Requirements Document (PRD) defines the complete set of customer-facing features for the Marketly platform. It is written to serve as the single source of truth for engineering, design, and QA teams during the development of the customer experience layer.

Every feature in this document is described with:
- **What it is** — a clear definition of the feature
- **Why it must be built** — business and customer justification
- **How it works** — detailed functional specification covering all screens, flows, states, and edge cases

### Scope

This document covers all features accessible to the **end customer** — the person who discovers a store, browses products, places orders, and manages their account on Marketly.

This document does **not** cover:
- Seller / Tenant dashboard features
- Super Admin panel features
- Internal platform infrastructure
- Delivery logistics (delivery is handled entirely by the seller)

### Important Constraint — Delivery

**The Marketly platform does not handle delivery.** Delivery is the sole responsibility of each individual seller. The platform's role ends at order placement and payment confirmation. Sellers manage their own delivery staff, dispatch process, and delivery communication. The platform facilitates status updates that the seller pushes back to the customer.

---

## 2. Platform Overview

Marketly is a **multi-tenant retail commerce platform**. Each seller (tenant) operates their own independent storefront with a unique URL, product catalog, pricing, and branding. Customers shop within a specific store — they do not see products from other stores while browsing.

**Customer touchpoints:**
- Web application (desktop + mobile browser)
- Progressive Web App (installable on Android and iOS)
- WhatsApp (text and voice message ordering)

**Key actors:**
- **Customer** — discovers products, places orders, makes payments, tracks orders
- **Seller** — manages products, accepts/rejects orders, updates order status, handles delivery
- **Platform** — provides the infrastructure, payment processing, notifications, and communication layer

---

## 3. Design Principles

Every customer-facing feature must adhere to these principles:

1. **Mobile first** — The majority of Indian retail customers shop on mobile. Every flow must be designed for a 375px screen width first.
2. **Speed over completeness** — A fast, incomplete experience is better than a slow, complete one. Pages must load within 2 seconds on a 4G connection.
3. **Minimal friction to first order** — A new customer must be able to discover a product and place an order in under 3 minutes.
4. **Vernacular-ready** — All customer-facing text must support regional language rendering. WhatsApp flows must respond in the language the customer writes in.
5. **Graceful degradation** — If a backend service is slow or unavailable, show cached data rather than an error screen wherever possible.
6. **Trust at every step** — Show security badges, GST invoices, and clear refund policies. Indian customers need to feel safe paying online.

---

## 4. Feature F-01 — Onboarding & Account Registration

### What is this feature?

Onboarding is the process by which a new customer creates an account on the Marketly platform for the first time. It covers the entry point (how they land), identity verification, and account creation before they can place their first order.

### Why must this be built?

Without an account, the platform cannot associate orders with customers, cannot store saved addresses, cannot track loyalty points, and cannot personalise the experience. Account creation is the foundation of every other customer feature. The onboarding experience is also the first impression — a long or confusing signup flow directly kills conversion.

Reference: Blinkit and Zepto both use phone OTP as the sole entry point. The result is a signup flow that takes under 30 seconds, with no password to remember. This dramatically reduces drop-off at registration.

### Functional Specification

#### 3.1 Guest Browsing

- A customer who visits the store URL must be able to browse the full product catalog, view product detail pages, and add items to cart **without creating an account**.
- The account creation / login prompt must appear **only at checkout**, when the customer attempts to place an order.
- Guest cart items must be preserved and merged into the customer's cart after login.
- **Rationale:** Forcing login before browsing increases bounce rate by 30–40% in commerce applications. Customers must see value (products they want) before being asked for personal information.

#### 3.2 Phone OTP Login / Registration

This is the primary authentication method.

**Flow:**
1. Customer enters their 10-digit Indian mobile number.
2. System sends a 6-digit OTP via SMS.
3. OTP is valid for 10 minutes.
4. Customer enters OTP. On success:
   - If the phone number is new → account is created automatically (no separate registration form).
   - If the phone number exists → customer is logged in.
5. After verification, customer is asked to enter their name and email (both optional, skippable).
6. Customer lands on the store home page, logged in.

**Constraints:**
- Maximum 5 OTP requests per phone number per 10-minute window. On exceeding: show "Too many attempts. Try again in X minutes."
- OTP input field must support Android SMS autofill (OTP reads automatically from SMS).
- If OTP is entered incorrectly 3 times, the session is invalidated and customer must request a new OTP.

**Edge cases:**
- Customer enters a number already linked to a seller account: treat as a different account type, do not merge.
- International numbers: out of scope for now. India (+91) only.

#### 3.3 Google / Apple Sign-In

Alternative to OTP for customers who prefer social login.

- Customer taps "Continue with Google" → standard Google OAuth consent screen → account created or matched by email.
- Customer taps "Continue with Apple" → Apple ID consent → account created or matched.
- On first social login, if no phone number is on file, prompt customer to add and verify a phone number (required for order notifications via SMS/WhatsApp).

#### 3.4 Referral Code at Signup

- During the first login (new account only), show an optional "Have a referral code?" field.
- If a valid code is entered, the referred customer gets a discount on their first order (amount configured by the platform/seller).
- Referral attribution is recorded even if the discount is applied later at checkout.

#### 3.5 Session Management

- After login, the customer receives an access token (valid 15 minutes) and a refresh token (valid 7 days).
- Token refresh happens silently in the background — the customer must never be logged out mid-session unless they explicitly log out or the refresh token expires.
- On a new device, the customer must re-authenticate via OTP.

---

## 5. Feature F-02 — Customer Profile & Address Management

### What is this feature?

The customer profile is the central place where a customer manages their personal information, saved delivery addresses, and account preferences. It is accessible from any store the customer shops at.

### Why must this be built?

A saved profile and saved addresses directly reduce checkout time. Research from Indian e-commerce platforms shows that returning customers with saved addresses complete checkout 4x faster than new customers who must re-enter details. This directly increases repeat order rate. Additionally, profile data (date of birth, dietary preferences) enables personalised marketing and offers.

### Functional Specification

#### 4.1 Profile Information

Fields:
- **First name** (required)
- **Last name** (optional)
- **Mobile number** (verified via OTP, read-only after verification)
- **Email address** (optional, used for invoice emails)
- **Date of birth** (optional, used to trigger birthday offers)
- **Profile photo** (optional, upload from camera or gallery)
- **Dietary preference** (optional, multi-select: Vegetarian / Vegan / Gluten-free / Jain)

All fields are editable except verified mobile number. To change mobile number, customer must verify the new number via OTP.

#### 4.2 Address Management

A customer can save up to 10 addresses. Each address contains:
- **Label** — Home / Work / Other (custom label allowed)
- **Full name** of recipient
- **Mobile number** of recipient (can differ from account number, for gifting)
- **Address line 1** — house/flat/building number
- **Address line 2** — street, area
- **Landmark** — free text (e.g., "Near SBI ATM", "Blue gate building")
- **City**
- **State**
- **Pincode**
- **Delivery instructions** — free text (e.g., "Ring bell twice", "Leave with security")
- **GPS coordinates** — captured when customer uses "Use my current location"
- **Is gated society?** — toggle; if yes, show fields for society name, tower, flat number

**Default address:** Customer marks one address as default. This address is pre-selected at checkout.

**GPS auto-detect:**
- Customer taps "Use current location" → browser Geolocation API → reverse geocoded to a readable address via Google Maps / MapMyIndia API → customer confirms or edits before saving.

**Add via map pin:**
- Customer can drop a pin on a map to set precise location, especially useful in areas where GPS address resolution is imprecise.

**Delivery availability check:**
- When an address is saved or selected at checkout, the system validates against the seller's configured delivery zones (pincode list or radius from store).
- If the address is outside the seller's delivery area: show a clear message — "This store does not deliver to this location. Please choose a different address or store."

---

## 6. Feature F-03 — Store Discovery & Home Feed

### What is this feature?

The home feed is the first screen a customer sees when they open a store. It is a dynamically curated page that surfaces the most relevant products, offers, and collections to drive discovery and conversion.

### Why must this be built?

The home feed is the highest-traffic page in any commerce application. Blinkit and Instamart both invest heavily in their home feed because it directly drives add-to-cart rate. A well-designed home feed reduces the time a customer spends searching for products and increases average order value through cross-sell and upsell shelves. Sellers also need a surface to promote their seasonal collections and flash deals without relying on customers to search.

### Functional Specification

#### 5.1 Location Bar (Persistent)

- At the top of every page within a store, show the customer's current selected delivery address (truncated: "Delivering to: Koramangala, 560034").
- Tapping the bar opens the address selector — customer can switch between saved addresses or add a new one.
- If no address is saved, show "Set delivery location" as a prompt.

#### 5.2 Hero Banners / Carousel

- Full-width image carousel at the top of the home feed.
- Seller uploads banner images via their dashboard with a deep link target (product, category, or offer page).
- Auto-advances every 4 seconds. Manual swipe supported. Dot indicators show position.
- Maximum 5 banners. Minimum 1.

#### 5.3 "Order Again" Section

- Appears only for returning customers who have at least one past order.
- Shows the 8 most recently ordered products as horizontal scrollable cards.
- Each card has a "+" quick-add button to add directly to cart without navigating to the product page.
- **Rationale:** Repeat ordering is the highest-intent action a returning customer takes. Surfacing it immediately on the home feed removes the need to search.

#### 5.4 Category Grid

- Visual grid of all top-level categories the seller has configured.
- Each category tile shows an emoji or icon + category name.
- Tapping a category navigates to the product listing page filtered to that category.
- Maximum 12 categories shown in the grid. A "See all" button expands to full list.

#### 5.5 Product Shelves

Multiple horizontal scrollable shelves, each curated by a rule:

| Shelf Name | Curation Rule |
|---|---|
| Flash Sale | Products with an active flash deal (seller-set start/end time + countdown timer) |
| Best Sellers | Products ranked by order volume in the last 30 days |
| New Arrivals | Products added in the last 14 days, sorted newest first |
| Deals & Offers | Products with a discount percentage above a threshold (seller configures) |
| Only a Few Left | Products where stock quantity is below a seller-configured low-stock threshold |
| Festive Collection | Seller manually curates this shelf for seasonal events |

Each shelf shows a minimum of 4 cards and a maximum of 20. If fewer than 4 products qualify for a rule, the shelf is hidden.

#### 5.6 Flash Sale Countdown

- Flash sale shelf header shows a live countdown timer — "Ends in 01:45:22".
- Timer is computed client-side from the sale end timestamp.
- When the sale ends, the shelf refreshes and either disappears or shows "Sale ended".

#### 5.7 Store Story / Announcement Banner

- A simple banner (text + optional image) the seller can update daily.
- Examples: "Today's fresh catch just arrived!", "Closed on Sunday — pre-order available", "New: Sugar-free range now in stock"
- Shown below the hero carousel. Dismissible by the customer.

---

## 7. Feature F-04 — Product Search & Filtering

### What is this feature?

Search is the primary navigation tool for customers who know what they want. The search experience covers the search bar, autocomplete suggestions, results listing, and filtering and sorting controls.

### Why must this be built?

According to industry data, customers who use search convert at 3–4x the rate of customers who only browse. A fast, accurate search experience is a direct revenue driver. Zepto's search is widely cited as the best in Indian quick-commerce because it returns relevant results instantly and handles spelling errors gracefully. Customers who cannot find a product via search abandon the store — they do not browse to find it.

### Functional Specification

#### 6.1 Search Bar

- Persistently visible at the top of every store page (sticky on scroll).
- Placeholder text: "Search for products, brands..."
- On focus, shows recent searches and trending searches immediately (before the customer types anything).

#### 6.2 Instant Search-as-you-type

- Results update with every keystroke, with no need to press Enter or a search button.
- Debounce of 200ms to avoid firing a request on every single keypress.
- Results appear in a dropdown overlay (not a full page navigation) while the customer is still typing.
- Dropdown shows up to 6 results with product image, name, price, and a quick-add button.

#### 6.3 Autocomplete Suggestions

The dropdown shows three types of suggestions simultaneously:
1. **Product name matches** — exact and partial matches from the catalog
2. **Brand matches** — "Amul", "Britannia", "Fortune"
3. **Category matches** — "Dairy", "Snacks", "Beverages"

#### 6.4 Recent Searches

- Last 10 searches stored locally (device, not server).
- Shown when search bar is focused with no text typed.
- Each recent search has an "X" to remove it individually, plus a "Clear all" option.

#### 6.5 Trending Searches

- Seller configures up to 5 trending search terms in their dashboard.
- Alternatively, auto-populated from the most searched terms in the store in the last 7 days.
- Shown below recent searches on search bar focus.

#### 6.6 Voice Search

- Microphone icon in the search bar.
- On tap: browser Web Speech API → transcribes speech to text → populates search bar → triggers search.
- Fallback message if microphone permission denied: "Enable microphone to use voice search."

#### 6.7 Barcode Scanner

- Camera icon in the search bar.
- On tap: opens device camera in barcode scanning mode.
- Scans EAN-13, EAN-8, QR codes.
- On match: navigates directly to the product detail page.
- On no match: "Product not found. Try searching by name."

#### 6.8 Spell Correction

- If no exact match is found, the system applies fuzzy matching and shows: "Did you mean: **tomato ketchup**?" with a tap-to-search link.
- Handles common phonetic errors (e.g., "dahi" → "curd" / "yogurt" in stores that use English product names).

#### 6.9 Full Search Results Page

When the customer submits a search (presses Enter or taps a suggestion):
- Full page results with the matched products.
- Result count shown — "24 results for 'bread'"
- If zero results: show "No products found for '[query]'" with suggestions to browse categories or check spelling.

#### 6.10 Filters

Available on the full search results page and category listing pages:

| Filter | Options |
|---|---|
| Brand | Multi-select checkbox list of all brands in results |
| Price Range | Dual-handle slider (min ₹ to max ₹) |
| Discount | 10%+ / 20%+ / 30%+ / 50%+ |
| Dietary | Vegetarian / Vegan / Gluten-free / Organic (multi-select) |
| Rating | 4★ & above / 3★ & above |
| Availability | In stock only toggle |

Filters are applied instantly without page reload. Active filter count shown on the filter button ("Filters (3)"). Individual filters can be removed via chips shown above the results.

#### 6.11 Sorting

| Sort Option | Behaviour |
|---|---|
| Relevance (default) | Platform's ranking algorithm |
| Price: Low to High | Ascending effective price |
| Price: High to Low | Descending effective price |
| Discount | Highest discount percentage first |
| Newest | Most recently added products first |
| Most Popular | Highest order volume in last 30 days |
| Rating | Highest average rating first |

---

## 8. Feature F-05 — Product Listing Page

### What is this feature?

The product listing page (PLP) displays a grid or list of products within a category, search result, or curated collection. It is the browsing surface between discovery and the product detail page.

### Why must this be built?

The PLP is where the majority of add-to-cart events happen. Customers who are browsing (not searching) decide what to buy based on how products are presented in the listing. A well-designed PLP with inline add-to-cart controls, clear pricing, and useful badges significantly increases the number of items added per session.

### Functional Specification

#### 7.1 Product Card

Each product is shown as a card containing:
- **Product image** — primary image, tappable to open PDP
- **Product name** — max 2 lines, truncated with ellipsis
- **Weight / unit** — "500g", "1 dozen", "1L"
- **Price** — effective selling price in bold
- **MRP** — crossed out if product has a discount
- **Discount badge** — "20% off" shown in a coloured pill on the card
- **Brand name** — in smaller muted text below product name
- **Add to cart button** — prominent "+" button; once added shows quantity stepper (−/qty/+)

#### 7.2 Status Badges

Overlaid on the product image or shown as a label:

| Badge | Trigger |
|---|---|
| Bestseller | Top 10 products by order volume in store |
| New | Added within last 14 days |
| Organic | Product tagged as organic by seller |
| Limited Stock | Quantity below seller's low-stock threshold |
| Out of Stock | Quantity = 0 (card grayed out) |

#### 7.3 Quick Variant Switcher

- If a product has variants (e.g., 500ml / 1L), show a compact toggle on the card itself.
- Switching variant updates the price and stock status on the card without navigating to PDP.

#### 7.4 Out-of-Stock Handling

- Card is shown with a grey overlay and reduced opacity.
- "Add" button is replaced with "Notify Me" button.
- Tapping "Notify Me" registers a back-in-stock alert for this product (requires login).

#### 7.5 Layout

- **Mobile:** 2-column grid
- **Tablet:** 3-column grid
- **Desktop:** 4-column grid
- Category name shown as a sticky header as the customer scrolls.
- Sub-categories shown as horizontally scrollable tabs below the category header.

#### 7.6 Pagination

- Infinite scroll with automatic next-page loading when customer reaches 80% of the current list.
- Page size: 20 products per load.
- Loading skeleton cards shown during fetch.

---

## 9. Feature F-06 — Product Detail Page

### What is this feature?

The Product Detail Page (PDP) is the full information page for a single product. It is where a customer gets all the information they need to decide whether to buy a product, select a variant, and add it to cart.

### Why must this be built?

The PDP is the highest-intent page in the funnel. A customer on a PDP has already expressed interest in a specific product. The quality of information on this page directly determines whether they convert or abandon. Missing nutritional info, unclear variant pricing, or no customer reviews are among the top reasons customers do not buy. Reference: Blinkit's PDP has nutritional tables, ingredient lists, and customer photos — all of which build the confidence needed for a food purchase.

### Functional Specification

#### 8.1 Image Gallery

- Minimum 1 image, maximum 10 images per product.
- Horizontal swipe carousel on mobile.
- Tap to open full-screen image viewer with pinch-to-zoom.
- Thumbnail strip below carousel on desktop.
- If seller uploads a product video (MP4, max 30 seconds), it appears as the first item in the gallery with a play button overlay.

#### 8.2 Product Header Information

- Product name (full, not truncated)
- Brand name (tappable to filter listing by brand)
- Weight / unit / pack size
- Average star rating + total review count (e.g., "4.2 ★ · 318 reviews") — tappable to scroll to reviews section
- "Verified by FSSAI" or other certification badges if tagged by seller

#### 8.3 Pricing

- **Effective price** — large and bold
- **MRP** — crossed out, shown next to effective price
- **Discount percentage** — "Save 18%" in a highlighted pill
- If product has a combo deal: "Buy 2 for ₹199 (save ₹21)" shown as a highlighted offer strip

#### 8.4 Variant Selector

- If product has variants (size, weight, flavour, colour):
  - Show all variants as selectable chips/pills
  - Selected variant is highlighted
  - Each variant shows its price
  - Switching variant updates all price information, stock status, and images instantly
- "Best value" badge on the variant with the lowest price-per-unit

#### 8.5 Stock Information

- "In stock" — green indicator
- "Only N left" — amber indicator, shown when quantity is below low-stock threshold
- "Out of stock" — red indicator; Add button replaced with "Notify Me"

#### 8.6 Add to Cart Controls

- Quantity selector (−/qty/+) with "Add to Cart" button
- Maximum quantity capped at available stock
- If item is already in cart: show current cart quantity with stepper; no separate "Add" button

#### 8.7 Product Information Tabs

Organised as tappable tabs or collapsible accordion sections:

**Tab 1 — About**
- Full product description (rich text, seller-authored)
- Key highlights as bullet points
- Country of origin, manufacturer name and address
- Imported by (if applicable)

**Tab 2 — Nutritional Information**
- Standard nutrition table: Serving size, Calories, Total Fat, Saturated Fat, Trans Fat, Cholesterol, Sodium, Total Carbohydrates, Dietary Fiber, Total Sugars, Protein, Vitamins & Minerals
- Only shown if seller has filled nutritional data

**Tab 3 — Ingredients**
- Full ingredients list
- Allergen warning (highlighted in bold within the list — e.g., "Contains: **Milk**, **Wheat**, **Soy**")

**Tab 4 — How to Use / Storage**
- Usage instructions
- Storage conditions ("Store in a cool, dry place", "Refrigerate after opening")
- Shelf life / best before information

#### 8.8 Ratings & Reviews Section

- **Summary bar:** Average rating (large number + stars) + rating distribution bar chart (5★ count, 4★ count, etc.)
- **Filter by star rating** — tap a star count to show only those reviews
- **Sort reviews** — Most Recent / Most Helpful / Photos first
- **Individual review card:**
  - Customer name (first name + last initial)
  - Star rating
  - Review date
  - Review title (optional)
  - Review body text
  - Customer uploaded photos (tappable to enlarge)
  - "Verified Purchase" badge if the reviewer has a confirmed order
  - "Helpful" thumbs-up button with count
- Show first 5 reviews; "Load more reviews" button for the rest
- **Write a review button** (only shown to customers who have purchased this product)

#### 8.9 Q&A Section

- Customer asks a question in free text
- Seller receives a notification and answers via their dashboard
- Questions and answers are publicly visible
- Customers can upvote helpful questions
- Show first 3 Q&As; "See all questions" expands

#### 8.10 Related Product Shelves

- **Frequently Bought Together** — horizontal shelf (seller-configured or auto-computed from co-occurrence in orders)
- **Customers Also Viewed** — based on product page view co-occurrence
- **More from this Brand** — other products from the same brand

#### 8.11 Share

- Share button opens native device share sheet
- Shared content: product name, image, price, and store link
- Pre-populated WhatsApp message: "Check out [Product Name] on [Store Name] — only ₹[price]! [link]"

---

## 10. Feature F-07 — Cart Management

### What is this feature?

The cart is the holding area where a customer accumulates products before placing an order. It is a critical conversion point — the cart experience determines whether a customer completes a purchase or abandons it.

### Why must this be built?

Cart abandonment is the single largest source of lost revenue in e-commerce. Industry average cart abandonment rate is 70%. Features like a savings summary, free delivery progress nudge, and seamless coupon application have been proven to reduce abandonment by 15–25% in Indian quick-commerce. Zepto's cart UX is specifically cited by customers as a reason they prefer it over competitors — it shows the total savings clearly and makes applying coupons effortless.

### Functional Specification

#### 9.1 Cart Access

- Cart icon in the header / navigation shows item count badge.
- Tapping opens a **cart sheet** (bottom sheet on mobile, side drawer on desktop) — the customer does not navigate away from the current page.
- Cart sheet can be dismissed by swiping down or tapping outside.

#### 9.2 Cart Item List

Each item in the cart shows:
- Product image, name, weight/unit, variant selected
- Unit price
- Quantity stepper (−/qty/+)
  - Minimum quantity: 1 (tapping − at qty 1 shows a remove confirmation)
  - Maximum quantity: available stock
- Line total (unit price × quantity)
- "Remove" button (trash icon or swipe-to-delete on mobile)

#### 9.3 Out-of-Stock Item Handling

If a product in the cart goes out of stock before checkout:
- Item is shown with a red "Out of stock" label and grayed out
- Customer is prompted to remove or replace it
- Cart cannot be submitted to checkout until all OOS items are removed

#### 9.4 Savings Summary Card

- Prominently shown below the item list
- Shows: "You're saving ₹[X] on this order 🎉"
- Breakdown: item-level discounts + coupon discount separately

#### 9.5 Free Delivery Progress Bar

- If the seller offers free delivery above a minimum order amount:
- Show a progress bar: "Add ₹[X] more for free delivery"
- Bar fills as cart value increases
- On reaching the threshold: "🎉 You've unlocked free delivery!"
- If the seller always charges delivery or always offers free delivery, this bar is hidden

#### 9.6 Coupon Section

- Input field for manual coupon code entry with "Apply" button
- "View all coupons" button opens a bottom sheet listing all coupons applicable to this customer and this cart:
  - Coupon code (tap to auto-fill)
  - Discount description
  - Minimum order requirement
  - Expiry date
  - Pre-calculated savings ("Save ₹45 with this coupon")
  - If a coupon's minimum order is not yet met: shown as greyed with "Add ₹X more to unlock"
- After applying: show coupon chip with "Coupon applied: [CODE] — Saving ₹[X]" and an "X" to remove
- If an invalid or expired code is entered: show inline error "Invalid coupon code" or "This coupon has expired"
- **Auto-best-coupon:** If only one coupon applies, auto-apply it and show "Best offer applied automatically"

#### 9.7 Order Notes

- Free-text field: "Add special instructions for your order"
- Examples: "No plastic bags please", "Extra bubble wrap for the eggs", "Call before delivery"
- Maximum 200 characters

#### 9.8 Upsell Shelf

- At the bottom of the cart, a horizontal shelf titled "You might also need"
- Shows products frequently co-purchased with items currently in cart
- Quick-add button on each card

#### 9.9 Price Breakdown

Shown at the bottom of the cart before the checkout button:

| Line Item | Description |
|---|---|
| Subtotal | Sum of all items at MRP |
| Item discounts | Total discount from product-level offers |
| Coupon discount | Discount from applied coupon code |
| Delivery fee | As configured by seller (₹0 if free delivery threshold met) |
| GST | Applicable tax (if seller is GST-registered) |
| **Total** | **Final payable amount** |

#### 9.10 Proceed to Checkout Button

- Sticky at the bottom of the cart sheet, always visible
- Shows total amount: "Proceed to Pay — ₹[Total]"
- Disabled if cart is empty or has unresolved OOS items

---

## 11. Feature F-08 — Checkout Flow

### What is this feature?

Checkout is the multi-step process where the customer confirms their delivery address, selects a delivery slot, and proceeds to payment. It is the final funnel step before an order is placed.

### Why must this be built?

Checkout is where the most friction exists. Every additional form field, page load, or unclear step increases drop-off. Zepto uses a single-page checkout where all steps are visible as collapsible sections — this pattern has proven to reduce checkout abandonment by making the entire process feel short. The checkout must be fast, clear, and trustworthy.

### Functional Specification

#### 10.1 Checkout Layout

- Single page with three collapsible sections: Address → Delivery Slot → Payment
- Each section is expanded in sequence; completed sections collapse and show a summary
- Order summary is always visible as a sticky panel (right side on desktop, sticky bottom strip on mobile)
- "Place Order" button is sticky at the bottom of the page, always visible

#### 10.2 Address Section

- Default address is pre-selected and shown
- Customer can:
  - Confirm the pre-selected address (tap to collapse and proceed)
  - Select a different saved address from a list
  - Add a new address inline without leaving the checkout page
- Delivery availability is validated in real time when an address is selected:
  - If seller delivers to this location: green checkmark "Delivery available"
  - If not: "This store does not deliver to this pincode. Please choose a different address."

#### 10.3 Delivery Slot Section

- Seller configures available delivery slots in their dashboard (e.g., Morning 9–12, Afternoon 12–5, Evening 5–9, Express 30-min)
- Customer sees only the slots the seller has made available for the selected date
- Slots with full capacity are shown greyed out as "Full"
- Selected slot shows estimated delivery window: "Delivery between 5 PM – 9 PM"
- **Important:** This is a seller-stated window, not a real-time ETA. No platform-managed tracking.
- Next day and future date scheduling: seller configures how many days in advance customers can schedule

#### 10.4 Payment Section

Covered in detail in Feature F-09.

#### 10.5 Order Summary (Sticky)

Shows throughout checkout:
- Item count and list (collapsible)
- Subtotal, discounts, delivery fee, GST, **Total**
- Applied coupon (if any)
- Edit cart link (returns to cart)

#### 10.6 Place Order

- Customer taps "Place Order — ₹[Total]"
- For prepaid orders: redirected to payment gateway
- For COD: order is placed immediately, confirmation screen shown
- On success: order confirmation screen with order ID, estimated delivery window, and a "Track order" button

#### 10.7 Checkout Trust Signals

- "100% Secure Payment" badge
- "Easy 7-day returns" badge
- Seller's return policy summary (linked)

---

## 12. Feature F-09 — Payment

### What is this feature?

The payment feature covers all the methods by which a customer can pay for their order, the payment processing flow, and the post-payment confirmation.

### Why must this be built?

Payment method availability is a direct conversion factor. In India, UPI is the dominant payment method (78% of digital transactions as of 2024). Customers who do not find their preferred payment method abandon checkout. COD remains important for first-time buyers who are not yet comfortable with online payments for a new store. Loyalty points redemption at checkout is a proven retention tool — customers who redeem points have a 40% higher repeat order rate.

### Functional Specification

#### 11.1 Available Payment Methods

**UPI:**
- Deep-link to GPay, PhonePe, Paytm, BHIM
- UPI ID manual entry field
- UPI QR code display (for desktop)
- Recommended method — shown first with a "Recommended" tag

**Saved Cards:**
- Masked card display (last 4 digits + card network logo)
- CVV entry field (CVV is never stored)
- Customer can delete saved cards

**Add New Card:**
- Card number, expiry, CVV, cardholder name
- Option to save card for future use (customer must explicitly check "Save this card")

**Net Banking:**
- Dropdown of major Indian banks
- Redirects to bank's net banking portal

**Cash on Delivery (COD):**
- Shown only if the seller has enabled COD for their store
- "Change required? Enter amount you'll be paying" — helps seller prepare change
- COD may have an extra handling charge (seller-configured)

**Loyalty Points:**
- "Use [X] points — save ₹[Y]" toggle
- Shows available points balance and equivalent rupee value
- Partial redemption — customer can use some points and pay the rest via another method
- Points redemption is applied as a line-item discount on the order total

**Store Wallet / Credits:**
- Balance shown if customer has store credits (from refunds or promotional credits)
- Applied automatically if customer chooses; can be toggled off

**Buy Now Pay Later (BNPL):**
- Simpl / LazyPay integration
- Available credit limit shown
- Customer redirected to BNPL provider for approval

#### 11.2 Payment Flow

1. Customer selects payment method
2. For UPI: deep-link opens the UPI app; customer approves payment in their UPI app; platform receives webhook from payment gateway confirming success
3. For cards/net banking: customer is redirected to payment gateway's hosted page; on success/failure, redirected back to Marketly
4. For COD: no payment gateway involved; order placed directly
5. On payment success: order status set to PLACED; confirmation screen shown
6. On payment failure: customer shown error message with reason ("Insufficient funds", "Bank server error") and options to retry with the same or a different method

#### 11.3 Payment Security

- All card data handled by the payment gateway (PCI-DSS compliant) — Marketly never stores raw card numbers
- 3D Secure / OTP authentication for cards as required by RBI mandate
- Payment gateway: Razorpay / PhonePe (to be confirmed during implementation)

#### 11.4 Payment Confirmation

After successful payment:
- Order confirmation screen with:
  - Order ID (large, prominent — customer screenshots this)
  - Items ordered
  - Total paid
  - Delivery address
  - Selected delivery slot / estimated window
  - "Track your order" button
  - "Continue shopping" button
- Confirmation also sent via:
  - Push notification
  - SMS
  - WhatsApp message
  - Email (if email address is on file)

---

## 13. Feature F-10 — Order Status & Tracking

### What is this feature?

Order tracking allows the customer to follow the progress of their order from the moment it is placed until it is delivered. Since delivery is handled by the seller, this feature is a **status update timeline** driven by seller actions — not a real-time GPS map.

### Why must this be built?

Order anxiety is one of the most common customer complaints in e-commerce. Customers who cannot see the status of their order contact support more, leave negative reviews, and cancel orders more frequently. A clear, real-time status timeline eliminates this anxiety. The key design principle here is: **sellers push status updates, the platform delivers them to the customer instantly.**

### Functional Specification

#### 12.1 Order Status Timeline

Displayed as a vertical step-by-step timeline. Each step shows a label, description, and timestamp when that status was reached.

| Step | Status | Label | Description |
|---|---|---|---|
| 1 | PLACED | Order Placed | Your order has been received |
| 2 | CONFIRMED | Order Confirmed | The store has accepted your order |
| 3 | PACKING | Being Packed | Your items are being packed |
| 4 | DISPATCHED | Out for Delivery | Order handed to delivery person |
| 5 | DELIVERED | Delivered | Order delivered successfully |

Completed steps are shown with a filled checkmark icon. Current step is highlighted. Future steps are shown in muted grey.

#### 12.2 Seller Notes on Status Updates

When the seller updates the order status, they can optionally attach a note. This note is shown to the customer under the relevant step.

Examples:
- On DISPATCHED: "Your order will be delivered by Ravi, +91 98xxx xxxxx"
- On PACKING: "One item (Bread) was out of stock. We've refunded ₹45 to your original payment method."

#### 12.3 Estimated Delivery Window

- Shown prominently at the top of the tracking page: "Expected delivery: 5 PM – 9 PM today"
- This is the slot the customer selected at checkout, not a real-time ETA
- If the seller updates the expected delivery (e.g., delay), the displayed window updates accordingly

#### 12.4 Contact Seller

- A "Contact Store" button is always visible on the order tracking page
- Tapping opens the in-app chat with the seller (see Feature F-22)
- Fallback: seller's phone number shown if chat is unavailable

#### 12.5 Cancel Order

- A "Cancel Order" button is available while the order is in PLACED or CONFIRMED status
- Once the order is in PACKING or later, cancellation is no longer available
- On cancellation:
  - If prepaid: refund initiated to original payment method (timeline: 3–7 business days depending on payment method)
  - If COD: no refund needed, order simply cancelled
  - Seller receives a cancellation notification

#### 12.6 Notification at Every Step

Every time the order status changes, the customer receives:
- Push notification (if app is installed)
- SMS (for PLACED, DISPATCHED, DELIVERED)
- WhatsApp message (if customer ordered via WhatsApp or has WhatsApp notifications enabled)

#### 12.7 Delivery Confirmation

When the seller marks an order as DELIVERED:
- Customer receives a notification: "Your order has been delivered!"
- Customer is prompted (via notification) to rate the order
- A "Report an issue" link is shown on the order detail page for 72 hours after delivery

---

## 14. Feature F-11 — Order History & Reorder

### What is this feature?

Order history is a complete log of all orders a customer has placed at a store. The reorder feature allows a customer to re-add all items from a past order to the cart with a single tap.

### Why must this be built?

Repeat ordering is the primary driver of customer lifetime value (LTV) in grocery and daily-needs commerce. The average Indian household orders staples (milk, bread, vegetables) on a near-daily or weekly basis. If reordering requires the customer to search for and add each item individually, many will simply place a smaller order or order less frequently. A one-tap reorder reduces friction to near zero. Reference: Blinkit reports that over 60% of orders from returning customers are reorders of past items.

### Functional Specification

#### 13.1 Order History List

- Accessible from the customer's account menu: "My Orders"
- Shows all past orders in reverse chronological order (most recent first)
- Grouped by time period: "This month", "Last month", "Older"
- Each order card shows:
  - Order ID
  - Order date and time
  - Items (first 2–3 product images as thumbnails + "& X more")
  - Total amount paid
  - Order status (current or final status with colour-coded badge)
  - "Reorder" button
  - "View details" link

#### 13.2 Order Detail Page

Full breakdown of a single order:
- Order ID, placed date and time
- Full item list (image, name, variant, quantity, unit price, line total)
- Delivery address
- Delivery slot / actual delivery time
- Payment method and amount
- Status timeline (same as tracking page)
- Contact Seller button
- Rate Order button (if delivered and not yet rated)
- Report Issue button (if delivered, within 72-hour window)
- Download Invoice button (PDF)

#### 13.3 Reorder in One Tap

- "Reorder" button on each order card in history
- On tap:
  - All items from the original order are added to the current cart
  - If any item is currently out of stock, it is skipped and a message shown: "2 items were out of stock and not added to cart. [View]"
  - If any item's price has changed since the original order, a notice is shown: "Note: prices may have changed since your last order"
  - Customer is taken directly to the cart to review before proceeding to checkout

#### 13.4 Downloadable Invoice

- Every order has a downloadable PDF invoice
- Invoice contains:
  - Store name, address, GSTIN (if registered)
  - Customer name and delivery address
  - Full item list with HSN codes, quantity, unit price, line total
  - Subtotal, CGST, SGST, total
  - Payment method and transaction reference
- PDF generated server-side, accessible from order detail page

---

## 15. Feature F-12 — Ratings & Reviews

### What is this feature?

The ratings and reviews feature allows customers who have purchased a product to publicly rate it (1–5 stars) and write a text review with optional photos. Reviews are displayed on the product detail page to inform future buyers.

### Why must this be built?

Product reviews are the most powerful trust signal in e-commerce. Studies consistently show that products with reviews convert at 3.5x the rate of products without reviews. In Indian quick-commerce, where customers cannot physically inspect a product before buying, reviews serve as the substitute for the "pick up and check" behaviour in physical stores. Reviews also provide sellers with direct product quality feedback. Without a review system, new products have no social proof and conversion remains low indefinitely.

### Functional Specification

#### 14.1 Review Eligibility

- Only customers who have a confirmed, delivered order containing the product can submit a review.
- One review per customer per product (can be edited, not duplicated).
- Review prompt is shown in the post-order screen and via push notification 2 hours after delivery.

#### 14.2 Review Submission Form

- **Star rating:** 1–5 star tap selector (required)
- **Review title:** Short headline, max 100 characters (optional)
- **Review body:** Free text, max 1000 characters (optional)
- **Photo upload:** Up to 5 photos from camera or gallery (optional)
- **Seller response invitation:** "Any feedback for the seller?" (private, not shown publicly)
- Submit button: "Post Review"

#### 14.3 Review Display on PDP

- Average rating shown prominently as a number + star graphic (e.g., "4.2 ★")
- Total review count ("318 reviews")
- Rating distribution bar chart (5★ — 60%, 4★ — 25%, 3★ — 10%, 2★ — 3%, 1★ — 2%)
- Filter tabs: All / 5★ / 4★ / 3★ / 2★ / 1★ / With Photos
- Sort: Most Recent / Most Helpful
- Individual review cards (as specified in Feature F-06 section 8.8)

#### 14.4 Review Moderation

- Seller can flag a review as inappropriate. Flagged reviews are queued for platform review.
- Platform admin can remove reviews that violate community guidelines (spam, offensive content).
- Sellers cannot delete reviews that are factually negative.

#### 14.5 Separate Seller Rating

In addition to per-product ratings, customers can rate the overall **seller experience**:
- Packaging quality (1–5 stars)
- Communication quality (1–5 stars)
- Delivery within stated window (1–5 stars) — this rates the seller, not the platform

These ratings are aggregated and shown on the store's page as the overall store rating.

---

## 16. Feature F-13 — Issue Reporting & Refunds

### What is this feature?

Issue reporting allows a customer to flag a problem with their delivered order — missing items, wrong items, damaged goods, or quality issues — and request a resolution (refund or replacement). The refund flow automates the resolution process for standard cases.

### Why must this be built?

No supply chain is perfect. Items will occasionally be missing, damaged, or incorrect. How a platform handles these issues determines customer trust more than the initial order experience. Zepto and Blinkit both offer instant self-serve refunds for eligible issues without requiring human review. Customers who receive a fast, hassle-free refund are more likely to order again than customers who never had an issue in the first place. A manual, slow refund process destroys trust and generates disproportionately negative reviews.

### Functional Specification

#### 15.1 Issue Reporting Entry Points

- From the order detail page: "Report an issue" button (visible for 72 hours post-delivery)
- From the post-delivery notification: "Something wrong? Tap here"
- From the order history list: overflow menu → "Report issue"

#### 15.2 Issue Reporting Flow

**Step 1 — Select affected items:**
- Show all items from the order as a checklist
- Customer selects the item(s) with issues
- Customer enters the quantity affected (if partial — e.g., 1 out of 2 packets was damaged)

**Step 2 — Select issue type (per selected item):**
- Item not delivered / Missing
- Wrong item delivered
- Damaged / broken packaging
- Expired / past best-before date
- Quality issue (spoiled, stale, not fresh)
- Quantity less than ordered

**Step 3 — Photo evidence (optional but encouraged):**
- Customer can upload up to 3 photos per affected item
- "Attach photo" prompt with camera and gallery options
- For "Item not delivered" type: photo not applicable, skip this step

**Step 4 — Select resolution:**
- Refund to original payment method
- Store credit / wallet (instant, no processing delay)
- Replacement in next order

**Step 5 — Review and submit:**
- Summary of selected items, issues, and requested resolution
- "Submit request" button

#### 15.3 Auto-Resolution Rules

For small-value orders and common issue types, the platform auto-approves without manual review:
- Order value below ₹500 AND issue is "Item not delivered" or "Wrong item" → instant auto-approval
- First 3 issues on an account → auto-approved
- Customers with a history of frequent refund requests → routed to manual review

#### 15.4 Resolution Communication

After submission:
- In-app confirmation: "Your request #XXX has been submitted"
- If auto-approved: "Refund of ₹[X] has been approved. It will be credited to [payment method] in 3–7 business days. Or get instant store credit: [Accept ₹X credit]"

#### 15.5 Refund Status Tracking

Accessible from order detail page → "View refund status":
- Requested → Under Review → Approved → Processed → Credited
- Each step shows timestamp
- For bank refunds: "Expected by [date]" shown

#### 15.6 Replacement Flow

If customer chose replacement:
- A new order is automatically created with the affected items
- Seller is notified of the replacement order
- Customer tracks the replacement order separately

---

## 17. Feature F-14 — Offers, Coupons & Deals

### What is this feature?

The offers feature is the central place where all discounts, coupon codes, and limited-time deals available to a customer are surfaced and managed. It covers product-level discounts, order-level coupons, bank-specific offers, and time-limited flash deals.

### Why must this be built?

Promotions are the primary driver of trial and repeat purchase in Indian retail. Customers actively look for deals before ordering — the Instamart and Zepto apps both have a dedicated "Offers" tab that is among the most-visited sections of the app. A well-executed offers system also allows sellers to drive specific behaviours (clear slow-moving stock, increase basket size, acquire new customers) without the platform needing to run platform-wide promotions.

### Functional Specification

#### 16.1 Offers Page

- Dedicated tab or navigation item: "Offers"
- Sections on this page:
  - **New User / First Order offers** — shown prominently at the top for new customers
  - **Active Flash Deals** — products currently on a time-limited discount with countdown
  - **Bank / Card Offers** — "10% off with HDFC credit card (max ₹100)"
  - **Free Delivery Coupons**
  - **Cashback Offers**
  - **Combo Deals** — product bundles at a special price

#### 16.2 Coupon Card

Each coupon shown as a card containing:
- Coupon code (bold, tappable to copy)
- Discount description ("Flat ₹50 off", "20% off up to ₹100")
- Minimum order value ("On orders above ₹299")
- Expiry date
- Applicable products or categories (if restricted)
- "Tap to apply" button — auto-applies the coupon to the current cart

#### 16.3 Product-Level Deal Types

Sellers can create the following deal types, all rendered on product cards and PDPs:

| Deal Type | Example |
|---|---|
| Percentage discount | "20% off" |
| Flat discount | "₹30 off" |
| Buy 1 Get 1 Free | "BOGO — add 2, pay for 1" |
| Buy X get Y% off | "Buy 2, get 10% off" |
| Combo bundle | "Bread + Butter = ₹89 (save ₹21)" |
| Free item above X | "Free Tata Salt on orders above ₹500" |
| Tiered quantity pricing | "1 for ₹50, 3 for ₹135, 5 for ₹200" |

#### 16.4 Flash Deals

- Seller sets a product's flash deal price + start time + end time
- During the active window: product card shows "FLASH" badge + countdown timer
- Flash deal shelf on home feed shows all active flash deals
- After the deal ends: product reverts to normal price, no badge shown
- Stock allocation for flash deals: seller can cap flash deal sales to a specific quantity

#### 16.5 Cashback Offers

- Seller creates a cashback offer: "Get 20% cashback up to ₹50 on your next order"
- Cashback is not an upfront discount — it is credited to the customer's store wallet after the order is delivered
- Customer sees pending cashback in their wallet with "Will be credited after delivery"
- Cashback expires 30 days after being credited (seller configures)

#### 16.6 Scratch Card

- After an order is delivered, customer receives a digital scratch card
- Customer swipes their finger across the card to "scratch" and reveal a reward
- Reward options: fixed discount coupon, percentage off coupon, free delivery on next order, loyalty points bonus
- Seller configures which orders trigger a scratch card and the reward pool
- Unclaimed scratch cards expire after 7 days

---

## 18. Feature F-15 — Loyalty Points & Tier System

### What is this feature?

The loyalty programme allows customers to earn points on every purchase and redeem them for discounts on future orders. The tier system automatically categorises customers into tiers (Bronze, Silver, Gold, Platinum) based on their total spending or order count, with each tier offering increasing benefits.

### Why must this be built?

Customer retention is significantly cheaper than customer acquisition. A loyalty programme creates a financial reason for customers to keep ordering from the same store rather than switching to a competitor. Tier systems add a gamification element — customers near a tier upgrade actively increase their order frequency and basket size to reach the next tier. In Indian retail, loyalty programmes are one of the most effective retention tools, particularly for grocery and daily-needs stores where the purchase frequency is high.

### Functional Specification

#### 17.1 Points Earning

- Seller configures the earn rate: e.g., "1 point for every ₹10 spent"
- Points are earned on the final order amount (after discounts, before GST)
- Points are credited to the customer's account after the order is delivered (not on placement — prevents fraud via cancelled orders)
- Bonus points events (seller-configured):
  - Welcome bonus: 50 points on first order
  - Birthday bonus: 2× points on birthday
  - Festive bonus: 2× or 3× points during configured periods
  - Review bonus: 10 points for writing a review

#### 17.2 Points Redemption

- Customer sees their points balance and rupee equivalent on the checkout payment page
- "Redeem [X] points = ₹[Y] off" toggle
- Partial redemption is allowed (customer can choose to use some points and save the rest)
- Minimum redemption: seller configures (e.g., minimum 100 points)
- Maximum redemption per order: seller configures (e.g., maximum 20% of order value)
- Redeemed points are deducted immediately on order placement; if order is cancelled, points are restored

#### 17.3 Tier System

| Tier | Threshold (example, seller-configurable) | Benefits |
|---|---|---|
| Bronze | 0 – 999 points lifetime | Base earn rate |
| Silver | 1,000 – 4,999 points lifetime | 1.25× earn rate |
| Gold | 5,000 – 14,999 points lifetime | 1.5× earn rate + priority support |
| Platinum | 15,000+ points lifetime | 2× earn rate + free delivery always + exclusive offers |

- Tier is based on **lifetime points earned** (not current balance)
- Tier never goes down once reached (or seller can configure annual reset)
- Tier badge shown on customer profile and on order confirmation

#### 17.4 Points Expiry

- Points expire after 12 months of inactivity (no orders placed)
- 30 days before expiry: push notification + email reminder: "Your [X] points expire on [date]"
- 7 days before expiry: second reminder

#### 17.5 Points History

- Accessible from "My Loyalty" in the account section
- Table showing:
  - Date
  - Description ("Order #1042", "Review bonus", "Birthday bonus")
  - Points earned or redeemed (+/−)
  - Running balance
- Current tier shown prominently with a progress bar to the next tier: "You need 340 more points to reach Gold"

---

## 19. Feature F-16 — Store Subscription / Pass

### What is this feature?

A store subscription (or "Pass") is a paid monthly or annual plan that a customer can buy for a specific store. In exchange for the subscription fee, the customer receives ongoing benefits such as free delivery on all orders, additional discounts, or bonus loyalty points.

### Why must this be built?

Subscription programmes lock in customer loyalty for an extended period. A customer who has paid ₹99 for a monthly pass has a strong financial incentive to place all their orders from that specific store. This directly increases order frequency and protects against competitor switching. Zepto's "Zepto Super" and Blinkit's "Blinkit Pass" are both subscription products that have meaningfully increased their customer retention metrics. For sellers on Marketly, this is a powerful tool to convert occasional buyers into regular customers.

### Functional Specification

#### 18.1 Subscription Plans

- Each seller can offer up to 3 subscription tiers (e.g., Basic, Standard, Premium)
- Each plan defines:
  - Plan name
  - Monthly price (e.g., ₹49 / ₹99 / ₹199)
  - Annual price (discounted, e.g., ₹499 / ₹999 / ₹1,999 — shown with "Save X%" badge)
  - Benefits list (seller configures; displayed as bullet points)
  - Free delivery: yes/no (and if yes, on all orders or above a minimum)
  - Extra discount percentage on all orders (e.g., 5% off everything)
  - Bonus loyalty points multiplier (e.g., 1.5× points on all orders)

#### 18.2 Subscription Purchase Flow

- Subscription offer prominently shown on the store home page and checkout page
- Customer taps "Get the Pass" → plan comparison screen → selects plan → pays via any available payment method
- Auto-renewal: subscription auto-renews at end of period; customer receives a 3-day advance reminder before renewal
- Customer can cancel auto-renewal anytime from their account (cancellation takes effect at end of current period)

#### 18.3 Savings Meter

- On the customer's subscription page: "Your pass has saved you ₹[X] this month"
- Calculated as: sum of delivery fees waived + sum of extra discounts applied on all orders since subscription start

#### 18.4 Pass Benefits Applied at Checkout

- If customer has an active pass, benefits are applied automatically at checkout — no code needed
- Free delivery waiver shown as a line item: "Pass benefit: Free delivery (−₹40)"
- Extra discount shown as: "Pass discount 5% (−₹28)"

---

## 20. Feature F-17 — Referral Program

### What is this feature?

The referral programme incentivises existing customers to invite new customers to the store, rewarding both the referrer and the new customer when the new customer places their first order.

### Why must this be built?

Referral marketing is the lowest cost-per-acquisition (CPA) channel available to a retail business. A referred customer has a built-in trust signal (a friend's recommendation), converts at a higher rate, and has a higher LTV than customers acquired through paid advertising. Making referrals easy and rewarding — as Zepto and Blinkit have done — creates a viral growth loop where the existing customer base drives new customer acquisition at near-zero cost.

### Functional Specification

#### 19.1 Referral Code Generation

- Every customer has a unique referral code (auto-generated at account creation, alphanumeric, 8 characters)
- Customer can also generate a referral link for direct sharing
- Accessible from account menu: "Refer & Earn"

#### 19.2 Sharing Options

On the "Refer & Earn" page:
- Share via WhatsApp (pre-populated message: "Order from [Store Name] and get ₹[X] off your first order! Use my code [CODE] or tap: [link]")
- Copy link button
- Share via native device share sheet (SMS, other apps)

#### 19.3 Reward Structure

- **Referrer reward:** credited when the referred friend places and completes their first order
- **Referee reward:** discount coupon on their first order (auto-applied at checkout when they sign up using the referral link/code)
- Reward amounts are configured by the seller

#### 19.4 Referral Dashboard

On the "Refer & Earn" page, customer can see:
- Their referral code and link
- Total friends referred
- Friends who signed up (count)
- Friends who placed their first order (count)
- Total rewards earned

#### 19.5 Anti-Fraud Rules

- One referral reward per referred phone number (a person cannot be referred twice)
- Self-referral is blocked (cannot use your own code)
- Referred customer must complete a genuine order (not cancelled) for reward to be triggered

---

## 21. Feature F-18 — Wishlist

### What is this feature?

The wishlist allows customers to save products they are interested in but are not ready to buy yet. Saved items can be moved to cart at a later time.

### Why must this be built?

Customers browsing a catalog frequently encounter products they want to remember for later — "I'll get this next week" or "I want to try this but not today." Without a wishlist, those products are lost. A wishlist also gives the platform data on which products have high intent but are not converting — useful for sellers to understand demand and for the platform to trigger targeted nudges ("That product you saved is now on sale!").

### Functional Specification

#### 20.1 Adding to Wishlist

- Heart icon on every product card and product detail page
- Tapping heart adds to wishlist; heart fills to indicate it is saved
- Tapping again removes from wishlist (with a brief "Removed from wishlist" toast)
- Requires login. If not logged in, prompt: "Login to save products to your wishlist"

#### 20.2 Wishlist Page

- Accessible from account menu and bottom navigation
- Shows all saved products as a grid
- Each product card shows current price (live, not price at time of saving)
- "Add to Cart" button on each card
- "Remove" button (swipe or tap X)
- If product is out of stock: greyed card with "Notify me when available" button

#### 20.3 Price Drop Notification

- If a wishlisted product's price drops, customer receives a push notification: "Good news! [Product] is now ₹[X] — down from ₹[Y]. Add to cart now."

#### 20.4 Back-in-Stock Notification

- Customer taps "Notify me" on an out-of-stock wishlisted product
- When the seller restocks that product, customer receives: "[Product] is back in stock! Don't miss it."

#### 20.5 Wishlist Sharing

- Customer can generate a shareable link to their wishlist
- Recipient can view the wishlist and add items to their own cart
- Useful for gifting: "Here's my wishlist, pick something!"

---

## 22. Feature F-19 — Subscribe & Save (Auto-Reorder)

### What is this feature?

Subscribe & Save allows a customer to set a product to be automatically ordered at a regular interval (daily, weekly, or monthly) at a discounted price. The order is placed automatically without any action from the customer.

### Why must this be built?

Indian households consume certain products on a fixed, predictable schedule — milk daily, bread weekly, cooking oil monthly. Currently, customers must remember to manually reorder these products. If they forget, they either run out or order from a competitor. Subscribe & Save eliminates this friction entirely — the product arrives automatically, the customer never runs out, and the seller gets guaranteed recurring revenue. BigBasket built its "BB Daily" product on exactly this model, with significant success in milk and bread subscription.

### Functional Specification

#### 21.1 Setting Up a Subscription

- "Subscribe & Save" option available on the product detail page (seller must have enabled this for the product)
- Customer selects:
  - Frequency: Daily / Every X days / Weekly / Bi-weekly / Monthly
  - Start date
  - Quantity per delivery
  - Delivery address (from saved addresses)
  - Preferred delivery slot
  - Payment method (saved card or UPI mandate for auto-debit)
- Subscription discount is shown prominently: "Subscribe and save 10% on every order"

#### 21.2 Subscription Management

From "My Subscriptions" in account:
- List of all active subscriptions across all stores
- For each subscription: product image, name, frequency, next order date, price per order
- Actions available:
  - **Edit:** change quantity, frequency, address, or slot
  - **Pause:** temporarily stop deliveries (specify resume date)
  - **Skip next:** skip the next scheduled order only
  - **Cancel:** cancel the subscription permanently (effective immediately or end of cycle)

#### 21.3 Automatic Order Placement

- System places the order automatically on the scheduled date
- Customer receives a notification 24 hours before: "Your subscription order for [Product] is scheduled for tomorrow. Tap to skip or edit."
- Order is placed at the customer's saved default payment method
- If payment fails: customer notified to update payment method; order is retried once after 2 hours
- If product is out of stock: customer notified, order skipped for that cycle, next delivery as scheduled

#### 21.4 UPI AutoPay / Mandate

- For seamless automatic payment, customer sets up a UPI AutoPay mandate (or saved card auto-charge)
- Mandate amount is set to cover the maximum possible subscription order value
- Customer approves the mandate once via their UPI app; subsequent charges are automatic

---

## 23. Feature F-20 — Personalisation & Smart Recommendations

### What is this feature?

Personalisation tailors the home feed, product recommendations, deal notifications, and reminders based on each individual customer's purchase history, browsing behaviour, dietary preferences, and order patterns.

### Why must this be built?

A generic, non-personalised experience is less relevant to every individual customer. Relevance drives engagement. Customers who see products they actually want to buy have a higher add-to-cart rate, higher order frequency, and higher average order value. Instamart's personalised home feed is a major competitive advantage — customers feel understood and discover products they would not have searched for on their own. Personalisation also enables highly targeted win-back and retention campaigns.

### Functional Specification

#### 22.1 Personalised Home Feed Sections

- The order of product shelves on the home feed is personalised per customer:
  - "Order again" section for returning customers (described in F-03)
  - Most relevant category grid tiles appear first (based on order history)
  - "Picked for you" shelf: products from categories the customer orders frequently, sorted by a relevance score

#### 22.2 Smart Reorder Reminder

- System analyses the customer's order history to detect reorder patterns
- If a customer orders Milk every 3 days and it has been 3 days since last order: push notification sent: "Running low on milk? Order now!"
- Notification deep-links directly to the product page with a pre-filled quantity

#### 22.3 Back-in-Stock Alerts

- Customer taps "Notify me" on any out-of-stock product
- When the seller updates stock to > 0: push notification immediately sent to all customers who requested alerts for that product
- Notification: "[Product] is back in stock at [Store]! Limited quantity available."

#### 22.4 Price Drop Alerts

- Customer can mark a product for price monitoring from the product detail page
- If the product's price decreases by any amount: push notification sent
- Notification includes: old price, new price, and a direct "Add to cart" deep link

#### 22.5 Dietary Preference Application

- Customer sets dietary preferences once in their profile: Vegetarian / Vegan / Gluten-free / Jain / Organic
- When active: product listing pages show a visual indicator on products that do not match the customer's preferences (e.g., dimmed, or a "Not veg" warning)
- Customer can toggle the filter on/off without changing their profile setting

#### 22.6 Allergy Warnings

- Customer can declare allergens in their profile: Nuts / Milk / Gluten / Soy / Eggs / Shellfish
- On any product detail page, if the product's ingredient list contains a declared allergen: prominent warning banner shown: "⚠️ Contains [allergen] — this product may not be suitable for you"

#### 22.7 Win-Back Campaign

- If a customer has not placed an order in 30 days: automated notification/message sent with a personalised coupon
- Message: "We miss you! Here's 20% off your next order — valid for 3 days"
- Coupon is automatically generated and linked to the customer's account

---

## 24. Feature F-21 — Notifications & Communication

### What is this feature?

The notifications feature covers all channels through which the platform communicates with the customer — push notifications, SMS, WhatsApp messages, email, and in-app notification centre. It also covers customer-controlled notification preferences.

### Why must this be built?

Notifications are the primary re-engagement mechanism. A customer who has placed one order and then forgotten about the store can be brought back via a well-timed flash deal notification. Order status notifications reduce customer anxiety and support tickets. Without notifications, the platform is silent — customers must actively remember to open the app, which dramatically reduces repeat order rate.

### Functional Specification

#### 23.1 Notification Channels

| Channel | Used For |
|---|---|
| Push notification | Order updates, flash deals, back-in-stock, price drops, subscription reminders, reorder nudges |
| SMS | OTP delivery, order placed confirmation, order dispatched, order delivered |
| WhatsApp | Order confirmation with item list, status updates, subscription reminders, win-back offers |
| Email | Order invoice, weekly deals digest, subscription renewal reminder, account security alerts |
| In-app notification centre | All of the above, stored persistently for reference |

#### 23.2 In-App Notification Centre

- Bell icon in the header with unread count badge
- On tap: full notification list, most recent first
- Categories / tabs: All / Orders / Offers / Account
- Each notification: icon, title, body, timestamp, read/unread state
- Tap notification: deep-links to the relevant page (order detail, product page, offers page)
- "Mark all as read" button
- Individual swipe-to-dismiss

#### 23.3 Notification Preferences

Customer can control, per channel and per category:
- Order updates: push / SMS / WhatsApp / email (each toggleable independently)
- Promotional offers: push / WhatsApp / email
- Price drop alerts: push / email
- Subscription reminders: push / SMS / WhatsApp
- Account alerts: email always on (non-disableable for security)

#### 23.4 Do Not Disturb

- Customer sets DND hours (e.g., 10:00 PM – 8:00 AM)
- During DND: promotional notifications are held and delivered at 8:00 AM
- During DND: transactional notifications (order updates, OTPs) are delivered immediately regardless

#### 23.5 WhatsApp Notification Opt-In

- During checkout, a checkbox: "Send order updates to my WhatsApp" (pre-checked, customer can uncheck)
- First WhatsApp message includes: "Reply STOP to stop receiving messages from [Store]"
- STOP reply immediately unsubscribes the customer from all WhatsApp messages from that store

---

## 25. Feature F-22 — Customer Support

### What is this feature?

Customer support gives customers a way to reach the seller (and the platform) when they have a question, a problem with their order, or a complaint that cannot be resolved through self-serve tools.

### Why must this be built?

Even with the best self-serve tools, some situations require human intervention. A customer who cannot reach support abandons the order, requests a chargeback, and leaves a 1-star review. Accessible, fast support is a trust signal in itself. The support architecture for Marketly has two tiers: seller-level support (for order-specific issues) and platform-level support (for account issues, seller disputes, or payment failures).

### Functional Specification

#### 24.1 In-App Chat with Seller

- Chat interface accessible from:
  - Order detail page: "Contact Store" button
  - Order tracking page: "Need help?" button
  - Store page: "Chat with store" in store info section
- Messages are delivered to the seller's dashboard in real time
- Customer sees online/offline status of the store
- If store is offline: "Store is currently offline. Your message will be delivered when they're back."
- Chat history is preserved for 90 days

#### 24.2 Order-Specific Help

- Issue reporting flow (Feature F-13) handles most post-delivery issues
- For pre-delivery issues (wrong address entered, want to add item to order): customer can message seller directly via chat
- If seller has not responded within 2 hours to an active order query: escalation prompt shown: "Seller hasn't responded. Escalate to Marketly support?"

#### 24.3 Self-Serve FAQ / Help Centre

- Searchable FAQ accessible from account menu → "Help"
- Articles cover: How to track an order, How to cancel an order, How to request a refund, How to change delivery address after placing order, How to contact a seller, How loyalty points work
- Articles are filterable by category

#### 24.4 Escalation to Platform Support

- If seller does not resolve an issue within 48 hours, customer can escalate to the Marketly platform support team
- Escalation form: describe issue, attach screenshots, select issue category
- Platform support responds via in-app chat within 24 hours (SLA)
- All escalated issues are tracked in a ticket system visible to the customer: "Your ticket #XXX — Status: Under Review"

#### 24.5 Proactive Support

- If an order is not updated to DISPATCHED within 2 hours of the seller's promised window: customer receives a notification: "Your order is taking longer than expected. [Contact store] or [Get help]"
- If a refund has been pending for more than 7 business days: customer receives a notification with an update and escalation option

---

## 26. Feature F-23 — WhatsApp Ordering (Text & Voice)

### What is this feature?

WhatsApp ordering allows customers to place orders with a store entirely through a WhatsApp conversation — either by typing their order in natural language or by sending a voice message. The order is processed through the exact same order pipeline as app orders, appearing identically on the seller's dashboard.

### Why must this be built?

WhatsApp is the most widely used app in India, with over 500 million active users. A significant portion of the Indian retail customer base — especially older demographics, less tech-savvy users, and customers in Tier 2 and Tier 3 cities — is more comfortable messaging a shopkeeper on WhatsApp than navigating an app. Many small retailers already receive orders via WhatsApp messages today, but these are unstructured, error-prone, and impossible to scale. By building a structured WhatsApp ordering channel, Marketly dramatically expands its addressable customer base and captures orders that would otherwise happen outside the platform entirely. Voice message support additionally covers customers who find typing in English (or even their regional language) difficult.

### Functional Specification

#### 25.1 Architecture Overview

The WhatsApp ordering system is a new `whatsapp-service` microservice that acts as a **channel adapter** — it translates WhatsApp conversations into the same API calls that the web/mobile app makes. The order-service, product-service, and customer-service have zero awareness that an order originated from WhatsApp.

```
Customer WhatsApp Message
         ↓
WhatsApp Business API (Meta Cloud API)
         ↓
whatsapp-service (new microservice)
         ↓ Speech-to-Text (if voice note)
         ↓ NLP / Intent extraction
         ↓ Product matching via product-service API
         ↓ Cart construction via order-service API
         ↓ Payment link generation (Razorpay/PhonePe)
         ↓ POST /api/v1/orders  ← same endpoint as app
         ↓
    order-service
         ↓
  Seller Dashboard (identical to any other order)
```

#### 25.2 Customer Identity & Account Linking

**First-time WhatsApp order:**
1. Customer sends any message to the store's WhatsApp number
2. System responds: "Welcome to [Store Name]! To place an order, let's verify your number. An OTP has been sent to [number]."
3. Customer replies with the OTP
4. If number already has a Marketly account: linked automatically
5. If number is new: account created automatically; customer asked for their name
6. After verification: all saved addresses and past orders are accessible in subsequent conversations

**Returning customer:**
- Phone number already linked → conversation begins immediately at item collection

#### 25.3 Text Order Flow

**Step 1 — Item Collection:**

Customer message: *"Hi, I want 2 packets of Amul butter, 1kg basmati rice and a dozen eggs"*

System response:
```
Got it! Here's what I found:
1. Amul Butter 500g × 2 = ₹220
2. Basmati Rice 1kg × 1 = ₹180
3. Eggs (12 pcs) × 1 = ₹89

Anything else to add? Or reply DONE to proceed.
```

**Step 2 — Address Confirmation:**
```
Where should we deliver?
1. Home — 12, MG Road, Koramangala
2. Work — 4th Floor, Prestige Tower

Reply 1, 2 or type a new address.
```

**Step 3 — Delivery Slot:**
```
Pick a delivery slot:
1. Morning (9 AM – 12 PM)
2. Afternoon (12 PM – 5 PM)
3. Evening (5 PM – 9 PM)

Reply 1, 2 or 3.
```

**Step 4 — Order Summary & Confirmation:**
```
Here's your order summary:
• Amul Butter 500g × 2 = ₹220
• Basmati Rice 1kg × 1 = ₹180
• Eggs (12 pcs) × 1 = ₹89

Subtotal: ₹489
Delivery: Free (above ₹299)
Total: ₹489

Delivering to: 12, MG Road, Koramangala
Slot: Evening (5 PM – 9 PM)

Reply PAY to confirm and pay
Reply EDIT to change items
Reply CANCEL to cancel
```

**Step 5 — Payment:**
```
Pay ₹489 securely here:
[Payment link — valid for 30 minutes]

Your order will be confirmed once payment is received.
```

**Step 6 — Confirmation:**
```
✅ Order #1042 confirmed!
Your order is being processed.

We'll send you updates here.
Reply STATUS anytime to check your order.
```

#### 25.4 Voice Message Flow

1. Customer sends a voice note (e.g., in Hindi: "Bhaiya ek litre doodh, do bread aur ek packet biscuit dena")
2. `whatsapp-service` downloads the audio file from WhatsApp media servers
3. Audio sent to Speech-to-Text engine (Google Cloud Speech-to-Text / OpenAI Whisper)
4. Transcription returned as text
5. Language detected (Hindi, Tamil, Telugu, Kannada, English, etc.)
6. NLP engine extracts: items, quantities, units, any delivery preferences
7. System responds in the **same language** the customer used:

```
[Hindi response]
समझ गया! यहाँ आपका order है:
• Milk 1L × 1 = ₹68
• Bread × 2 = ₹90
• Biscuits × 1 = ₹35

कुल: ₹193

क्या यह सही है?
PAY भेजें confirm करने के लिए
EDIT भेजें बदलने के लिए
```

8. If voice note quality is too low for reliable transcription: "I couldn't clearly hear your voice note. Could you please type your order?"

#### 25.5 Ambiguity Resolution

When the customer's message matches multiple products:

```
Customer: "1 bread"

System: Which bread would you like?
1. Modern White Bread 400g — ₹45
2. Britannia Brown Bread 400g — ₹52
3. Local Bakery Fresh Loaf — ₹40

Reply 1, 2 or 3
```

When quantity is unclear:
- "bread" with no quantity → ask: "How many packs of bread?"

#### 25.6 Returning Customer Shortcuts

```
Customer: "Same as last time"

System: Your last order was:
• Amul Butter × 2
• Basmati Rice 1kg × 1
• Eggs 12 pcs × 1
Total: ₹489

Reply YES to reorder the same
Reply EDIT to change something
```

#### 25.7 Catalog Browsing via WhatsApp

```
Customer: "What do you have in dairy?"

System: Dairy products available:
🥛 Milk 1L — ₹68
🧈 Amul Butter 500g — ₹110
🧀 Amul Cheese Slice 200g — ₹145
🥚 Eggs 12 pcs — ₹89

Just tell me what you want and how many!
```

#### 25.8 Global Commands (Available at Any Point in Conversation)

| Command | Action |
|---|---|
| STATUS | Show status of most recent active order |
| CANCEL | Cancel the current order being built |
| ORDERS | List last 5 orders |
| HELP | Show list of available commands |
| STOP | Unsubscribe from all WhatsApp messages from this store |
| MENU | Show all product categories |

#### 25.9 Conversation State Machine

Each customer-store conversation is tracked in Redis with an expiry of 30 minutes of inactivity:

| State | Description |
|---|---|
| IDLE | No active conversation |
| COLLECTING_ITEMS | Customer is adding items; waiting for DONE |
| ADDRESS_CONFIRMATION | Showing saved addresses for selection |
| SLOT_SELECTION | Waiting for delivery slot choice |
| SUMMARY_SHOWN | Order summary sent, awaiting PAY/EDIT/CANCEL |
| AWAITING_PAYMENT | Payment link sent, waiting for payment webhook |
| ORDER_CONFIRMED | Order placed; back to IDLE |

On 30-minute timeout: "Your cart has been saved. Send us a message anytime to continue."

#### 25.10 Order Status Updates via WhatsApp

Every status change the seller makes triggers a WhatsApp message to the customer:

```
Order Confirmed:
✅ [Store Name] confirmed your order #1042!
Expected delivery: Evening 5–9 PM

Being Packed:
📦 Your order is being packed!

Out for Delivery:
🛵 Your order is out for delivery!
Expected: within the next 45–60 mins.
Questions? Reply here to reach the store.

Delivered:
✅ Your order has been delivered!
Hope you enjoyed your order.

Rate your experience: [link]
Something wrong? Reply ISSUE
```

#### 25.11 Issue Handling via WhatsApp

```
Customer: ISSUE

System: Sorry to hear that! What's the problem?
1. Item not delivered
2. Wrong item received
3. Damaged/broken item
4. Quantity was less
5. Other

Reply 1–5
```

Flow continues to collect affected items and request resolution, mirroring the in-app issue reporting flow.

#### 25.12 Order Source Tracking

- A `source` field is added to the order entity: `APP` | `WHATSAPP`
- Seller dashboard shows a small "WhatsApp" channel badge on orders placed via WhatsApp
- Analytics tracks order volume by channel

#### 25.13 Edge Cases

| Scenario | Handling |
|---|---|
| Product not found | "Sorry, I couldn't find [item]. Did you mean [closest match]? Or browse: MENU" |
| Item out of stock | "Sorry, [item] is out of stock. Reply NOTIFY to be alerted when it's back." |
| Below minimum order | "Your cart total is ₹89. Minimum order is ₹150. Add ₹61 more!" |
| Store closed | "FreshMart is currently closed. Opens at 9 AM. Want to pre-order for tomorrow morning?" |
| Payment link expired | "Your payment link expired. Reply PAY for a new link." |
| Gibberish / unrecognised | Route to "I didn't understand that. Type HELP to see what I can do, or type your order (e.g., '2 milk 1 bread')" |
| Multiple languages mid-conversation | Detect and respond in the language the customer most recently used |

---

## 27. Feature F-24 — Progressive Web App (PWA)

### What is this feature?

The Progressive Web App feature allows customers to install the Marketly storefront on their mobile device's home screen like a native app, and provides app-like experiences including offline browsing, push notifications, and fast load times — without requiring a download from the App Store or Play Store.

### Why must this be built?

App Store and Play Store apps have a significant friction barrier — users must search, download (often over slow mobile data), and install before they can place their first order. A PWA eliminates this entirely. Customers can start shopping immediately from a browser link (e.g., shared on WhatsApp) and optionally install to home screen in one tap. PWAs load faster, use less data, and work partially offline. For a multi-tenant platform where each seller has their own URL, maintaining separate native apps per seller is not scalable — a PWA is the pragmatic solution.

### Functional Specification

#### 26.1 Home Screen Installation

- On the customer's 3rd+ visit (or after first order), show an install prompt: "Add [Store Name] to your home screen for faster access"
- Tapping "Install" triggers the native browser install flow (Chrome's Add to Home Screen)
- Installed icon uses the store's logo/emoji and name
- Installation prompt can be dismissed; does not re-appear for 7 days

#### 26.2 Push Notifications

- Customers are prompted to enable push notifications after their first order
- Notification permission prompt is shown with context: "Enable notifications to get order updates"
- Push notifications work even when the browser tab is closed (once installed as PWA or permission granted in browser)
- All notifications listed in Feature F-21 are delivered via Web Push

#### 26.3 Offline Browsing

- Service worker caches the last-loaded product catalog for each store
- If the customer is offline: show cached product listing with a banner "You're offline — showing last updated catalog"
- Cart additions while offline are queued and synced when connection is restored
- Checkout is not available offline (requires live stock validation)

#### 26.4 App Shell & Fast Loading

- App shell (navigation, header, bottom bar) is cached and loads instantly
- Product images are lazy-loaded and cached after first load
- Time to interactive target: under 2 seconds on a 4G connection

---

## 28. Feature F-25 — Accessibility & Inclusivity

### What is this feature?

Accessibility features ensure that customers with visual, motor, or cognitive impairments can use the platform effectively. Inclusivity features ensure the platform serves customers across language backgrounds and levels of digital literacy.

### Why must this be built?

India has over 70 million people with some form of disability. Accessibility is both an ethical obligation and a legal requirement (Rights of Persons with Disabilities Act, 2016). Beyond legal compliance, accessibility improvements — such as larger tap targets, high-contrast modes, and screen reader support — also benefit the broader customer base: older users, customers using low-quality screens, and customers with temporary impairments (e.g., a broken hand). Inclusive design is good product design.

### Functional Specification

#### 27.1 Screen Reader Support

- All interactive elements have meaningful ARIA labels (e.g., "Add Amul Butter to cart", not just "Add")
- Images have descriptive alt text
- Form fields have associated label elements
- Navigation landmarks defined (header, main, nav, footer)

#### 27.2 High Contrast / Dark Mode

- Dark mode toggle in account settings (system preference also respected automatically)
- High contrast mode for customers with visual impairments

#### 27.3 Font Size Control

- Text size setting in account preferences: Small / Medium / Large / Extra Large
- Applies across the entire application

#### 27.4 Tap Target Sizes

- All interactive elements (buttons, links, toggles) are minimum 44×44px
- No elements placed so close together that accidental taps occur

#### 27.5 Multi-Language Support

- Interface available in English + regional languages: Hindi, Tamil, Telugu, Kannada, Bengali, Marathi
- Language selected in account settings or auto-detected from device locale
- All product names, descriptions, and seller content remain as entered by the seller (not auto-translated)
- UI chrome (navigation labels, button text, system messages) fully translated

#### 27.6 Low-Data Mode

- Enabled from account settings
- In low-data mode: product images replaced with placeholder emoji/colour (as already implemented in some parts of the current codebase)
- Video autoplay disabled
- Image carousels load only the visible image

---

## 29. Delivery Scope Clarification

This section formally documents the delivery constraint and its implications for the customer experience.

### Platform Responsibility

The Marketly platform is responsible for:
- Capturing the customer's delivery address accurately
- Presenting the seller's available delivery slots to the customer
- Communicating the customer's selected address and slot to the seller in the order payload
- Relaying seller-pushed status updates to the customer via notifications
- Providing a channel for the customer to contact the seller if delivery is delayed or problematic

### Seller Responsibility

Each individual seller is fully responsible for:
- Employing and managing their own delivery staff
- Physically packing and dispatching orders
- Updating order statuses as the order progresses (via their seller dashboard)
- Communicating delivery agent details to the customer (optional, via status update notes)
- Meeting the delivery window they offered at checkout

### What This Means for the Customer Experience

- There is **no live GPS delivery map** — the platform cannot show the delivery agent's live location because the platform does not know who the delivery agent is
- The **estimated delivery time** shown at checkout is the seller's stated window, not a platform-calculated real-time ETA
- If a delivery is delayed, the customer's recourse is to **contact the seller** via in-app chat, not a platform-managed delivery operations team
- **Proof of delivery** (if required) is the seller's responsibility to collect and communicate

---

## 30. Feature Priority Matrix

This matrix helps the engineering team sequence development. Priority is based on impact on order conversion, customer retention, and business criticality.

### P0 — Must have for launch (no launch without these)

| Feature ID | Feature Name |
|---|---|
| F-01 | Onboarding & Account Registration |
| F-02 | Customer Profile & Address Management |
| F-03 | Store Discovery & Home Feed |
| F-04 | Product Search & Filtering |
| F-05 | Product Listing Page |
| F-06 | Product Detail Page |
| F-07 | Cart Management |
| F-08 | Checkout Flow |
| F-09 | Payment |
| F-10 | Order Status & Tracking |
| F-11 | Order History & Reorder |
| F-21 | Notifications & Communication |

### P1 — High priority (build in first release cycle post-launch)

| Feature ID | Feature Name |
|---|---|
| F-12 | Ratings & Reviews |
| F-13 | Issue Reporting & Refunds |
| F-14 | Offers, Coupons & Deals |
| F-15 | Loyalty Points & Tier System |
| F-18 | Wishlist |
| F-22 | Customer Support |
| F-24 | Progressive Web App (PWA) |

### P2 — Medium priority (second release cycle)

| Feature ID | Feature Name |
|---|---|
| F-16 | Store Subscription / Pass |
| F-17 | Referral Program |
| F-19 | Subscribe & Save |
| F-20 | Personalisation & Smart Recommendations |
| F-23 | WhatsApp Ordering (Text & Voice) |

### P3 — Nice to have (third release cycle / future roadmap)

| Feature ID | Feature Name |
|---|---|
| F-25 | Accessibility & Inclusivity |
| Scratch cards, gamification | Post-delivery engagement |
| Gift orders | Social commerce |
| Social sharing & collections | Viral growth |

---

*End of Document*

**Document Version:** 1.0  
**Last Updated:** June 2026  
**Next Review:** September 2026
