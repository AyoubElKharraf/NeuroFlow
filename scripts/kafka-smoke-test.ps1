# NeuroFlow - Kafka smoke test (Windows PowerShell)
# From cmd.exe use:  scripts\kafka-smoke-test.cmd
# From PowerShell:   .\scripts\kafka-smoke-test.ps1

$ErrorActionPreference = "Stop"
$Bootstrap = "kafka:29092"
$Topic = "telemetry-events"
$Message = '{"service":"demo-api","latency_ms":42,"status":200,"ts":"' + (Get-Date).ToUniversalTime().ToString("o") + '"}'

Write-Host "==> Listing topics..." -ForegroundColor Cyan
docker compose exec -T kafka kafka-topics --bootstrap-server $Bootstrap --list

Write-Host "==> Producing test message to '$Topic'..." -ForegroundColor Cyan
$Message | docker compose exec -T kafka kafka-console-producer --bootstrap-server $Bootstrap --topic $Topic

Write-Host "==> Consuming 1 message from '$Topic'..." -ForegroundColor Cyan
docker compose exec -T kafka kafka-console-consumer `
  --bootstrap-server $Bootstrap `
  --topic $Topic `
  --from-beginning `
  --max-messages 1 `
  --timeout-ms 15000

Write-Host ""
Write-Host "Smoke test OK - Kafka produce/consume works." -ForegroundColor Green
