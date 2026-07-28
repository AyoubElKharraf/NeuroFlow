"use client";

import { useCallback, useEffect, useState, useTransition } from "react";
import { ActuatorMetricsPanel } from "@/components/ActuatorMetricsPanel";
import { DashboardHeader } from "@/components/DashboardHeader";
import { DriftHistoryTimeline } from "@/components/DriftHistoryTimeline";
import { DriftMonitor } from "@/components/DriftMonitor";
import { KpiCards } from "@/components/KpiCards";
import { LiveLogs } from "@/components/LiveLogs";
import { LoginModal } from "@/components/LoginModal";
import { PerformanceCharts } from "@/components/PerformanceCharts";
import { AuthProvider, useAuth } from "@/hooks/useAuth";
import { fetchDriftReport, fetchKpis, fetchPerformanceSeries } from "@/lib/api";
import { MOCK_DRIFT_OK, MOCK_KPI } from "@/lib/mock-data";
import type { DriftReport, KpiSnapshot, PerformancePoint } from "@/lib/types";

function DashboardApp() {
  const { ready, isAuthenticated } = useAuth();
  const [project, setProject] = useState("neuroflow-prod");
  const [mockMode, setMockMode] = useState(true);
  const [live, setLive] = useState(false);
  const [kpi, setKpi] = useState<KpiSnapshot>(MOCK_KPI);
  const [drift, setDrift] = useState<DriftReport>(MOCK_DRIFT_OK);
  const [series, setSeries] = useState<PerformancePoint[]>([]);
  const [historyKey, setHistoryKey] = useState(0);
  const [, startTransition] = useTransition();

  const handleMockFallback = useCallback(() => setMockMode(true), []);

  const refreshDashboard = useCallback(() => {
    if (!isAuthenticated) return;
    startTransition(() => {
      void (async () => {
        const [kpiResult, driftResult, perfResult] = await Promise.all([
          fetchKpis(),
          fetchDriftReport(),
          fetchPerformanceSeries(),
        ]);
        setKpi({
          ...kpiResult.data,
          datasetDrift: driftResult.data.datasetDrift,
          driftShare: driftResult.data.driftShare,
        });
        setDrift(driftResult.data);
        setSeries(perfResult.data);
        setMockMode(kpiResult.mock || driftResult.mock || perfResult.mock);
        setHistoryKey((key) => key + 1);
      })();
    });
  }, [isAuthenticated]);

  useEffect(() => {
    if (!ready || !isAuthenticated) return;
    refreshDashboard();
  }, [ready, isAuthenticated, refreshDashboard]);

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
          onSimulationChange={() => {
            // Give Kafka → ClickHouse a moment, then refresh KPIs/charts
            window.setTimeout(() => refreshDashboard(), 2500);
          }}
        />

        <KpiCards kpi={kpi} />

        <ActuatorMetricsPanel />

        <div className="grid gap-4 xl:grid-cols-2">
          <LiveLogs onLiveChange={setLive} onMockFallback={handleMockFallback} />
          <PerformanceCharts series={series} />
        </div>

        <div className="grid gap-4 xl:grid-cols-2">
          <DriftMonitor
            initial={drift}
            onReport={(report, mock) => {
              setDrift(report);
              setKpi((prev) => ({
                ...prev,
                datasetDrift: report.datasetDrift,
                driftShare: report.driftShare,
              }));
              setHistoryKey((key) => key + 1);
              if (mock) setMockMode(true);
              else setMockMode(false);
            }}
          />
          <DriftHistoryTimeline refreshKey={historyKey} />
        </div>
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
