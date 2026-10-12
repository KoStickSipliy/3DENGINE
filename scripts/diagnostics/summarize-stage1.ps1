param([string]$Directory = 'AI_dev/stage1-2026-10-01')
$ErrorActionPreference = 'Stop'
$culture = [Globalization.CultureInfo]::InvariantCulture
$results = foreach ($run in Get-ChildItem -LiteralPath $Directory -Directory) {
    $logPath = Join-Path $run.FullName 'session.log'
    if (-not (Test-Path -LiteralPath $logPath) -or $run.Name -like '*startup-failed') { continue }
    $lines = Get-Content -LiteralPath $logPath
    $metrics = @{}
    $observedMax = @{}
    foreach ($line in $lines) {
        if ($line -match 'slow ([\w-]+) ms=([\d.]+)') {
            $name = $Matches[1]; $value = [double]::Parse($Matches[2], $culture)
            if (-not $observedMax.ContainsKey($name)) { $observedMax[$name] = 0.0 }
            $observedMax[$name] = [Math]::Max($observedMax[$name], $value)
        }
        if ($line -match 'aggregate ([\w-]+) n=(\d+) meanMs=([\d.]+) p95Ms=([\d.]+) maxMs=([\d.]+)') {
            $name = $Matches[1]; $n = [int]$Matches[2]
            $mean = [double]::Parse($Matches[3], $culture); $max = [double]::Parse($Matches[5], $culture)
            if (-not $metrics.ContainsKey($name)) { $metrics[$name] = @{ N=0; Sum=0.0; Max=0.0 } }
            $metrics[$name].N += $n; $metrics[$name].Sum += $mean*$n
            $metrics[$name].Max = [Math]::Max($metrics[$name].Max, $max)
        }
    }
    $fields = [ordered]@{ Run=$run.Name; Exit=(Get-Content (Join-Path $run.FullName 'exit-code.txt') -ErrorAction SilentlyContinue); CompletedActions=@($lines | Where-Object { $_ -match 'action-next-pulse ' }).Count; OOM=[bool]($lines -match 'OutOfMemoryError'); Timeout=[bool]($lines -match 'TimeoutException'); ThreadDumps=@(Get-ChildItem $run.FullName -Filter 'threads-*.txt').Count }
    foreach ($metric in @('cpu-render','canvas-upload','frame-handler','pulse-gap','ui-queue','action-queue','action-to-next-pulse')) {
        if ($metrics.ContainsKey($metric)) {
            $fields["$metric-meanMs"] = [Math]::Round($metrics[$metric].Sum/$metrics[$metric].N,3)
            $fields["$metric-maxMs"] = $metrics[$metric].Max
        }
        if ($observedMax.ContainsKey($metric)) {
            $previousMax = if ($fields.Contains("$metric-maxMs")) { $fields["$metric-maxMs"] } else { 0.0 }
            $fields["$metric-maxMs"] = [Math]::Max($previousMax,$observedMax[$metric])
        }
    }
    [PSCustomObject]$fields
}
$results | ConvertTo-Json -Depth 5 | Set-Content (Join-Path $Directory 'summary.json')
$results | Select-Object Run,Exit,CompletedActions,OOM,Timeout,ThreadDumps,'cpu-render-meanMs','canvas-upload-meanMs','pulse-gap-maxMs','ui-queue-maxMs' | Format-Table -AutoSize
# Only sample counts/weighted means/maxima can be combined from interval summaries.
# A percentile of interval percentiles would not be a whole-session percentile.
