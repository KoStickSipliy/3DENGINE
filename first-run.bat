@echo off
setlocal
set "CGENGINE_LAUNCHER=%~f0"
pushd "%~dp0" || exit /b 1
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -Command "try { $text = [IO.File]::ReadAllText($env:CGENGINE_LAUNCHER); & ([scriptblock]::Create(($text -split '\r?\n# POWERSHELL START\r?\n', 2)[1])) } catch { Write-Host ('ERROR: ' + $_.Exception.Message) -ForegroundColor Red; exit 1 }"
if errorlevel 1 goto failed
popd
exit /b 0

:failed
echo Install JDK 21 or newer and Apache Maven 3.9 or newer.
echo Set JAVA_HOME and add Maven bin to PATH, then run this file again.
echo The first build needs internet access to download Maven dependencies.
pause
popd
exit /b 1

# POWERSHELL START
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath (Split-Path -LiteralPath $env:CGENGINE_LAUNCHER)
if (-not (Test-Path -LiteralPath 'pom.xml' -PathType Leaf)) {
    throw 'pom.xml is missing. Keep this launcher in the project root.'
}

# Use a single JDK for Maven, compilation, package checks and execution.
$jdkHome = $env:JAVA_HOME
if (-not $jdkHome) {
    if (-not (Get-Command java.exe -ErrorAction SilentlyContinue)) {
        throw 'Java was not found. Install a JDK and set JAVA_HOME.'
    }
    $ErrorActionPreference = 'Continue'
    $settings = & java.exe -XshowSettings:properties -version 2>&1 | ForEach-Object { "$_" }
    $ErrorActionPreference = 'Stop'
    if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect the installed Java runtime.' }
    $homeLine = $settings | Where-Object { $_ -match '^\s*java\.home\s*=' } | Select-Object -First 1
    if (-not $homeLine) { throw 'Cannot determine JAVA_HOME.' }
    $jdkHome = ($homeLine -split '=', 2)[1].Trim()
}
foreach ($tool in @('java', 'javac', 'jar')) {
    if (-not (Test-Path -LiteralPath (Join-Path $jdkHome "bin/$tool.exe") -PathType Leaf)) {
        throw "Missing JDK tool: $tool. JAVA_HOME must point to a full JDK."
    }
}
$env:JAVA_HOME = $jdkHome
$env:PATH = (Join-Path $jdkHome 'bin') + ';' + $env:PATH
$ErrorActionPreference = 'Continue'
$compilerVersion = (& javac.exe -version 2>&1 | ForEach-Object { "$_" }) -join "`n"
$ErrorActionPreference = 'Stop'
if ($LASTEXITCODE -ne 0 -or $compilerVersion -notmatch 'javac\s+(\d+)' -or [int]$Matches[1] -lt 21) {
    throw "JDK 21 or newer is required. Found: $compilerVersion"
}
$modules = & java.exe --list-modules
if ($LASTEXITCODE -ne 0) { throw 'Cannot list JDK modules.' }
foreach ($module in @('java.base', 'java.desktop', 'jdk.compiler')) {
    if (-not ($modules -match ('^' + [regex]::Escape($module) + '@'))) {
        throw "Required JDK module is missing: $module"
    }
}
if ($env:MAVEN_HOME) { $env:PATH = (Join-Path $env:MAVEN_HOME 'bin') + ';' + $env:PATH }
$maven = Get-Command mvn.cmd -ErrorAction SilentlyContinue
if (-not $maven) { throw 'Apache Maven was not found. Add its bin directory to PATH.' }
$ErrorActionPreference = 'Continue'
$mavenVersion = (& $maven.Source -version 2>&1 | ForEach-Object { "$_" }) -join "`n"
$ErrorActionPreference = 'Stop'
if ($LASTEXITCODE -ne 0 -or $mavenVersion -notmatch 'Apache Maven (\d+\.\d+\.\d+)' -or [version]$Matches[1] -lt [version]'3.9.0') {
    throw "Maven 3.9 or newer is required. Found: $mavenVersion"
}
Write-Host $compilerVersion
Write-Host $mavenVersion
Write-Host 'Building for this Windows device; Maven downloads JavaFX and build dependencies as needed.'
& $maven.Source clean package '-Dmaven.test.skip=true'
if ($LASTEXITCODE -ne 0) { throw 'Maven build failed. Check the output, internet access and Maven configuration.' }
if (-not (Test-Path -LiteralPath 'target/cgengine.jar' -PathType Leaf)) {
    throw 'The build did not produce target/cgengine.jar.'
}
$entries = & jar.exe tf 'target/cgengine.jar'
if ($LASTEXITCODE -ne 0) { throw 'Cannot read the application JAR.' }
foreach ($entry in @('com/cgengine/Main.class', 'com/cgengine/fxml/gui.fxml', 'javafx/application/Platform.class', 'javafx/scene/control/Control.class', 'javafx/fxml/FXMLLoader.class', 'glass.dll', 'prism_d3d.dll', 'prism_sw.dll', 'javafx_font.dll', 'javafx_iio.dll')) {
    if ($entries -notcontains $entry) { throw "Required application resource or JavaFX component is missing: $entry" }
}
Write-Host 'Checks passed. Starting CGEngine...'
& java.exe -jar '.\target\cgengine.jar'
if ($LASTEXITCODE -ne 0) { throw 'Application startup or execution failed. See the Java output above.' }
