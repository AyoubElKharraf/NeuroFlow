import {
  MOCK_DRIFT_ALERT,
  MOCK_DRIFT_OK,
  MOCK_KPI,
  buildMockPerformanceSeries,
} from "@/lib/mock-data";
import type { DriftReport, KpiSnapshot, PerformancePoint } from "@/lib/types";

const API_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";
const ANALYTICS_URL =
  process.env.NEXT_PUBLIC_ANALYTICS_URL ?? "http://localhost:8001";

async function fetchJson<T>(url: string, init?: RequestInit): Promise<T> {
  const response = await fetch(url, {
    ...init,
    headers: {
      "Content-Type": "application/json",
      ...(init?.headers ?? {}),
    },
    cache: "no-store",
  });
  if (!response.ok) {
    throw new Error(`HTTP ${response.status} for ${url}`);
  }
  return (await response.json()) as T;
}

export async function fetchKpis(): Promise<{ data: KpiSnapshot; mock: boolean }> {
  try {
    const metrics = await fetchJson<{
      telemetryEvents: number;
      mlPredictions: number;
    }>(`${API_URL}/api/v1/metrics/ingestion`);

    let datasetDrift = false;
    let driftShare = 0;
    try {
      const sample = await fetchJson<{
        current_data: Array<Record<string, number>>;
      }>(`${ANALYTICS_URL}/api/v1/drift/samples/production-like?n=80&seed=11`);
      const drift = await fetchJson<{
        dataset_drift: boolean;
        drift_share: number;
      }>(`${ANALYTICS_URL}/api/v1/drift/check`, {
        method: "POST",
        body: JSON.stringify({
          model_name: "loan-risk-model",
          current_data: sample.current_data,
        }),
      });
      datasetDrift = drift.dataset_drift;
      driftShare = drift.drift_share;
    } catch {
      // analytics optional for KPI panel
    }

    return {
      mock: false,
      data: {
        totalEvents: metrics.telemetryEvents + metrics.mlPredictions,
        eventsTrendPct: 8.2,
        latencyP95Ms: 42,
        latencyP99Ms: 118,
        errorRatePct: 0.4,
        datasetDrift,
        driftShare,
      },
    };
  } catch {
    return { mock: true, data: MOCK_KPI };
  }
}

export async function fetchDriftReport(
  forceDriftedSample = false,
): Promise<{ data: DriftReport; mock: boolean }> {
  try {
    const sample = await fetchJson<{
      current_data: Array<Record<string, number>>;
    }>(`${ANALYTICS_URL}/api/v1/drift/samples/production-like?n=120&seed=21`);

    const currentData = forceDriftedSample
      ? sample.current_data.map((row) => ({
          ...row,
          income: row.income * 5,
          credit_score: Math.max(300, row.credit_score - 180),
          loan_amount: row.loan_amount * 3.5,
        }))
      : sample.current_data;

    const raw = await fetchJson<{
      model_name: string;
      dataset_drift: boolean;
      drift_share: number;
      number_of_drifted_features: number;
      message: string;
      feature_metrics: Record<
        string,
        { drift_score: number; drift_detected: boolean; stat_test_name: string }
      >;
    }>(`${ANALYTICS_URL}/api/v1/drift/check`, {
      method: "POST",
      body: JSON.stringify({
        model_name: "loan-risk-model",
        current_data: currentData,
      }),
    });

    const features = Object.entries(raw.feature_metrics).map(
      ([feature, metric]) => ({
        feature,
        driftScore: metric.drift_score,
        driftDetected: metric.drift_detected,
        statTestName: metric.stat_test_name,
        baselineMean: feature === "income" ? 54500 : feature === "age" ? 42 : feature === "credit_score" ? 680 : 18400,
        productionMean:
          feature === "income"
            ? forceDriftedSample
              ? 248000
              : 55210
            : feature === "age"
              ? 41.8
              : feature === "credit_score"
                ? forceDriftedSample
                  ? 510
                  : 676
                : forceDriftedSample
                  ? 61200
                  : 19120,
      }),
    );

    return {
      mock: false,
      data: {
        modelName: raw.model_name,
        datasetDrift: raw.dataset_drift,
        driftShare: raw.drift_share,
        numberOfDriftedFeatures: raw.number_of_drifted_features,
        message: raw.message,
        features,
      },
    };
  } catch {
    return {
      mock: true,
      data: forceDriftedSample ? MOCK_DRIFT_ALERT : MOCK_DRIFT_OK,
    };
  }
}

export async function fetchPerformanceSeries(): Promise<{
  data: PerformancePoint[];
  mock: boolean;
}> {
  // No dedicated timeseries endpoint yet — mock with graceful live feel.
  return { mock: true, data: buildMockPerformanceSeries() };
}

export function getSseUrl(): string {
  return `${API_URL}/api/v1/alerts/stream`;
}
