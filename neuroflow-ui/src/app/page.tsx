import { Activity } from "lucide-react";

export default function HomePage() {
  return (
    <main className="mx-auto flex min-h-screen max-w-3xl flex-col justify-center gap-6 px-6">
      <p className="font-display text-5xl tracking-tight text-accent md:text-6xl">NeuroFlow</p>
      <h1 className="max-w-xl text-2xl font-medium text-ink/90 md:text-3xl">
        Event-driven observability for microservices and ML models.
      </h1>
      <p className="max-w-lg text-ink/65">
        Phase 1 scaffold — connect Kafka telemetry, ClickHouse analytics, and live alerts in the
        next phases.
      </p>
      <div className="flex items-center gap-2 text-sm text-accent/90">
        <Activity className="h-4 w-4" aria-hidden />
        <span>Dashboard ready for Phase 4 wiring</span>
      </div>
    </main>
  );
}
