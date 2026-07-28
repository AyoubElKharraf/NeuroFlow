"use client";

import { useCallback, useEffect, useState } from "react";
import { Play, Square } from "lucide-react";
import {
  fetchSimulationStatus,
  startSimulation,
  stopSimulation,
} from "@/lib/api";
import { useAuth } from "@/hooks/useAuth";
import type { SimulationStatus } from "@/lib/types";
import { cn } from "@/lib/cn";

interface SimulationControlsProps {
  onChanged?: (status: SimulationStatus) => void;
}

const IDLE: SimulationStatus = {
  running: false,
  emitted: 0,
  targetRatePerSec: 5,
};

export function SimulationControls({ onChanged }: SimulationControlsProps) {
  const { isAuthenticated, isAdmin } = useAuth();
  const [status, setStatus] = useState<SimulationStatus>(IDLE);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    if (!isAuthenticated) return;
    try {
      const next = await fetchSimulationStatus();
      setStatus(next);
      setError(null);
    } catch {
      // Backend down — keep last known status
    }
  }, [isAuthenticated]);

  useEffect(() => {
    if (!isAuthenticated) {
      setStatus(IDLE);
      return;
    }
    void refresh();
    const timer = window.setInterval(() => void refresh(), 4000);
    return () => window.clearInterval(timer);
  }, [isAuthenticated, refresh]);

  async function runAction(action: "start" | "stop") {
    if (!isAdmin || busy) return;
    setBusy(true);
    setError(null);
    try {
      const next =
        action === "start" ? await startSimulation() : await stopSimulation();
      setStatus(next);
      onChanged?.(next);
    } catch (err) {
      setError(
        err instanceof Error
          ? err.message
          : action === "start"
            ? "Unable to start simulation"
            : "Unable to stop simulation",
      );
    } finally {
      setBusy(false);
    }
  }

  if (!isAuthenticated) return null;

  return (
    <div className="flex flex-col gap-1">
      <div className="flex flex-wrap items-center gap-2">
        <span
          className={cn(
            "inline-flex items-center gap-2 rounded-md border px-2.5 py-1.5 font-mono text-xs",
            status.running
              ? "border-ok/40 bg-ok/10 text-ok"
              : "border-line bg-elevated text-mute",
          )}
          title="Kafka event simulation"
        >
          <span
            className={cn(
              "h-1.5 w-1.5 rounded-full bg-current",
              status.running && "animate-pulseDot",
            )}
          />
          {status.running ? "SIM RUNNING" : "SIM IDLE"}
          <span className="text-mute">
            · {status.emitted.toLocaleString("en-US")} evt
            {status.running ? ` · ~${status.targetRatePerSec}/s` : ""}
          </span>
        </span>

        {isAdmin && (
          <>
            <button
              type="button"
              disabled={busy || status.running}
              onClick={() => void runAction("start")}
              className="inline-flex cursor-pointer items-center gap-1.5 rounded-md border border-ok/30 bg-ok/10 px-2.5 py-1.5 text-xs font-medium text-ok transition hover:bg-ok/20 disabled:cursor-not-allowed disabled:opacity-50"
            >
              <Play className="h-3.5 w-3.5" aria-hidden />
              Start
            </button>
            <button
              type="button"
              disabled={busy || !status.running}
              onClick={() => void runAction("stop")}
              className="inline-flex cursor-pointer items-center gap-1.5 rounded-md border border-danger/30 bg-danger/10 px-2.5 py-1.5 text-xs font-medium text-danger transition hover:bg-danger/20 disabled:cursor-not-allowed disabled:opacity-50"
            >
              <Square className="h-3 w-3" aria-hidden />
              Stop
            </button>
          </>
        )}
      </div>
      {error && <p className="text-[11px] text-danger">{error}</p>}
      {!isAdmin && (
        <p className="text-[11px] text-mute">Start/Stop requires ADMIN role</p>
      )}
    </div>
  );
}
