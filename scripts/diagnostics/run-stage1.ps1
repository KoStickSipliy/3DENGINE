param(
    [string]$OutputDirectory = 'AI_dev/stage1-2026-10-01',
    [string[]]$Modes = @('normal', 'cached', 'no-upload')
)
$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
Set-Location -LiteralPath $projectRoot
$outputRoot = [IO.Path]::GetFullPath((Join-Path $projectRoot $OutputDirectory))
New-Item -ItemType Directory -Force -Path $outputRoot | Out-Null
$classpath = "$OutputDirectory/probe;target/cgengine.jar"
$models = [ordered]@{
    cube = 'Test_Models/cube.obj'
    tetrahedron = 'Test_Models/tetrahedron.obj'
    hand = '3DModels_DELETE/eo53jaydu328-Rigged_Hand/Rigged_Hand/Rigged_Hand_obj/Rigged Hand.obj'
    jaw = '3DModels_DELETE/Faceform/WrapJaw.obj'
    body = '3DModels_DELETE/Faceform/WrapBody.obj'
}
foreach ($mode in $Modes) {
    foreach ($entry in $models.GetEnumerator()) {
        $runDirectory = Join-Path $outputRoot "$mode-$($entry.Key)"
        if (Test-Path -LiteralPath $runDirectory) { throw "Refusing to overwrite diagnostic run: $runDirectory" }
        New-Item -ItemType Directory -Path $runDirectory | Out-Null
        # Native JVM startup options mishandle Cyrillic absolute paths on this JDK.
        # Relative ASCII paths retain the exact same files without that conversion.
        $relativeRun = "$OutputDirectory/$mode-$($entry.Key)"
        Write-Output "Starting $mode / $($entry.Key)"
        & java '-Dcgengine.diagnostics=true' "-Dcgengine.diagnostics.mode=$mode" `
            "-XX:ErrorFile=$relativeRun/hs_err_pid%p.log" `
            "-XX:StartFlightRecording=filename=$relativeRun/session.jfr,settings=profile,dumponexit=true" `
            -cp $classpath com.cgengine.Stage1Probe $entry.Value $relativeRun *> (Join-Path $runDirectory 'session.log')
        $result = $LASTEXITCODE
        $result | Set-Content (Join-Path $runDirectory 'exit-code.txt')
        Write-Output "Finished $mode / $($entry.Key): exit=$result"
    }
}
