"use client";

import { AlertTriangle, Gauge, Radio, Waves } from "lucide-react";
import type { KpiSnapshot } from "@/lib/types";
import { cn } from "@/lib/cn";

interface KpiCardsProps {
  kpi: KpiSnapshot;
}

export function KpiCards({ kpi }: KpiCardsProps) {
  const cards = [
    {
      label: "Total Events Ingested",
      value: kpi.totalEvents.toLocaleString("en-US"),
      meta: `${kpi.eventsTrendPct >= 0 ? "+" : ""}${kpi.eventsTrendPct.toFixed(1)}% vs prior window`,
      icon: Radio,
      tone: "text-accent",
    },
    {
      label: "System Latency p95 / p99",
      value: `${kpi.latencyP95Ms}ms / ${kpi.latencyP99Ms}ms`,
      meta: "Rolling 5-minute window",
      icon: Gauge,
      tone: "text-accent2",
    },
    {
      label: "HTTP Error Rate",
      value: `${kpi.errorRatePct.toFixed(1)}%`,
      meta: "4xx + 5xx over 1 minute",
      icon: AlertTriangle,
      tone: kpi.errorRatePct > 5 ? "text-danger" : "text-warn",
    },
    {
      label: "ML Data Drift Status",
      value: kpi.datasetDrift ? "DRIFT DETECTED" : "NO DRIFT",
      meta: `share=${(kpi.driftShare * 100).toFixed(0)}%`,
      icon: Waves,
      tone: kpi.datasetDrift ? "text-danger" : "text-ok",
      badge: true,
    },
  ] as const;

  return (
    <section className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
      {cards.map((card, index) => {
        const Icon = card.icon;
        return (
          <article
            key={card.label}
            className="animate-fadeUp rounded-xl border border-line bg-panel/90 p-4 shadow-glow"
            style={{ animationDelay: `${index * 70}ms` }}
          >
            <div className="mb-3 flex items-center justify-between gap-3">
              <p className="text-xs uppercase tracking-[0.14em] text-mute">
                {card.label}
              </p>
              <Icon className={cn("h-4 w-4", card.tone)} aria-hidden />
            </div>
            {"badge" in card && card.badge ? (
              <span
                className={cn(
                  "inline-flex rounded-md border px-2.5 py-1 font-mono text-sm font-medium",
                  kpi.datasetDrift
                    ? "border-danger/40 bg-danger/10 text-danger"
                    : "border-ok/40 bg-ok/10 text-ok",
                )}
              >
                {card.value}
              </span>
            ) : (
              <p className="font-mono text-2xl text-ink">{card.value}</p>
            )}
            <p className="mt-2 text-xs text-mute">{card.meta}</p>
          </article>
        );
      })}
    </section>
  );
}
