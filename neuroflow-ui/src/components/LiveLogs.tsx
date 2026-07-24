"use client";

import { useEffect, useMemo, useState } from "react";
import { Terminal } from "lucide-react";
import { createMockLogEvent } from "@/lib/mock-data";
import { getSseUrl } from "@/lib/api";
import { useAuth } from "@/hooks/useAuth";
import type { LiveLogEvent, LogLevel } from "@/lib/types";
import { cn } from "@/lib/cn";

type Filter = "ALL" | LogLevel;

interface LiveLogsProps {
  onLiveChange: (live: boolean) => void;
  onMockFallback: () => void;
}

export function LiveLogs({ onLiveChange, onMockFallback }: LiveLogsProps) {
  const { token, isAuthenticated } = useAuth();
  const [events, setEvents] = useState<LiveLogEvent[]>([]);
  const [filter, setFilter] = useState<Filter>("ALL");
  const [usingMock, setUsingMock] = useState(false);

  useEffect(() => {
    let cancelled = false;
    let mockTimer: ReturnType<typeof setInterval> | undefined;
    let mockIndex = 0;
    let source: EventSource | null = null;
    let opened = false;
    let fallbackTimer: ReturnType<typeof setTimeout> | undefined;

    const startMock = () => {
      if (cancelled || mockTimer) return;
      setUsingMock(true);
      onMockFallback();
      onLiveChange(true);
      mockTimer = setInterval(() => {
        mockIndex += 1;
        setEvents((prev) =>
          [createMockLogEvent(mockIndex), ...prev].slice(0, 120),
        );
      }, 900);
    };

    if (!isAuthenticated || !token) {
      setEvents([]);
      onLiveChange(false);
      return () => {
        cancelled = true;
      };
    }

    try {
      // EventSource cannot set Authorization headers — pass JWT as ?token=
      source = new EventSource(getSseUrl(token));
      source.onopen = () => {
        if (cancelled) return;
        opened = true;
        setUsingMock(false);
        onLiveChange(true);
      };
      source.onmessage = (message) => {
        if (cancelled) return;
        try {
          const parsed = JSON.parse(message.data) as LiveLogEvent;
          setEvents((prev) => [parsed, ...prev].slice(0, 120));
        } catch {
          // ignore malformed frames
        }
      };
      source.onerror = () => {
        source?.close();
        source = null;
        onLiveChange(false);
        if (!opened) startMock();
      };
      fallbackTimer = setTimeout(() => {
        if (!cancelled && !opened) {
          source?.close();
          source = null;
          startMock();
        }
      }, 2500);
    } catch {
      startMock();
    }

    return () => {
      cancelled = true;
      if (fallbackTimer) clearTimeout(fallbackTimer);
      if (mockTimer) clearInterval(mockTimer);
      source?.close();
      onLiveChange(false);
    };
  }, [isAuthenticated, token, onLiveChange, onMockFallback]);

  const visible = useMemo(
    () =>
      filter === "ALL" ? events : events.filter((event) => event.level === filter),
    [events, filter],
  );

  const filters: Filter[] = ["ALL", "INFO", "WARN", "ERROR"];

  return (
    <section className="rounded-xl border border-line bg-panel/90 shadow-glow">
      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-line px-4 py-3">
        <div className="flex items-center gap-2">
          <Terminal className="h-4 w-4 text-accent" aria-hidden />
          <h2 className="text-sm font-medium text-ink">Live Stream Panel</h2>
          {usingMock && (
            <span className="font-mono text-[11px] text-warn">mock feed</span>
          )}
        </div>
        <div className="flex gap-1">
          {filters.map((item) => (
            <button
              key={item}
              type="button"
              onClick={() => setFilter(item)}
              className={cn(
                "cursor-pointer rounded-md px-2.5 py-1 font-mono text-xs transition",
                filter === item
                  ? "bg-accent/15 text-accent"
                  : "text-mute hover:bg-elevated hover:text-ink",
              )}
            >
              {item}
            </button>
          ))}
        </div>
      </div>

      <div className="h-[320px] overflow-y-auto bg-[#060a12] px-3 py-2 font-mono text-xs leading-6">
        {!isAuthenticated ? (
          <p className="text-mute">Sign in to open the authenticated SSE stream…</p>
        ) : visible.length === 0 ? (
          <p className="text-mute">Waiting for stream events…</p>
        ) : (
          visible.map((event) => (
            <div key={event.id} className="grid grid-cols-[88px_56px_1fr] gap-2">
              <span className="text-mute">
                {new Date(event.timestamp).toLocaleTimeString("en-US")}
              </span>
              <span
                className={cn(
                  event.level === "ERROR" && "text-danger",
                  event.level === "WARN" && "text-warn",
                  event.level === "INFO" && "text-accent2",
                )}
              >
                {event.level}
              </span>
              <span className="truncate text-ink/90">
                <span className="text-mute">{event.service}</span> — {event.message}
              </span>
            </div>
          ))
        )}
      </div>
    </section>
  );
}
