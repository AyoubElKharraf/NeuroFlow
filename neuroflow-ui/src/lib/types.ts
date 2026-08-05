export type LogLevel = "INFO" | "WARN" | "ERROR";

export interface LiveLogEvent {
  id: string;
  timestamp: string;
  level: LogLevel;
  service: string;
  message: string;
}

export interface KpiSnapshot {
  totalEvents: number;
  eventsTrendPct: number;
  latencyP95Ms: number;
  latencyP99Ms: number;
  errorRatePct: number;
  datasetDrift: boolean;
  driftShare: number;
}

export interface FeatureDriftMetric {
  feature: string;
  driftScore: number;
  driftDetected: boolean;
  statTestName: string;
  baselineMean: number;
  productionMean: number;
}

export interface DriftReport {
  modelName: string;
  datasetDrift: boolean;
  driftShare: number;
  numberOfDriftedFeatures: number;
  features: FeatureDriftMetric[];
  message: string;
}

export interface PerformancePoint {
  time: string;
  requestsPerMin: number;
  inferenceLatencyMs: number;
}

export interface SimulationStatus {
  running: boolean;
  emitted: number;
  targetRatePerSec: number;
  /** Target share of simulated HTTP 4xx/5xx responses (0–100). */
  errorRatePct: number;
}

export interface DriftHistoryPoint {
  checkId: string;
  modelName: string;
  datasetDrift: boolean;
  driftShare: number;
  numberOfDriftedFeatures: number;
  sampleSize: number;
  message: string;
  triggerSource: string;
  checkedAt: string;
}

export interface RuntimeMetrics {
  source: string;
  prometheusPath: string;
  jvm: {
    heapUsedMb: number;
    heapMaxMb: number;
    heapUsagePct: number;
    threadsLive: number;
    cpuProcessPct: number;
    cpuSystemPct: number;
    uptimeSec: number;
  };
  http: {
    requestCount: number;
    meanLatencyMs: number;
    maxLatencyMs: number;
  };
  neuroflow: {
    alertsPublished: number;
    simulationEmitted: number;
    driftChecks: number;
    driftDetected: number;
  };
}
