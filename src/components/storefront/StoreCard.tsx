import { Link } from "@tanstack/react-router";
import { motion } from "framer-motion";
import { Clock, Star } from "lucide-react";
import type { Tenant } from "@/lib/types";

export function StoreCard({ tenant }: { tenant: Tenant }) {
  return (
    <motion.div whileHover={{ y: -4 }} transition={{ type: "spring", stiffness: 300, damping: 22 }}>
      <Link
        to="/s/$tenant"
        params={{ tenant: tenant.slug }}
        className="group block overflow-hidden rounded-3xl border border-border/60 bg-card shadow-card transition-shadow hover:shadow-pop"
      >
        <div
          className="relative grid aspect-[16/9] place-items-center text-6xl"
          style={{ background: tenant.bannerGradient }}
          aria-hidden
        >
          <span className="drop-shadow-sm">{tenant.logoEmoji}</span>
          <span className="absolute left-4 top-4 rounded-full bg-background/85 px-2.5 py-1 text-[11px] font-medium backdrop-blur">
            {tenant.category}
          </span>
        </div>
        <div className="p-4">
          <div className="flex items-start justify-between gap-2">
            <div className="min-w-0">
              <h3 className="truncate text-[15px] font-semibold">{tenant.name}</h3>
              <p className="mt-0.5 line-clamp-1 text-[12.5px] text-muted-foreground">{tenant.tagline}</p>
            </div>
            <div className="shrink-0 rounded-full bg-success/10 px-2 py-1 text-[11px] font-semibold text-success">
              <Star className="mr-0.5 inline h-3 w-3 fill-current" />
              {tenant.rating}
            </div>
          </div>
          <div className="mt-3 flex items-center gap-3 text-[11.5px] text-muted-foreground">
            <span className="inline-flex items-center gap-1">
              <Clock className="h-3.5 w-3.5" />
              {tenant.deliveryMinutes} min
            </span>
            <span>·</span>
            <span>Min ₹{tenant.minOrder}</span>
            <span className="ml-auto text-primary">Shop now →</span>
          </div>
        </div>
      </Link>
    </motion.div>
  );
}
