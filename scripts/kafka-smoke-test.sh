#!/usr/bin/env bash
set -euo pipefail

BOOTSTRAP="${BOOTSTRAP:-kafka:29092}"
TOPIC="${TOPIC:-telemetry-events}"
MESSAGE="{\"service\":\"demo-api\",\"latency_ms\":42,\"status\":200,\"ts\":\"$(date -u +%Y-%m-%dT%H:%M:%SZ)\"}"

echo "==> Listing topics..."
docker compose exec -T kafka kafka-topics --bootstrap-server "$BOOTSTRAP" --list

echo "==> Producing test message to '$TOPIC'..."
echo "$MESSAGE" | docker compose exec -T kafka kafka-console-producer \
  --bootstrap-server "$BOOTSTRAP" \
  --topic "$TOPIC"

echo "==> Consuming 1 message from '$TOPIC'..."
docker compose exec -T kafka kafka-console-consumer \
  --bootstrap-server "$BOOTSTRAP" \
  --topic "$TOPIC" \
  --from-beginning \
  --max-messages 1 \
  --timeout-ms 15000

echo
echo "Smoke test OK — Kafka produce/consume works."
