"use client";

import { useCallback, useEffect, useState, useTransition } from "react";
import { DashboardHeader } from "@/components/DashboardHeader";
import { DriftMonitor } from "@/components/DriftMonitor";
import { KpiCards } from "@/components/KpiCards";
import { LiveLogs } from "@/components/LiveLogs";
import { LoginModal } from "@/components/LoginModal";
import { PerformanceCharts } from "@/components/PerformanceCharts";
import { AuthProvider, useAuth } from "@/hooks/useAuth";
import { fetchDriftReport, fetchKpis, fetchPerformanceSeries } from "@/lib/api";
import { MOCK_DRIFT_OK, MOCK_KPI, buildMockPerformanceSeries } from "@/lib/mock-data";
import type { DriftReport, KpiSnapshot, PerformancePoint } from "@/lib/types";

function DashboardApp() {
  const { ready, isAuthenticated } = useAuth();
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
    if (!ready || !isAuthenticated) return;

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
  }, [ready, isAuthenticated]);

  if (!ready) {
    return (
      <main className="mx-auto flex min-h-screen max-w-7xl items-center justify-center px-4">
        <p className="font-mono text-sm text-mute">Loading session…</p>
      </main>
    );
  }

  return (
    <>
      {!isAuthenticated && <LoginModal />}

      <main
        className={`mx-auto flex min-h-screen max-w-7xl flex-col gap-6 px-4 py-6 md:px-8 md:py-8 ${
          !isAuthenticated ? "pointer-events-none select-none blur-[2px]" : ""
        }`}
      >
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
    </>
  );
}

export default function HomePage() {
  return (
    <AuthProvider>
      <DashboardApp />
    </AuthProvider>
  );
}
