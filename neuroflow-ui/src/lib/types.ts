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
