"use client";

import { useCallback, useEffect, useState, useTransition } from "react";
import { DashboardHeader } from "@/components/DashboardHeader";
import { DriftMonitor } from "@/components/DriftMonitor";
import { KpiCards } from "@/components/KpiCards";
import { LiveLogs } from "@/components/LiveLogs";
import { PerformanceCharts } from "@/components/PerformanceCharts";
import { fetchDriftReport, fetchKpis, fetchPerformanceSeries } from "@/lib/api";
import { MOCK_DRIFT_OK, MOCK_KPI, buildMockPerformanceSeries } from "@/lib/mock-data";
import type { DriftReport, KpiSnapshot, PerformancePoint } from "@/lib/types";

export default function HomePage() {
  const [project, setProject] = useState("neuroflow-prod");
  const [mockMode, setMockMode] = useState(true);
  const [live, setLive] = useState(false);
  const [kpi, setKpi] = useState<KpiSnapshot>(MOCK_KPI);
  const [drift, setDrift] = useState<DriftReport>(MOCK_DRIFT_OK);
  const [series, setSeries] = useState<PerformancePoint[]>([]);
  const [, startTransition] = useTransition();

  const handleMockFallback = useCallback(() => setMockMode(true), []);

  useEffect(() => {
    setSeries(buildMockPerformanceSeries());
  }, []);

  useEffect(() => {
    startTransition(() => {
      void (async () => {
        const [kpiResult, driftResult, perfResult] = await Promise.all([
          fetchKpis(),
          fetchDriftReport(false),
          fetchPerformanceSeries(),
        ]);
        setKpi(kpiResult.data);
        setDrift(driftResult.data);
        setSeries(perfResult.data);
        setMockMode(kpiResult.mock || driftResult.mock || perfResult.mock);
      })();
    });
  }, []);

  return (
    <main className="mx-auto flex min-h-screen max-w-7xl flex-col gap-6 px-4 py-6 md:px-8 md:py-8">
      <DashboardHeader
        mockMode={mockMode}
        live={live}
        project={project}
        onProjectChange={setProject}
      />

      <KpiCards kpi={kpi} />

      <div className="grid gap-4 xl:grid-cols-2">
        <LiveLogs onLiveChange={setLive} onMockFallback={handleMockFallback} />
        <PerformanceCharts series={series} />
      </div>

      <DriftMonitor
        initial={drift}
        onReport={(report, mock) => {
          setDrift(report);
          setKpi((prev) => ({
            ...prev,
            datasetDrift: report.datasetDrift,
            driftShare: report.driftShare,
          }));
          if (mock) setMockMode(true);
        }}
      />
    </main>
  );
}
