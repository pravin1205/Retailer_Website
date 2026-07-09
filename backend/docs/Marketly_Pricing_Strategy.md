# Marketly — Pricing Strategy & Business Model
## SaaS Subscription Pricing Document

**Version:** 1.0
**Date:** June 2026
**Prepared by:** Product & Business Team — Marketly
**Document Type:** Pricing Strategy & Cost Analysis
**Audience:** Founders, Business Team, Investor Reference

---

## Table of Contents

1. [Document Purpose](#1-document-purpose)
2. [Platform Overview & Subscription Models](#2-platform-overview--subscription-models)
3. [What Each Plan Includes](#3-what-each-plan-includes)
4. [Infrastructure Cost Analysis](#4-infrastructure-cost-analysis)
5. [Variable Cost Per Seller Per Month](#5-variable-cost-per-seller-per-month)
6. [WhatsApp Feature — Detailed Cost Breakdown](#6-whatsapp-feature--detailed-cost-breakdown)
7. [Freelancer & Development Cost](#7-freelancer--development-cost)
8. [Total Investment to Launch](#8-total-investment-to-launch)
9. [Monthly Operating Cost After Launch](#9-monthly-operating-cost-after-launch)
10. [Final Subscription Pricing](#10-final-subscription-pricing)
11. [Revenue & Profitability Projections](#11-revenue--profitability-projections)
12. [Break-Even Analysis](#12-break-even-analysis)
13. [Free Trial Model](#13-free-trial-model)
14. [Investment Recovery Timeline](#14-investment-recovery-timeline)
15. [Additional Revenue Levers](#15-additional-revenue-levers)
16. [Market Benchmarking](#16-market-benchmarking)
17. [Pricing Summary Card](#17-pricing-summary-card)

---

## 1. Document Purpose

This document establishes the complete pricing strategy for the Marketly SaaS platform. It covers every cost component — infrastructure, APIs, third-party services, freelancer development cost, and ongoing operational cost — and derives a subscription price that is:

- **Above the floor** — covers all costs at a defined seller count
- **Below the ceiling** — competitive with comparable tools in the Indian market
- **Profitable** — recovers the total development investment within 18 months of launch
- **Clear** — leaves no ambiguity about what each plan includes, what costs are passed to sellers, and how the free trial works

### Key Business Constraints

The following constraints were defined by the founders and are incorporated into every calculation in this document:

| Constraint | Detail |
|---|---|
| **Seller pays everything** | Sellers bear 100% of subscription cost. No cost is passed to customers. |
| **Delivery not platform-managed** | Delivery is handled entirely by each seller. The platform has no delivery cost. |
| **Two subscription tiers** | Basic (app ordering only) and Premium (app + WhatsApp ordering) |
| **WhatsApp ordering — Premium only** | WhatsApp text and voice ordering is exclusively a Premium feature |
| **Seller's own WhatsApp number** | Under Premium, sellers connect their existing WhatsApp Business number (Option B) |

---

## 2. Platform Overview & Subscription Models

Marketly is a **multi-tenant retail commerce SaaS platform**. Each seller gets their own isolated storefront, product catalog, order management dashboard, and customer communication channel.

### Two Subscription Models

```
┌─────────────────────────────────────┐
│              BASIC                  │
│                                     │
│  Customers order exclusively        │
│  through the Marketly web app       │
│  and Progressive Web App (PWA)      │
│                                     │
│  Seller manages everything          │
│  through their store dashboard      │
└─────────────────────────────────────┘

┌─────────────────────────────────────┐
│             PREMIUM                 │
│                                     │
│  Everything in Basic, PLUS:         │
│                                     │
│  Customers can also order via       │
│  the seller's WhatsApp number       │
│  using text or voice messages       │
│  in any Indian language             │
│                                     │
│  Orders from WhatsApp arrive in     │
│  the seller dashboard in the exact  │
│  same format as app orders          │
└─────────────────────────────────────┘
```

### Important Clarification — WhatsApp Number Ownership

Under the Premium plan, each seller connects **their own existing WhatsApp Business number** to the Marketly platform. This means:

- Customers message the **seller's own WhatsApp number** — not a Marketly number
- The seller's brand identity is fully preserved
- Marketly operates invisibly in the background as the processing engine
- Each seller's customers only interact with that seller's store — there is complete isolation between stores
- The platform connects to Meta's WhatsApp Business API under the seller's own WhatsApp Business Account

---

## 3. What Each Plan Includes

### Basic Plan — Complete Feature Set

| Feature Category | Features Included |
|---|---|
| **Storefront** | Unique store URL, custom branding (logo, colours, banner), PWA installable app |
| **Product Management** | Unlimited products, categories, variants, inventory tracking, low-stock alerts |
| **Customer Ordering** | Full app-based shopping experience — browse, search, cart, checkout |
| **Order Management** | Order dashboard, status updates (Placed → Confirmed → Packing → Dispatched → Delivered), order history |
| **Payments** | UPI, cards, net banking, Cash on Delivery — via Razorpay |
| **Customer Features** | Loyalty points & tiers (Bronze/Silver/Gold/Platinum), coupons & discounts, wishlist, ratings & reviews |
| **Notifications** | Push notifications, email, SMS for order updates |
| **Analytics** | Revenue dashboard, daily/weekly/monthly KPIs, top products, customer insights |
| **Customer Support** | In-app chat between customer and seller |
| **Admin** | Super admin panel for platform-level oversight |

### Premium Plan — Additional Features Over Basic

| Feature Category | Features Included |
|---|---|
| **WhatsApp Ordering** | Customers can place orders by sending a WhatsApp text message to the seller's own number |
| **Voice Ordering** | Customers can send a voice note in Hindi, Tamil, Telugu, Kannada, or English — the platform transcribes and processes it |
| **WhatsApp Status Updates** | Every order status change is automatically sent as a WhatsApp message to the customer |
| **Multilingual Support** | Platform detects the customer's language and responds in the same language |
| **Reorder via WhatsApp** | Customer can type "Same as last time" and reorder their previous order |
| **WhatsApp Issue Reporting** | Customers can report issues directly in the WhatsApp chat |
| **Priority Support** | Faster response SLA for Premium sellers from the Marketly support team |

### What is NOT Included in Either Plan

| Item | Why Not Included |
|---|---|
| Delivery management | Delivery is handled entirely by each seller using their own staff |
| Live delivery GPS tracking | Not applicable — platform does not manage delivery agents |
| Delivery route optimisation | Seller's responsibility |
| Platform-owned customer base | Each seller's customers belong to that seller only — not shared across the platform |

---

## 4. Infrastructure Cost Analysis

The platform's infrastructure is **shared across all sellers** on a single deployment. As more sellers join, the per-seller infrastructure cost decreases significantly. This is the fundamental economics of a multi-tenant SaaS platform.

### Fixed Monthly Infrastructure Cost (Entire Platform)

| Component | Description | Monthly Cost |
|---|---|---|
| Application Server | Runs all 8 microservices (4 vCPU, 16GB RAM VM or equivalent cloud instance) | ₹6,000 – ₹10,000 |
| PostgreSQL Database | Primary database with 8 schemas, automated backups, point-in-time recovery | ₹3,000 – ₹5,000 |
| Redis | Session management, OTP storage, rate limiting, caching, conversation state | ₹1,000 – ₹2,000 |
| Apache Kafka | Event streaming between microservices | ₹1,500 – ₹3,000 |
| Email (SMTP) | Transactional email via Mailgun or AWS SES | ₹500 – ₹1,000 |
| Object Storage | Product images, invoices, voice note files | ₹500 – ₹1,500 |
| Monitoring | Prometheus + Grafana (self-hosted, no additional cost) | ₹0 |
| SSL Certificates | Let's Encrypt (free) or paid wildcard cert | ₹0 – ₹500 |
| CDN | Content delivery for product images | ₹500 – ₹1,000 |
| **Total Fixed Monthly** | | **₹13,000 – ₹24,000** |

**Working number used in calculations: ₹18,000/month**

### Per-Seller Infrastructure Cost at Different Scales

This table shows how the infrastructure cost per seller drops as the platform grows. This is why SaaS businesses become significantly more profitable at scale.

| Active Sellers | Total Infra Cost/Month | Cost Per Seller |
|---|---|---|
| 10 sellers | ₹13,000 – ₹24,000 | ₹1,300 – ₹2,400 |
| 25 sellers | ₹15,000 – ₹26,000 | ₹600 – ₹1,040 |
| 50 sellers | ₹18,000 – ₹30,000 | ₹360 – ₹600 |
| 100 sellers | ₹22,000 – ₹36,000 | ₹220 – ₹360 |
| 200 sellers | ₹30,000 – ₹46,000 | ₹150 – ₹230 |

> **Key insight:** At 10 sellers, infrastructure alone costs ₹1,300 – ₹2,400 per seller. At 100 sellers, it drops to ₹220 – ₹360 per seller. This is why the pricing must be set to survive the early low-seller phase while being fair at scale.

---

## 5. Variable Cost Per Seller Per Month

These costs are tied to actual seller activity and scale with usage.

### Basic Plan Seller — Variable Costs

A typical Basic seller with 200–400 orders per month:

| Component | What It Covers | Monthly Cost |
|---|---|---|
| SMS (OTP for customer logins) | ~150–300 OTP SMSes per month at ₹0.20–₹0.30 each | ₹30 – ₹90 |
| Email notifications | Order confirmations, invoices, status updates | ₹10 – ₹30 |
| Object storage (product images) | ~50–200 product images, invoices | ₹50 – ₹150 |
| Payment gateway | UPI = ₹0 (NPCI mandate). Cards/NB = 0.4%–1.5% (paid by customer at time of transaction, not by platform) | ₹0 |
| **Total Variable — Basic Seller** | | **₹90 – ₹270/month** |

### Premium Plan Seller — Variable Costs (Additional)

Additional costs on top of Basic for WhatsApp ordering:

| Component | What It Covers | Monthly Cost |
|---|---|---|
| WhatsApp conversations (Meta) | ~100–300 WhatsApp ordering conversations/month at ₹0.35–₹1.17 each | ₹35 – ₹350 |
| Speech-to-Text (voice orders) | ~20–30% of orders use voice; Google STT / Whisper at ₹0.50/minute | ₹20 – ₹100 |
| NLP — GPT-4o-mini | Intent extraction and order parsing at ~₹0.08 per order | ₹10 – ₹40 |
| **Total Variable — Premium (Additional)** | | **₹65 – ₹490/month** |

### Total Variable Cost Summary

| Plan | Monthly Orders | Total Variable Cost/Month |
|---|---|---|
| Basic | 200 orders | ₹90 – ₹180 |
| Basic | 400 orders | ₹150 – ₹270 |
| Premium | 200 app + 100 WhatsApp orders | ₹200 – ₹500 |
| Premium | 300 app + 200 WhatsApp orders | ₹300 – ₹760 |

---

## 6. WhatsApp Feature — Detailed Cost Breakdown

This section provides complete transparency on every cost component of the WhatsApp ordering feature, which is exclusively available on the Premium plan.

### 6.1 Meta WhatsApp Business API

Meta charges per **conversation** (a 24-hour messaging window), not per individual message. Within one conversation window, unlimited messages can be exchanged at no additional cost.

**Conversation types and pricing (India tier):**

| Conversation Type | When It Occurs | Cost Per Conversation |
|---|---|---|
| User-initiated | Customer sends the first message | ~₹0.35 |
| Business-initiated | Platform sends first message (proactive update) | ~₹0.82 |

**Free allowance:** Meta provides **1,000 free user-initiated conversations per month per WhatsApp Business Account**. Since each seller connects their own WhatsApp Business Account under Option B, each seller independently benefits from this 1,000-conversation free allowance.

**Cost per order lifecycle:**
- Customer messages first → user-initiated conversation (₹0.35)
- All order confirmation, packing, dispatch, delivery messages typically occur within 24 hours → covered by the same conversation window
- **Effective cost per WhatsApp order: ₹0.35 – ₹1.17**

### 6.2 Speech-to-Text (Voice Message Processing)

| Provider | Cost | Notes |
|---|---|---|
| Google Cloud Speech-to-Text | Free first 60 min/month; ₹0.50/min thereafter | Excellent Hindi, Tamil, Telugu support |
| OpenAI Whisper API | ₹0.50/min | Best multilingual accuracy for Indian languages |

A typical voice order note is 10–20 seconds. Cost per voice message: ₹0.08 – ₹0.17

**Monthly cost at scale:**
- 100 voice messages/month: ₹8 – ₹17
- 500 voice messages/month: ₹40 – ₹85

### 6.3 NLP — Order Intent Extraction (OpenAI GPT-4o-mini)

The NLP layer converts natural language ("bhaiya ek litre doodh aur do bread dena") into a structured order. GPT-4o-mini is used because it offers the best cost-to-accuracy ratio for Indian language understanding.

| Metric | Cost |
|---|---|
| Input tokens | $0.15 per 1 million tokens |
| Output tokens | $0.60 per 1 million tokens |
| **Cost per WhatsApp order** | **₹0.06 – ₹0.10** |

### 6.4 Payment Links (Razorpay)

Razorpay charges on successful transactions, not on payment link creation.

| Payment Method | Fee |
|---|---|
| UPI | ₹0 (free — NPCI mandate) |
| Debit/Credit Card | 0.40% – 1.50% |
| Cash on Delivery | ₹0 |

Since the majority of Indian retail transactions are UPI or COD, the effective payment gateway cost per WhatsApp order is **₹0 for most orders**.

### 6.5 One-Time Setup Cost for WhatsApp Feature

| Item | Cost |
|---|---|
| Meta Business Manager account | ₹0 (free) |
| Meta Developer App creation | ₹0 (free) |
| WhatsApp Business API access | ₹0 (pay only per conversation) |
| Razorpay account setup | ₹0 (no monthly fees) |
| Google Cloud / OpenAI API account | ₹0 (pay per use) |
| **Total one-time setup** | **₹0** |

### 6.6 Total WhatsApp Cost Per Order Summary

| Cost Component | Per Order |
|---|---|
| WhatsApp conversation (Meta) | ₹0.35 – ₹1.17 |
| Speech-to-Text (25% of orders use voice) | ₹0.02 – ₹0.04 |
| NLP processing | ₹0.06 – ₹0.10 |
| Payment gateway (UPI/COD) | ₹0 |
| **Total platform cost per WhatsApp order** | **₹0.43 – ₹1.31** |

> **Context:** If an average WhatsApp order is ₹400 – ₹600, the platform's cost per order is 0.1% – 0.3% of the order value. This is extremely low and is fully absorbed in the Premium subscription margin.

---

## 7. Freelancer & Development Cost

### Team Composition

The Marketly platform is being built by a team of 6 freelancers on fixed monthly retainers:

| Role | Count | Monthly Retainer (Est.) |
|---|---|---|
| Full-stack developer (senior) | 2 | ₹15,000 – ₹20,000 each |
| Full-stack developer (mid-level) | 2 | ₹10,000 – ₹15,000 each |
| Designer / UI-UX | 1 | ₹8,000 – ₹12,000 |
| DevOps / QA | 1 | ₹8,000 – ₹12,000 |
| **Total monthly team cost** | **6 people** | **₹66,000 – ₹94,000** |

**Working number used in all calculations: ₹80,000/month**

### Current Build Status Assessment

An independent analysis of the codebase was conducted. The following table shows the completion status of each platform component:

| Component | Status | % Complete |
|---|---|---|
| API Gateway (security, routing, JWT) | Complete | 95% |
| Identity Service (auth, OTP, RBAC) | Complete | 95% |
| Tenant Service (store management, KYC) | Complete | 90% |
| Product Service (catalog, inventory) | Complete | 90% |
| Customer Service (profiles, loyalty) | Complete | 85% |
| Order Service (cart, checkout, orders) | Complete | 85% |
| Notification Service (email, in-app, WebSocket) | Mostly complete — SMS stub only | 80% |
| Analytics Service (KPIs, dashboards) | Complete | 75% |
| Customer Storefront (app UI) | Mostly complete | 80% |
| Seller Dashboard (admin UI) | Mostly complete | 75% |
| Super Admin Panel | Mostly complete | 70% |
| Auth & Seller Onboarding (7-step wizard) | Complete | 90% |
| CI/CD Pipeline (Jenkins + Docker) | Complete | 85% |
| SMS Gateway Integration | Not integrated (stub only) | 10% |
| Payment Gateway (Razorpay) | Not integrated | 15% |
| Ratings & Reviews | Mock data only | 20% |
| Kubernetes deployment | Not built | 0% |
| WhatsApp Ordering Service | Not started | 0% |
| **Overall Platform** | | **~72–75%** |

### Remaining Work to Complete

**Phase 1 — Basic Plan Launch (remaining ~25%):**

| Work Item | Estimated Effort |
|---|---|
| Real SMS gateway integration (MSG91 / Twilio) | 3–4 days |
| Razorpay payment gateway integration | 5–7 days |
| Product ratings & reviews (backend + frontend) | 1.5 weeks |
| Issue reporting & refund flow | 1 week |
| End-to-end testing and bug resolution | 2 weeks |
| Production deployment + domain/SSL setup | 1 week |
| **Total for Basic launch** | **~6–7 weeks (~1.75 months)** |

**Phase 2 — Premium Plan Launch (WhatsApp, additional work):**

| Work Item | Estimated Effort |
|---|---|
| `whatsapp-service` microservice (Spring Boot) | 3–4 weeks |
| Meta Embedded Signup flow (seller dashboard UI) | 1 week |
| Speech-to-Text integration (Google / Whisper) | 1.5 weeks |
| NLP order parsing (GPT-4o-mini) | 1.5 weeks |
| Razorpay payment link in WhatsApp flow | 3 days |
| End-to-end WhatsApp testing | 1 week |
| **Total for Premium launch** | **~8–9 weeks (~2 months)** |

---

## 8. Total Investment to Launch

### Development Cost Breakdown

| Phase | Duration | Monthly Team Cost | Total |
|---|---|---|---|
| Work already completed (~75% of platform) | ~5 months (estimated) | ₹80,000 | ₹4,00,000 |
| Basic plan — remaining work | ~2 months | ₹80,000 | ₹1,60,000 |
| Premium plan — additional work | ~2 months | ₹80,000 | ₹1,60,000 |
| **Total development cost** | **~9 months** | | **₹7,20,000** |

### All-In Investment

| Cost Item | Total |
|---|---|
| Freelancer development cost (9 months) | ₹7,20,000 |
| Infrastructure during development (9 months × ₹5,000) | ₹45,000 |
| API costs during testing and development | ₹15,000 |
| Miscellaneous (domains, tools, Meta setup, Razorpay KYC) | ₹20,000 |
| **Total all-in investment to full launch** | **₹8,00,000** |

> **This ₹8,00,000 is the total capital required to reach a fully launched product with both Basic and Premium plans live.** Of this, approximately ₹4,00,000 is already spent. The remaining ₹4,00,000 covers the next 4 months of work.

---

## 9. Monthly Operating Cost After Launch

Once the platform is live, the team shifts from building to maintaining, supporting, and improving. Assume 4 freelancers are retained post-launch (2 full-stack for features and bug fixes, 1 DevOps/QA, 1 designer on reduced engagement):

### Post-Launch Monthly Cost Structure

| Cost Item | Monthly Cost |
|---|---|
| Retained freelancers (4 people, maintenance mode) | ₹45,000 – ₹55,000 |
| Infrastructure (servers, DB, Redis, Kafka) | ₹15,000 – ₹25,000 |
| SMS gateway (scales with active sellers) | ₹2,000 – ₹6,000 |
| Email service | ₹500 – ₹1,500 |
| Storage (product images, invoices) | ₹500 – ₹2,000 |
| WhatsApp/NLP/STT (scales with Premium sellers) | ₹5,000 – ₹15,000 |
| Support tools, monitoring, miscellaneous | ₹2,000 – ₹5,000 |
| **Total monthly operating cost** | **₹70,000 – ₹1,09,500** |

**Working number: ₹85,000/month** (used in all profitability calculations)

---

## 10. Final Subscription Pricing

### Pricing Derivation Logic

The pricing was determined by working from three inputs simultaneously:

1. **Floor (cost-based):** What is the minimum revenue needed to cover operating costs at a viable seller count?
2. **Ceiling (market-based):** What do comparable Indian SaaS tools charge?
3. **Recovery (investment-based):** At what price does the ₹8,00,000 investment recover within 18 months?

### The Prices

---

### BASIC PLAN — ₹1,999 per month

**Annual option: ₹19,990 per year** *(equivalent to ₹1,666/month — saving ₹2,398 vs monthly)*

**What is included:**

- Your own store URL (`marketly.com/s/your-store-name`)
- Unlimited product listings with variants, categories, and inventory management
- Full customer shopping experience — browse, search, cart, checkout
- Order management dashboard with complete order lifecycle management
- UPI, card, net banking, and Cash on Delivery payments via Razorpay
- Customer loyalty points and tier system (Bronze → Silver → Gold → Platinum)
- Coupons, discount codes, flash deals, and combo offers
- Product ratings and customer reviews
- Push notifications, SMS, and email for all order events
- Real-time analytics dashboard — revenue, orders, top products, customer metrics
- In-app customer-to-seller chat support
- Progressive Web App (PWA) — customers can install the store as an app without the App Store or Play Store
- Seller branding — custom logo, colours, banner
- Super admin oversight and KYC approval

**What is NOT included in Basic:**
- WhatsApp ordering (text or voice)
- WhatsApp status update messages to customers

---

### PREMIUM PLAN — ₹3,999 per month

**Annual option: ₹39,990 per year** *(equivalent to ₹3,333/month — saving ₹5,998 vs monthly)*

**What is included:**

- **Everything in the Basic plan**, plus:
- WhatsApp ordering via the seller's own WhatsApp number
- Customers can place orders by sending a text message in any Indian language
- Customers can send a voice note — the platform transcribes and processes it automatically
- The system understands Hindi, Tamil, Telugu, Kannada, and English
- Orders from WhatsApp appear in the seller's dashboard in identical format to app orders
- Automatic WhatsApp messages to customers at every order status change
- "Same as last time" reorder shortcut via WhatsApp
- Issue reporting and refund requests via WhatsApp
- Priority customer support response from the Marketly team

**Key clarification on WhatsApp number:**
Under the Premium plan, each seller connects their own existing WhatsApp Business number to Marketly. Customers message the seller's own number — not a Marketly-owned number. The seller's customer relationship is fully preserved and Marketly operates invisibly in the background.

---

### Annual Pricing — Why It Matters

| Plan | Monthly Billing | Annual Billing | Annual Saving |
|---|---|---|---|
| Basic | ₹1,999/month | ₹19,990/year (₹1,666/month) | ₹2,398 |
| Premium | ₹3,999/month | ₹39,990/year (₹3,333/month) | ₹5,998 |

Annual plans benefit the seller (saves money) and benefit Marketly (upfront cash improves cash flow and reduces churn risk). Sellers who pay annually are committed for 12 months and are significantly less likely to cancel.

**Recommendation:** Always offer annual billing as the primary option on the pricing page. Show the monthly equivalent price prominently ("₹1,666/month, billed annually").

---

## 11. Revenue & Profitability Projections

### Scenario 1 — Conservative (Mostly Basic Sellers)

| Month | Basic Sellers | Premium Sellers | Monthly Revenue | Monthly Cost | Monthly Profit/Loss |
|---|---|---|---|---|---|
| Month 1–2 (launch) | 5 | 0 | ₹9,995 | ₹85,000 | **−₹75,005** |
| Month 3 | 12 | 2 | ₹31,986 | ₹87,000 | **−₹55,014** |
| Month 4 | 20 | 4 | ₹55,974 | ₹89,000 | **−₹33,026** |
| Month 6 | 35 | 8 | ₹101,957 | ₹93,000 | **+₹8,957** |
| Month 9 | 55 | 15 | ₹169,920 | ₹98,000 | **+₹71,920** |
| Month 12 | 75 | 20 | ₹229,875 | ₹1,05,000 | **+₹1,24,875** |

### Scenario 2 — Realistic (Balanced Mix)

| Month | Basic Sellers | Premium Sellers | Monthly Revenue | Monthly Cost | Monthly Profit/Loss |
|---|---|---|---|---|---|
| Month 1–2 | 5 | 0 | ₹9,995 | ₹85,000 | **−₹75,005** |
| Month 3 | 15 | 5 | ₹49,970 | ₹88,000 | **−₹38,030** |
| Month 5 | 30 | 12 | ₹107,838 | ₹92,000 | **+₹15,838** |
| Month 7 | 45 | 18 | ₹161,808 | ₹96,000 | **+₹65,808** |
| Month 10 | 60 | 25 | ₹219,825 | ₹1,02,000 | **+₹1,17,825** |
| Month 12 | 75 | 30 | ₹269,775 | ₹1,08,000 | **+₹1,61,775** |

### Scenario 3 — Optimistic (Strong Premium Adoption)

| Month | Basic Sellers | Premium Sellers | Monthly Revenue | Monthly Cost | Monthly Profit/Loss |
|---|---|---|---|---|---|
| Month 3 | 15 | 8 | ₹61,967 | ₹89,000 | **−₹27,033** |
| Month 5 | 30 | 18 | ₹131,952 | ₹93,000 | **+₹38,952** |
| Month 8 | 50 | 30 | ₹219,870 | ₹1,00,000 | **+₹1,19,870** |
| Month 12 | 70 | 45 | ₹319,755 | ₹1,12,000 | **+₹2,07,755** |

---

## 12. Break-Even Analysis

Break-even is the point where monthly revenue equals monthly operating cost (₹85,000).

### Break-Even Points

| Seller Mix | Monthly Revenue | vs ₹85,000 Cost |
|---|---|---|
| 43 Basic, 0 Premium | ₹85,957 | ✅ Break-even |
| 30 Basic, 10 Premium | ₹99,960 | ✅ Break-even |
| 20 Basic, 17 Premium | ₹107,903 | ✅ Break-even |
| 42 Basic, 0 Premium | ₹83,958 | ❌ Just below |

**The critical milestone is 40–43 total sellers.** At any mix that totals approximately 43 sellers (or fewer if more are on Premium), the platform covers its monthly operating cost entirely.

### Why This Number is Achievable

43 sellers across all Indian cities is a very small number for a platform that serves independent retailers. A single city (Bangalore, Mumbai, Delhi, Chennai, Hyderabad) has tens of thousands of independent grocery stores, bakeries, pharmacies, and organic shops that currently have no digital ordering capability and no WhatsApp integration.

The target of 43 sellers to break even is a conservative, achievable milestone within 6–8 months of launch.

---

## 13. Free Trial Model

### The Trial Offer

**Both plans offer a 30-day free trial.**

- No credit card required to start the trial
- Full access to all features of the chosen plan during the trial
- Trial applies to the first subscription only (not available on renewal or plan switches)

### Why a Free Trial is Essential

A seller who has not yet seen orders come through the platform has no evidence of value. Asking them to pay ₹1,999 or ₹3,999 upfront without proof is a friction point that kills conversion.

A 30-day trial solves this decisively:

1. The seller sets up their store (products, branding, categories)
2. They share their store URL or WhatsApp number with existing customers
3. Within the first week, orders start coming in
4. The seller now has evidence: real orders, real revenue, real customers using the platform
5. At day 30, paying ₹1,999 or ₹3,999/month is an easy decision — they are already generating value

**Industry data:** SaaS platforms that offer free trials convert 60–80% of trial users to paid subscribers if the trial results in at least one successful outcome (in this case, one real order).

### Trial Rules — What Is and Is Not Allowed

| Rule | Detail |
|---|---|
| Duration | 30 days from account activation |
| Credit card | Not required to start |
| Features | Full plan features — no artificial restrictions |
| Eligible accounts | First-time sellers only. One trial per phone number / business registration |
| After trial | Subscription must be activated to continue. Store goes into read-only mode if subscription is not activated within 7 days after trial ends |
| Grace period | 7-day grace period after trial ends — store remains accessible but order placement is disabled. Seller is notified on day 25, 28, and 30 |
| WhatsApp during trial | Premium trial sellers can connect their WhatsApp number and test the ordering flow with real customers during the trial period |

### Trial Communication Timeline

| Day | Action |
|---|---|
| Day 1 | Welcome email + setup guide sent to seller |
| Day 7 | "Your trial is going well — here's how to get your first orders" email |
| Day 15 | Mid-trial check-in — "You've had X orders so far" |
| Day 25 | "5 days left in your trial — activate your subscription" warning |
| Day 28 | Second activation reminder |
| Day 30 | Trial ends — grace period begins |
| Day 37 | Grace period ends — store goes read-only |

### What Happens After Trial if Seller Does NOT Subscribe

- Store is put into **read-only mode**: seller can see their data but customers cannot place new orders
- Store URL shows a "This store is temporarily unavailable" message to customers
- Seller's data is retained for 90 days — they can reactivate at any time by subscribing
- After 90 days of inactivity: account is marked for deletion with a 30-day final notice

### No Hidden Costs During Trial

There are zero costs to the seller during the 30-day trial. Marketly absorbs:
- All infrastructure costs
- All SMS costs (OTP for customer logins)
- All email notification costs
- All WhatsApp conversation costs (Premium trial)
- All NLP and STT costs (Premium trial)

This is intentional — the trial period is a customer acquisition cost for Marketly, not a revenue opportunity.

---

## 14. Investment Recovery Timeline

### Total Investment: ₹8,00,000

This must be recovered through subscription profits after covering monthly operating costs.

### Recovery Calculation (Realistic Scenario)

| Period | Cumulative Profit | Investment Recovered |
|---|---|---|
| Months 1–3 (loss phase) | −₹1,50,000 | 0% |
| Months 4–6 | −₹60,000 net | 0% |
| Month 7 | +₹65,808/month | Beginning recovery |
| Month 9 | +₹95,000/month | ₹2,00,000 recovered |
| Month 12 | +₹1,61,775/month | ₹5,50,000 recovered |
| Month 15 | +₹2,00,000/month | ₹8,00,000+ recovered ✅ |

**Full investment recovery: Month 15 (15 months after launch)**

### What "Recovery" Means in Practice

Recovery does not mean the business stops generating losses first. The timeline works as follows:

- **Months 1–4:** Monthly losses (₹30,000 – ₹75,000/month) as seller base grows. These losses are funded from the ₹4,00,000 remaining development budget which covers both remaining development AND early operational losses.
- **Month 5–6:** Break-even zone. Monthly losses reduce to near zero.
- **Month 7 onwards:** Monthly profits begin. These profits are used to recover the ₹8,00,000 sunk investment.
- **Month 15:** Full recovery achieved. Every rupee from month 16 onwards is retained profit.

### Sensitivity: What If Growth is Slower?

| Growth Scenario | Break-Even Month | Full Recovery Month |
|---|---|---|
| Slow (5 sellers/month acquisition) | Month 9 | Month 22 |
| Realistic (8–10 sellers/month) | Month 6 | Month 15 |
| Fast (15 sellers/month) | Month 4 | Month 10 |

Even in the slowest scenario, the investment is fully recovered within 22 months — well within a standard business planning horizon.

---

## 15. Additional Revenue Levers

These are optional revenue streams that can be activated after the core subscription model is stable. None of these are required to reach profitability — they are upside opportunities.

| Revenue Stream | Description | When to Activate |
|---|---|---|
| **Transaction fee** | 0.5% on monthly GMV above ₹1,00,000 per seller | After 100 sellers |
| **One-time setup fee** | ₹2,999 onboarding fee (waived for first 50 sellers as a launch promotion) | At launch (optional) |
| **Extra staff accounts** | Base plan includes 2 staff accounts. Additional accounts: ₹299/account/month | Month 3 onwards |
| **Custom domain mapping** | Seller maps their own domain (janesbakery.com) to their Marketly store: ₹499/month add-on | Month 6 onwards |
| **SMS top-up credits** | Sell SMS credits above the included monthly bundle | Month 6 onwards |
| **Promoted listings** | Sellers pay to feature products at the top of category pages | Month 9 onwards |

**Policy on additional revenue:** None of these levers should be activated before the seller base reaches 50 and before the core product is stable. Introducing add-on costs too early damages trust and increases churn.

---

## 16. Market Benchmarking

### Comparable Indian SaaS Tools — What They Charge

| Platform | What It Does | Monthly Price |
|---|---|---|
| **Dukaan** | Basic online store builder | ₹999 – ₹2,499/month |
| **Instamojo** | Online store + payments | ₹999 – ₹2,499/month |
| **Zoho Commerce** | E-commerce + inventory | ₹1,099 – ₹2,299/month |
| **Shopify India** | Full e-commerce suite | ₹1,994 – ₹7,447/month |
| **Interakt** | WhatsApp Business API tool (only) | ₹2,499 – ₹6,999/month |
| **Wati** | WhatsApp Business API tool (only) | ₹2,999 – ₹7,999/month |
| **AiSensy** | WhatsApp marketing + ordering | ₹2,499 – ₹5,999/month |
| **Petpooja / UrbanPiper** | Restaurant-specific commerce | ₹3,000 – ₹8,000/month |

### Marketly's Value vs Market

**Basic plan at ₹1,999:**
Dukaan and Instamojo charge ₹999 – ₹2,499 for a significantly simpler product with no multi-tenancy, no loyalty system, no analytics, and no real-time notifications. Marketly at ₹1,999 is priced at the midpoint of this range while offering a far superior product.

**Premium plan at ₹3,999:**
WhatsApp-only tools (Interakt, Wati, AiSensy) charge ₹2,499 – ₹7,999/month — for WhatsApp alone, with no store, no inventory, no ordering system. Marketly Premium at ₹3,999 gives sellers a complete commerce platform AND WhatsApp ordering. The value comparison is decisive.

A seller who wants both a store platform AND WhatsApp ordering would spend:
- Dukaan (₹1,999) + Interakt (₹2,499) = **₹4,498/month** for two separate tools
- Marketly Premium = **₹3,999/month** — everything integrated, one bill, one platform

---

## 17. Pricing Summary Card

This is the clean, simple version of the pricing — suitable for the website, sales deck, and seller onboarding material.

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

  MARKETLY SUBSCRIPTION PLANS

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

  BASIC                          PREMIUM
  ─────────────────────────────  ─────────────────────────
  ₹1,999/month                   ₹3,999/month
  ₹19,990/year (save ₹2,398)     ₹39,990/year (save ₹5,998)

  ✅ Your own store URL           ✅ Everything in Basic
  ✅ Unlimited products           ✅ WhatsApp ordering
  ✅ Order management                (text + voice)
  ✅ Customer app + PWA           ✅ Hindi/Tamil/Telugu/
  ✅ UPI + Card + COD                Kannada support
  ✅ Loyalty points & tiers       ✅ WhatsApp order updates
  ✅ Coupons & discounts          ✅ Connect your own
  ✅ Analytics dashboard             WhatsApp number
  ✅ Push + SMS + email alerts    ✅ Priority support
  ✅ Ratings & reviews
  ✅ Customer support chat
  ✅ Seller dashboard

  ⭐ 30-day free trial            ⭐ 30-day free trial
     No credit card required         No credit card required

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

  DELIVERY: Handled entirely by seller — not by platform
  PAYMENTS: Seller receives payments via their Razorpay account
  SUPPORT:  In-app chat + email support for all sellers

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

---

## Key Numbers at a Glance

| Metric | Value |
|---|---|
| Total investment to full launch | ₹8,00,000 |
| Already invested (estimated) | ₹4,00,000 |
| Remaining investment needed | ₹4,00,000 |
| Monthly operating cost post-launch | ₹85,000 |
| Basic plan price | ₹1,999/month |
| Premium plan price | ₹3,999/month |
| Break-even seller count | ~43 sellers (any mix) |
| Expected break-even month | Month 5–6 after launch |
| Full investment recovery | Month 15 after launch |
| Free trial duration | 30 days, no credit card |
| WhatsApp cost per order (platform cost) | ₹0.43 – ₹1.31 |
| Target profit at 75 Basic + 30 Premium | ₹1,61,775/month |

---

*End of Document*

**Document Version:** 1.0
**Prepared:** June 2026
**Next Review:** September 2026 (or when seller count reaches 25)
