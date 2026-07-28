import {
  MOCK_DRIFT_OK,
  MOCK_KPI,
  buildMockPerformanceSeries,
} from "@/lib/mock-data";
import { buildSseUrl, getStoredToken, notifyUnauthorized } from "@/lib/auth";
import type {
  DriftHistoryPoint,
  DriftReport,
  FeatureDriftMetric,
  KpiSnapshot,
  PerformancePoint,
  SimulationStatus,
} from "@/lib/types";



const API_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

/**
 * Centralized fetch: injects Bearer token for backend calls and
 * clears the session on HTTP 401.
 */
export async function apiFetch(
  url: string,
  init: RequestInit = {},
): Promise<Response> {
  const headers = new Headers(init.headers);
  if (!headers.has("Content-Type") && init.body) {
    headers.set("Content-Type", "application/json");
  }

  if (url.startsWith(API_URL)) {
    const token = getStoredToken();
    if (token) {
      headers.set("Authorization", `Bearer ${token}`);
    }
  }

  const response = await fetch(url, {
    ...init,
    headers,
    cache: "no-store",
  });

  if (response.status === 401 && url.startsWith(API_URL)) {
    notifyUnauthorized();
  }

  return response;
}

async function fetchJson<T>(url: string, init?: RequestInit): Promise<T> {
  const response = await apiFetch(url, init);
  if (!response.ok) {
    throw new Error(`HTTP ${response.status} for ${url}`);
  }
  return (await response.json()) as T;
}

export async function fetchKpis(): Promise<{ data: KpiSnapshot; mock: boolean }> {
  try {
    const metrics = await fetchJson<{
      totalEvents: number;
      eventsTrendPct: number;
      latencyP95Ms: number;
      latencyP99Ms: number;
      errorRatePct: number;
      datasetDrift: boolean;
      driftShare: number;
    }>(`${API_URL}/api/v1/metrics/kpis`);

    return {
      mock: false,
      data: {
        totalEvents: metrics.totalEvents,
        eventsTrendPct: metrics.eventsTrendPct,
        latencyP95Ms: metrics.latencyP95Ms,
        latencyP99Ms: metrics.latencyP99Ms,
        errorRatePct: metrics.errorRatePct,
        datasetDrift: metrics.datasetDrift,
        driftShare: metrics.driftShare,
      },
    };
  } catch {
    return { mock: true, data: MOCK_KPI };
  }
}

function mapDriftFeatures(
  features: Array<{
    feature: string;
    driftScore: number;
    driftDetected: boolean;
    statTestName: string;
    baselineMean: number;
    productionMean: number;
  }>,
): FeatureDriftMetric[] {
  return features.map((feature) => ({
    feature: feature.feature,
    driftScore: feature.driftScore,
    driftDetected: feature.driftDetected,
    statTestName: feature.statTestName,
    baselineMean: feature.baselineMean,
    productionMean: feature.productionMean,
  }));
}

export async function fetchDriftReport(): Promise<{
  data: DriftReport;
  mock: boolean;
}> {
  try {
    const raw = await fetchJson<{
      status?: string;
      modelName: string;
      datasetDrift: boolean;
      driftShare: number;
      numberOfDriftedFeatures: number;
      message: string;
      features: Array<{
        feature: string;
        driftScore: number;
        driftDetected: boolean;
        statTestName: string;
        baselineMean: number;
        productionMean: number;
      }>;
    }>(`${API_URL}/api/v1/analytics/drift/run`, { method: "POST" });

    if (raw.status === "ANALYTICS_DOWN" || raw.status === "EMPTY_RESPONSE") {
      return { mock: true, data: MOCK_DRIFT_OK };
    }

    return {
      mock: false,
      data: {
        modelName: raw.modelName ?? "loan-risk-model",
        datasetDrift: Boolean(raw.datasetDrift),
        driftShare: Number(raw.driftShare ?? 0),
        numberOfDriftedFeatures: Number(raw.numberOfDriftedFeatures ?? 0),
        message:
          raw.message ??
          (raw.status === "SKIPPED"
            ? "No production features in ClickHouse yet — start the simulation"
            : "Drift check complete"),
        features: mapDriftFeatures(raw.features ?? []),
      },
    };
  } catch {
    return { mock: true, data: MOCK_DRIFT_OK };
  }
}

export async function fetchDriftHistory(
  limit = 30,
): Promise<{ data: DriftHistoryPoint[]; mock: boolean }> {
  try {
    const payload = await fetchJson<{
      history: DriftHistoryPoint[];
    }>(`${API_URL}/api/v1/analytics/drift/history?limit=${limit}`);
    return { mock: false, data: payload.history ?? [] };
  } catch {
    return { mock: true, data: [] };
  }
}

export async function fetchPerformanceSeries(): Promise<{
  data: PerformancePoint[];
  mock: boolean;
}> {
  try {
    const payload = await fetchJson<{
      series: PerformancePoint[];
    }>(`${API_URL}/api/v1/metrics/performance?minutes=30`);

    return {
      mock: false,
      data: payload.series ?? [],
    };
  } catch {
    return { mock: true, data: buildMockPerformanceSeries() };
  }
}

export async function fetchSimulationStatus(): Promise<SimulationStatus> {
  return fetchJson<SimulationStatus>(`${API_URL}/api/v1/simulation/status`);
}

export async function startSimulation(): Promise<SimulationStatus> {
  return fetchJson<SimulationStatus>(`${API_URL}/api/v1/simulation/start`, {
    method: "POST",
  });
}

export async function stopSimulation(): Promise<SimulationStatus> {
  return fetchJson<SimulationStatus>(`${API_URL}/api/v1/simulation/stop`, {
    method: "POST",
  });
}

export function getSseUrl(token?: string | null): string {
  return buildSseUrl(token);
}
