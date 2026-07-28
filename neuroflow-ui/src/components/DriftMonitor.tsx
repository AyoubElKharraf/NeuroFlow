"use client";

import { useEffect, useState } from "react";
import {
  Bar,
  BarChart,
  CartesianGrid,
  Legend,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { RefreshCw } from "lucide-react";
import { fetchDriftReport } from "@/lib/api";
import type { DriftReport } from "@/lib/types";
import { cn } from "@/lib/cn";

interface DriftMonitorProps {
  initial: DriftReport;
  onReport: (report: DriftReport, mock: boolean) => void;
}

export function DriftMonitor({ initial, onReport }: DriftMonitorProps) {
  const [report, setReport] = useState(initial);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setReport(initial);
  }, [initial]);

  const chartData = report.features.map((feature) => ({
    feature: feature.feature,
    baseline: feature.baselineMean,
    production: feature.productionMean,
  }));

  async function runCheck() {
    setLoading(true);
    setError(null);
    try {
      const result = await fetchDriftReport();
      setReport(result.data);
      onReport(result.data, result.mock);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Drift check failed");
    } finally {
      setLoading(false);
    }
  }

  return (
    <section className="rounded-xl border border-line bg-panel/90 p-4 shadow-glow">
      <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
        <div>
          <h2 className="text-sm font-medium text-ink">MLOps Data Drift</h2>
          <p className="mt-1 text-xs text-mute">
            {report.modelName} · {report.message}
            {report.features.length === 0
              ? " · waiting for ml_predictions rows"
              : ` · ${report.features.length} features`}
          </p>
        </div>
        <div className="flex gap-2">
          <button
            type="button"
            disabled={loading}
            onClick={() => void runCheck()}
            className="inline-flex cursor-pointer items-center gap-2 rounded-md border border-accent/30 bg-accent/10 px-3 py-2 text-xs font-medium text-accent transition hover:bg-accent/20 disabled:cursor-not-allowed disabled:opacity-60"
          >
            <RefreshCw className={cn("h-3.5 w-3.5", loading && "animate-spin")} />
            Run Drift Check (ClickHouse)
          </button>
        </div>
      </div>

      <div className="mb-4 flex flex-wrap gap-2">
        <span
          className={cn(
            "rounded-md border px-2.5 py-1 font-mono text-xs",
            report.datasetDrift
              ? "border-danger/40 bg-danger/10 text-danger"
              : "border-ok/40 bg-ok/10 text-ok",
          )}
        >
          {report.datasetDrift ? "DRIFT DETECTED" : "NO DRIFT"}
        </span>
        <span className="rounded-md border border-line bg-elevated px-2.5 py-1 font-mono text-xs text-mute">
          share={(report.driftShare * 100).toFixed(0)}%
        </span>
        <span className="rounded-md border border-line bg-elevated px-2.5 py-1 font-mono text-xs text-mute">
          drifted={report.numberOfDriftedFeatures}
        </span>
      </div>

      {error && <p className="mb-3 text-xs text-danger">{error}</p>}

      <div className="mb-5 overflow-x-auto">
        <table className="min-w-full text-left text-xs">
          <thead className="text-mute">
            <tr className="border-b border-line">
              <th className="px-2 py-2 font-medium">Feature</th>
              <th className="px-2 py-2 font-medium">Score</th>
              <th className="px-2 py-2 font-medium">Test</th>
              <th className="px-2 py-2 font-medium">Status</th>
            </tr>
          </thead>
          <tbody>
            {report.features.length === 0 ? (
              <tr>
                <td colSpan={4} className="px-2 py-4 text-mute">
                  No feature rows yet. Start simulation to ingest ML predictions into ClickHouse.
                </td>
              </tr>
            ) : (
              report.features.map((feature) => (
                <tr key={feature.feature} className="border-b border-line/70">
                  <td className="px-2 py-2 font-mono text-ink">{feature.feature}</td>
                  <td className="px-2 py-2 font-mono text-mute">
                    {feature.driftScore.toExponential(2)}
                  </td>
                  <td className="px-2 py-2 text-mute">{feature.statTestName}</td>
                  <td className="px-2 py-2">
                    <span
                      className={cn(
                        "rounded px-2 py-0.5 font-mono",
                        feature.driftDetected
                          ? "bg-danger/15 text-danger"
                          : "bg-ok/15 text-ok",
                      )}
                    >
                      {feature.driftDetected ? "DRIFTED" : "STABLE"}
                    </span>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      <div className="h-[260px] w-full">
        {chartData.length === 0 ? (
          <div className="flex h-full items-center justify-center text-xs text-mute">
            Baseline vs production chart appears after the first ClickHouse drift check.
          </div>
        ) : (
          <ResponsiveContainer width="100%" height="100%">
            <BarChart data={chartData}>
              <CartesianGrid stroke="#1e293b" strokeDasharray="3 3" />
              <XAxis dataKey="feature" stroke="#94a3b8" tick={{ fontSize: 11 }} />
              <YAxis stroke="#94a3b8" tick={{ fontSize: 11 }} />
              <Tooltip
                contentStyle={{
                  background: "#121a2b",
                  border: "1px solid #1e293b",
                  borderRadius: 8,
                  color: "#e8eef9",
                }}
              />
              <Legend />
              <Bar dataKey="baseline" fill="#38bdf8" radius={[4, 4, 0, 0]} />
              <Bar dataKey="production" fill="#22d3ee" radius={[4, 4, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
        )}
      </div>
    </section>
  );
}
