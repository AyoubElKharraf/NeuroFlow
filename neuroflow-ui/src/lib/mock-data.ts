import type {
  DriftReport,
  KpiSnapshot,
  LiveLogEvent,
  PerformancePoint,
} from "@/lib/types";

export const MOCK_KPI: KpiSnapshot = {
  totalEvents: 12840,
  eventsTrendPct: 12.4,
  latencyP95Ms: 42,
  latencyP99Ms: 118,
  errorRatePct: 0.4,
  datasetDrift: false,
  driftShare: 0.0,
};

export const MOCK_DRIFT_OK: DriftReport = {
  modelName: "loan-risk-model",
  datasetDrift: false,
  driftShare: 0,
  numberOfDriftedFeatures: 0,
  message: "No significant dataset drift detected",
  features: [
    {
      feature: "age",
      driftScore: 0.62,
      driftDetected: false,
      statTestName: "K-S p_value",
      baselineMean: 42.1,
      productionMean: 41.8,
    },
    {
      feature: "income",
      driftScore: 0.41,
      driftDetected: false,
      statTestName: "K-S p_value",
      baselineMean: 54500,
      productionMean: 55210,
    },
    {
      feature: "credit_score",
      driftScore: 0.55,
      driftDetected: false,
      statTestName: "K-S p_value",
      baselineMean: 680,
      productionMean: 676,
    },
    {
      feature: "loan_amount",
      driftScore: 0.48,
      driftDetected: false,
      statTestName: "K-S p_value",
      baselineMean: 18400,
      productionMean: 19120,
    },
  ],
};

export const MOCK_DRIFT_ALERT: DriftReport = {
  modelName: "loan-risk-model",
  datasetDrift: true,
  driftShare: 0.75,
  numberOfDriftedFeatures: 3,
  message: "DATA DRIFT DETECTED",
  features: [
    {
      feature: "age",
      driftScore: 0.21,
      driftDetected: false,
      statTestName: "K-S p_value",
      baselineMean: 42.1,
      productionMean: 43.0,
    },
    {
      feature: "income",
      driftScore: 0.0004,
      driftDetected: true,
      statTestName: "K-S p_value",
      baselineMean: 54500,
      productionMean: 248000,
    },
    {
      feature: "credit_score",
      driftScore: 0.0012,
      driftDetected: true,
      statTestName: "K-S p_value",
      baselineMean: 680,
      productionMean: 510,
    },
    {
      feature: "loan_amount",
      driftScore: 0.00008,
      driftDetected: true,
      statTestName: "K-S p_value",
      baselineMean: 18400,
      productionMean: 61200,
    },
  ],
};

export function buildMockPerformanceSeries(): PerformancePoint[] {
  const now = Date.now();
  const points: PerformancePoint[] = [];
  for (let i = 29; i >= 0; i -= 1) {
    const t = new Date(now - i * 60_000);
    const wave = Math.sin(i / 4) * 18;
    points.push({
      time: t.toLocaleTimeString("en-US", { hour: "2-digit", minute: "2-digit" }),
      requestsPerMin: Math.round(110 + wave + (i % 5) * 3),
      inferenceLatencyMs: Math.round(28 + Math.abs(wave) * 0.7 + (i % 7)),
    });
  }
  return points;
}

const SERVICES = ["checkout-api", "auth-api", "catalog-api", "payments-api", "loan-risk-model"];
const LEVELS = ["INFO", "WARN", "ERROR"] as const;

export function createMockLogEvent(index: number): LiveLogEvent {
  const level = LEVELS[index % 11 === 0 ? 2 : index % 5 === 0 ? 1 : 0];
  const service = SERVICES[index % SERVICES.length];
  const messages: Record<(typeof LEVELS)[number], string> = {
    INFO: `Handled request latency=${20 + (index % 40)}ms`,
    WARN: `Elevated p95 latency on ${service}`,
    ERROR: `Upstream timeout after retries service=${service}`,
  };
  return {
    id: `mock-${Date.now()}-${index}`,
    timestamp: new Date().toISOString(),
    level,
    service,
    message: messages[level],
  };
}
