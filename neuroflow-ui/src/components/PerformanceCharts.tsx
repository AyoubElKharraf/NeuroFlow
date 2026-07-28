"use client";

import {
  Area,
  AreaChart,
  CartesianGrid,
  Legend,
  Line,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import type { PerformancePoint } from "@/lib/types";

interface PerformanceChartsProps {
  series: PerformancePoint[];
}

export function PerformanceCharts({ series }: PerformanceChartsProps) {
  return (
    <section className="rounded-xl border border-line bg-panel/90 p-4 shadow-glow">
      <div className="mb-4">
        <h2 className="text-sm font-medium text-ink">System Performance</h2>
        <p className="mt-1 text-xs text-mute">
          Request volume and ML inference latency from ClickHouse — last 30 minutes
        </p>
      </div>
      <div className="h-[280px] w-full">
        <ResponsiveContainer width="100%" height="100%">
          <AreaChart data={series}>
            <defs>
              <linearGradient id="reqFill" x1="0" y1="0" x2="0" y2="1">
                <stop offset="0%" stopColor="#22d3ee" stopOpacity={0.35} />
                <stop offset="100%" stopColor="#22d3ee" stopOpacity={0.02} />
              </linearGradient>
            </defs>
            <CartesianGrid stroke="#1e293b" strokeDasharray="3 3" />
            <XAxis dataKey="time" stroke="#94a3b8" tick={{ fontSize: 10 }} minTickGap={24} />
            <YAxis yAxisId="left" stroke="#94a3b8" tick={{ fontSize: 10 }} />
            <YAxis
              yAxisId="right"
              orientation="right"
              stroke="#94a3b8"
              tick={{ fontSize: 10 }}
            />
            <Tooltip
              contentStyle={{
                background: "#121a2b",
                border: "1px solid #1e293b",
                borderRadius: 8,
                color: "#e8eef9",
              }}
            />
            <Legend />
            <Area
              yAxisId="left"
              type="monotone"
              dataKey="requestsPerMin"
              name="req/min"
              stroke="#22d3ee"
              fill="url(#reqFill)"
              strokeWidth={2}
            />
            <Line
              yAxisId="right"
              type="monotone"
              dataKey="inferenceLatencyMs"
              name="inference ms"
              stroke="#38bdf8"
              strokeWidth={2}
              dot={false}
            />
          </AreaChart>
        </ResponsiveContainer>
      </div>
    </section>
  );
}
