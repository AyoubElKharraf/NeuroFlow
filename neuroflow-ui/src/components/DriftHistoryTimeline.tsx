"use client";

import { useEffect, useMemo, useState } from "react";
import {
  Area,
  AreaChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { History } from "lucide-react";
import { fetchDriftHistory } from "@/lib/api";
import type { DriftHistoryPoint } from "@/lib/types";
import { cn } from "@/lib/cn";

interface DriftHistoryTimelineProps {
  refreshKey?: number;
}

export function DriftHistoryTimeline({ refreshKey = 0 }: DriftHistoryTimelineProps) {
  const [history, setHistory] = useState<DriftHistoryPoint[]>([]);
  const [loading, setLoading] = useState(true);
  const [mock, setMock] = useState(false);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    void (async () => {
      const result = await fetchDriftHistory(40);
      if (cancelled) return;
      setHistory(result.data);
      setMock(result.mock);
      setLoading(false);
    })();
    return () => {
      cancelled = true;
    };
  }, [refreshKey]);

  const chartData = useMemo(
    () =>
      [...history]
        .reverse()
        .map((point) => ({
          time: new Date(point.checkedAt).toLocaleTimeString("en-US", {
            hour: "2-digit",
            minute: "2-digit",
          }),
          driftSharePct: Math.round(point.driftShare * 1000) / 10,
          drifted: point.datasetDrift ? 1 : 0,
        })),
    [history],
  );

  return (
    <section className="rounded-xl border border-line bg-panel/90 p-4 shadow-glow">
      <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-2">
          <History className="h-4 w-4 text-accent2" aria-hidden />
          <div>
            <h2 className="text-sm font-medium text-ink">Drift History</h2>
            <p className="mt-1 text-xs text-mute">
              ClickHouse timeline of Evidently checks
              {mock ? " · unavailable" : ` · ${history.length} runs`}
            </p>
          </div>
        </div>
      </div>

      <div className="mb-4 h-[180px] w-full">
        {loading ? (
          <div className="flex h-full items-center justify-center text-xs text-mute">
            Loading history…
          </div>
        ) : chartData.length === 0 ? (
          <div className="flex h-full items-center justify-center text-xs text-mute">
            No drift checks stored yet — run a manual check to seed the timeline.
          </div>
        ) : (
          <ResponsiveContainer width="100%" height="100%">
            <AreaChart data={chartData}>
              <defs>
                <linearGradient id="driftFill" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0%" stopColor="#f59e0b" stopOpacity={0.35} />
                  <stop offset="100%" stopColor="#f59e0b" stopOpacity={0.02} />
                </linearGradient>
              </defs>
              <CartesianGrid stroke="#1e293b" strokeDasharray="3 3" />
              <XAxis dataKey="time" stroke="#94a3b8" tick={{ fontSize: 10 }} minTickGap={28} />
              <YAxis
                stroke="#94a3b8"
                tick={{ fontSize: 10 }}
                unit="%"
                domain={[0, "auto"]}
              />
              <Tooltip
                contentStyle={{
                  background: "#121a2b",
                  border: "1px solid #1e293b",
                  borderRadius: 8,
                  color: "#e8eef9",
                }}
              />
              <Area
                type="monotone"
                dataKey="driftSharePct"
                name="drift share %"
                stroke="#f59e0b"
                fill="url(#driftFill)"
                strokeWidth={2}
              />
            </AreaChart>
          </ResponsiveContainer>
        )}
      </div>

      <div className="max-h-[220px] overflow-y-auto">
        <table className="min-w-full text-left text-xs">
          <thead className="sticky top-0 bg-panel text-mute">
            <tr className="border-b border-line">
              <th className="px-2 py-2 font-medium">When</th>
              <th className="px-2 py-2 font-medium">Source</th>
              <th className="px-2 py-2 font-medium">Share</th>
              <th className="px-2 py-2 font-medium">Status</th>
            </tr>
          </thead>
          <tbody>
            {history.length === 0 ? (
              <tr>
                <td colSpan={4} className="px-2 py-4 text-mute">
                  Empty history in `drift_history`.
                </td>
              </tr>
            ) : (
              history.map((point) => (
                <tr key={point.checkId} className="border-b border-line/70">
                  <td className="px-2 py-2 font-mono text-mute">
                    {new Date(point.checkedAt).toLocaleString("en-US", {
                      month: "short",
                      day: "2-digit",
                      hour: "2-digit",
                      minute: "2-digit",
                      second: "2-digit",
                    })}
                  </td>
                  <td className="px-2 py-2 font-mono text-mute">{point.triggerSource}</td>
                  <td className="px-2 py-2 font-mono text-ink">
                    {(point.driftShare * 100).toFixed(0)}%
                    <span className="text-mute">
                      {" "}
                      · {point.numberOfDriftedFeatures} feat
                    </span>
                  </td>
                  <td className="px-2 py-2">
                    <span
                      className={cn(
                        "rounded px-2 py-0.5 font-mono",
                        point.datasetDrift
                          ? "bg-danger/15 text-danger"
                          : "bg-ok/15 text-ok",
                      )}
                    >
                      {point.datasetDrift ? "DRIFT" : "OK"}
                    </span>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </section>
  );
}
