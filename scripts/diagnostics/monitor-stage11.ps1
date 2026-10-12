param([string]$Log = 'AI_dev/stage11-2026-10-02/full-verify.log',
      [string]$Output = 'AI_dev/stage11-2026-10-02/native-resources.csv')
$ErrorActionPreference = 'Stop'
$taskDeadline = (Get-Date).AddMinutes(55)
$taskProcessId = $null
while ((Get-Date) -lt $taskDeadline) {
    if (!$taskProcessId -and (Test-Path -LiteralPath $Log)) {
        $taskMatch = Select-String -LiteralPath $Log -Pattern 'Stage11 mixed START pid=(\d+)' | Select-Object -Last 1
        if ($taskMatch) { $taskProcessId = [int]$taskMatch.Matches[0].Groups[1].Value }
    }
    if ($taskProcessId) {
        $taskProcess = Get-Process -Id $taskProcessId -ErrorAction SilentlyContinue
        if (!$taskProcess) { break }
        [pscustomobject]@{
            Time = (Get-Date).ToString('o')
            ProcessId = $taskProcessId
            Handles = $taskProcess.HandleCount
            NativeThreads = $taskProcess.Threads.Count
            WorkingSetBytes = $taskProcess.WorkingSet64
            PrivateBytes = $taskProcess.PrivateMemorySize64
        } | Export-Csv -LiteralPath $Output -NoTypeInformation -Append
        if (Select-String -LiteralPath $Log -Pattern 'Stage11 mixed COMPLETE' -Quiet) { break }
    }
    Start-Sleep -Seconds 30
}
