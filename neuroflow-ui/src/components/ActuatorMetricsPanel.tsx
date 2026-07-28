"use client";

import { useCallback, useEffect, useState } from "react";
import { Activity, Cpu, Gauge, Radio } from "lucide-react";
import { fetchRuntimeMetrics } from "@/lib/api";
import type { RuntimeMetrics } from "@/lib/types";
import { cn } from "@/lib/cn";

function formatUptime(sec: number): string {
  const total = Math.max(0, Math.floor(sec));
  const h = Math.floor(total / 3600);
  const m = Math.floor((total % 3600) / 60);
  const s = total % 60;
  if (h > 0) return `${h}h ${m}m`;
  if (m > 0) return `${m}m ${s}s`;
  return `${s}s`;
}

export function ActuatorMetricsPanel() {
  const [metrics, setMetrics] = useState<RuntimeMetrics | null>(null);
  const [mock, setMock] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    try {
      const result = await fetchRuntimeMetrics();
      setMetrics(result.data);
      setMock(result.mock);
      setError(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Runtime metrics unavailable");
    }
  }, []);

  useEffect(() => {
    void refresh();
    const timer = window.setInterval(() => void refresh(), 5000);
    return () => window.clearInterval(timer);
  }, [refresh]);

  const jvm = metrics?.jvm;
  const http = metrics?.http;
  const nf = metrics?.neuroflow;

  const cards = [
    {
      label: "Heap",
      value: jvm ? `${jvm.heapUsedMb.toFixed(0)} / ${jvm.heapMaxMb.toFixed(0)} MB` : "—",
      meta: jvm ? `${jvm.heapUsagePct.toFixed(1)}% used` : "Actuator JVM",
      icon: Gauge,
      tone: (jvm?.heapUsagePct ?? 0) > 85 ? "text-danger" : "text-accent",
    },
    {
      label: "CPU process",
      value: jvm ? `${jvm.cpuProcessPct.toFixed(1)}%` : "—",
      meta: jvm ? `system ${jvm.cpuSystemPct.toFixed(1)}% · threads ${jvm.threadsLive}` : "Micrometer",
      icon: Cpu,
      tone: "text-accent2",
    },
    {
      label: "HTTP requests",
      value: http ? http.requestCount.toLocaleString("en-US") : "—",
      meta: http
        ? `mean ${http.meanLatencyMs.toFixed(1)}ms · max ${http.maxLatencyMs.toFixed(1)}ms`
        : "http.server.requests",
      icon: Activity,
      tone: "text-ok",
    },
    {
      label: "NeuroFlow counters",
      value: nf
        ? `${nf.simulationEmitted.toLocaleString("en-US")} sim`
        : "—",
      meta: nf
        ? `alerts ${nf.alertsPublished} · drift ${nf.driftChecks}/${nf.driftDetected}`
        : "custom meters",
      icon: Radio,
      tone: "text-warn",
    },
  ] as const;

  return (
    <section className="rounded-xl border border-line bg-panel/90 p-4 shadow-glow">
      <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
        <div>
          <h2 className="text-sm font-medium text-ink">Actuator / Prometheus</h2>
          <p className="mt-1 text-xs text-mute">
            Micrometer runtime snapshot
            {jvm ? ` · uptime ${formatUptime(jvm.uptimeSec)}` : ""}
            {mock ? " · fallback" : ""}
          </p>
        </div>
        <div className="flex flex-wrap gap-2 text-[11px] font-mono">
          <a
            href="http://localhost:8080/actuator/prometheus"
            target="_blank"
            rel="noreferrer"
            className="rounded-md border border-line bg-elevated px-2.5 py-1 text-mute transition hover:border-accent/40 hover:text-accent"
          >
            /actuator/prometheus
          </a>
          <a
            href="http://localhost:9090"
            target="_blank"
            rel="noreferrer"
            className="rounded-md border border-line bg-elevated px-2.5 py-1 text-mute transition hover:border-accent/40 hover:text-accent"
          >
            Prometheus UI :9090
          </a>
        </div>
      </div>

      {error && <p className="mb-3 text-xs text-danger">{error}</p>}

      <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        {cards.map((card) => {
          const Icon = card.icon;
          return (
            <article
              key={card.label}
              className="rounded-lg border border-line bg-elevated/60 p-3"
            >
              <div className="mb-2 flex items-center justify-between gap-2">
                <p className="text-[11px] uppercase tracking-[0.14em] text-mute">
                  {card.label}
                </p>
                <Icon className={cn("h-3.5 w-3.5", card.tone)} aria-hidden />
              </div>
              <p className="font-mono text-lg text-ink">{card.value}</p>
              <p className="mt-1 text-[11px] text-mute">{card.meta}</p>
            </article>
          );
        })}
      </div>
    </section>
  );
}
