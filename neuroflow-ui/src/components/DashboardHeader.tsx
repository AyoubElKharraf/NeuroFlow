"use client";

import { Activity, Circle, LogOut } from "lucide-react";
import { cn } from "@/lib/cn";
import { useAuth } from "@/hooks/useAuth";
import { SimulationControls } from "@/components/SimulationControls";
import type { SimulationStatus } from "@/lib/types";

interface DashboardHeaderProps {
  mockMode: boolean;
  live: boolean;
  project: string;
  onProjectChange: (project: string) => void;
  onSimulationChange?: (status: SimulationStatus) => void;
}

const PROJECTS = ["neuroflow-prod", "neuroflow-staging", "loan-risk-lab"];

export function DashboardHeader({
  mockMode,
  live,
  project,
  onProjectChange,
  onSimulationChange,
}: DashboardHeaderProps) {
  const { session, isAuthenticated, logout } = useAuth();

  return (
    <header className="flex flex-col gap-4 border-b border-line/80 pb-5 md:flex-row md:items-end md:justify-between">
      <div className="space-y-2">
        <p className="font-display text-3xl tracking-tight text-accent md:text-4xl">
          NeuroFlow
        </p>
        <div className="flex flex-wrap items-center gap-3 text-sm">
          <span className="inline-flex items-center gap-2 text-ok">
            <Circle className="h-2.5 w-2.5 fill-ok text-ok" aria-hidden />
            All Systems Operational
          </span>
          <span
            className={cn(
              "inline-flex items-center gap-2 rounded-md border px-2.5 py-1 font-mono text-xs",
              live
                ? "border-accent/30 bg-accent/10 text-accent"
                : "border-line bg-elevated text-mute",
            )}
          >
            <span
              className={cn(
                "h-1.5 w-1.5 rounded-full bg-current",
                live && "animate-pulseDot",
              )}
            />
            {live ? "SSE LIVE" : "SSE IDLE"}
          </span>
          {mockMode && (
            <span className="inline-flex items-center gap-1.5 rounded-md border border-warn/30 bg-warn/10 px-2.5 py-1 font-mono text-xs text-warn">
              <Activity className="h-3 w-3" aria-hidden />
              MOCK MODE
            </span>
          )}
          {isAuthenticated && session && (
            <span className="inline-flex items-center gap-2 rounded-md border border-line bg-elevated px-2.5 py-1 font-mono text-xs text-ink">
              {session.username}
              <span className="text-mute">
                {session.roles
                  .map((role) => role.replace(/^ROLE_/, ""))
                  .join("+")}
              </span>
            </span>
          )}
        </div>
      </div>

      <div className="flex flex-wrap items-end gap-3">
        <SimulationControls onChanged={onSimulationChange} />

        <label className="flex flex-col gap-1 text-xs uppercase tracking-[0.14em] text-mute">
          Project
          <select
            value={project}
            onChange={(event) => onProjectChange(event.target.value)}
            className="min-w-[220px] cursor-pointer rounded-md border border-line bg-elevated px-3 py-2 font-mono text-sm normal-case tracking-normal text-ink outline-none transition hover:border-accent/40 focus:border-accent"
          >
            {PROJECTS.map((item) => (
              <option key={item} value={item}>
                {item}
              </option>
            ))}
          </select>
        </label>

        {isAuthenticated && (
          <button
            type="button"
            onClick={logout}
            className="inline-flex cursor-pointer items-center gap-2 rounded-md border border-line bg-elevated px-3 py-2 text-sm text-mute transition hover:border-danger/40 hover:text-danger"
          >
            <LogOut className="h-4 w-4" aria-hidden />
            Logout
          </button>
        )}
      </div>
    </header>
  );
}
