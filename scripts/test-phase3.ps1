# NeuroFlow Phase 3 — Evidently drift verification
$ErrorActionPreference = "Stop"
$BaseUrl = "http://localhost:8001"

function Write-Step($msg) { Write-Host "==> $msg" -ForegroundColor Cyan }

function New-DriftedFromNormal($rows) {
    $drifted = @()
    foreach ($row in $rows) {
        $drifted += [ordered]@{
            age          = [double]$row.age
            income       = [Math]::Round(([double]$row.income) * 5.0, 2)
            credit_score = [Math]::Round([Math]::Max(300, ([double]$row.credit_score) - 180), 1)
            loan_amount  = [Math]::Round(([double]$row.loan_amount) * 3.5, 2)
        }
    }
    return $drifted
}

Write-Step "Health check"
$health = Invoke-RestMethod -Uri "$BaseUrl/health" -TimeoutSec 5
if ($health.status -ne "UP") { throw "Analytics health failed" }
$health | ConvertTo-Json -Compress | Write-Host

Write-Step "Fetch production-like sample from baseline generator"
$sample = Invoke-RestMethod -Uri "$BaseUrl/api/v1/drift/samples/production-like?n=150&seed=99" -TimeoutSec 30

Write-Step "Drift check with NORMAL data (expect dataset_drift=false)"
$normalBody = @{
    model_name   = "loan-risk-model"
    current_data = @($sample.current_data)
} | ConvertTo-Json -Depth 8

$normal = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/drift/check" `
    -ContentType "application/json" -Body $normalBody -TimeoutSec 120
Write-Host ("dataset_drift={0} share={1} drifted={2}" -f $normal.dataset_drift, $normal.drift_share, $normal.number_of_drifted_features)

if ($normal.dataset_drift -eq $true) {
    throw "Phase 3 FAILED: normal batch unexpectedly reported dataset_drift=true"
}

Write-Step "Drift check with DRIFTED data (expect dataset_drift=true)"
$driftBody = @{
    model_name   = "loan-risk-model"
    current_data = @(New-DriftedFromNormal $sample.current_data)
} | ConvertTo-Json -Depth 8

$drifted = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/drift/check" `
    -ContentType "application/json" -Body $driftBody -TimeoutSec 120
Write-Host ("dataset_drift={0} share={1} drifted={2}" -f $drifted.dataset_drift, $drifted.drift_share, $drifted.number_of_drifted_features)

if ($drifted.dataset_drift -ne $true) {
    throw "Phase 3 FAILED: drifted batch did not set dataset_drift=true"
}

Write-Step "HTML report endpoint"
$tmpHtml = Join-Path $env:TEMP "neuroflow-drift-report.html"
curl.exe -sS -o $tmpHtml -w "%{http_code}" "$BaseUrl/api/v1/drift/report-html" | ForEach-Object {
    $code = $_
    if ($code -ne "200") { throw "Phase 3 FAILED: HTML report HTTP $code" }
}
$len = (Get-Item $tmpHtml).Length
if ($len -lt 1000) { throw "Phase 3 FAILED: HTML report too small ($len bytes)" }
Write-Host ("HTML report bytes: {0}" -f $len)
Remove-Item $tmpHtml -Force -ErrorAction SilentlyContinue

Write-Host ""
Write-Host "Phase 3 OK - Evidently drift detection works." -ForegroundColor Green
