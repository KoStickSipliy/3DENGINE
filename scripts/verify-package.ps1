param([string]$JdkHome = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
if (-not $JdkHome) {
    $settings = & java -XshowSettings:properties -version 2>&1
    $homeLine = $settings | Where-Object { "$_" -match '^\s*java\.home\s*=' } | Select-Object -First 1
    if (-not $homeLine) { throw 'Cannot determine JDK home; pass -JdkHome or set JAVA_HOME' }
    $JdkHome = ("$homeLine" -split '=', 2)[1].Trim()
}
foreach ($tool in @('java', 'javac', 'jar')) {
    if (-not (Test-Path -LiteralPath (Join-Path $JdkHome "bin/$tool.exe"))) {
        throw "JDK tool is missing: $tool; pass -JdkHome pointing to JDK 21"
    }
}
Push-Location (Join-Path $PSScriptRoot '..')
try {
    $sources = @(Get-ChildItem src -Recurse -File) + @(Get-Item pom.xml)
    if ($sources | Select-String -Pattern 'io\.github\.(alphameo|shimeoki)|javax\.vecmath|<artifactId>(linear_algebra|jshaper|rasterization|vecmath)</artifactId>') {
        throw 'Source or module/build configuration references a replaced library'
    }
    $jarPath = (Resolve-Path 'target/cgengine.jar').Path
    $entries = & (Join-Path $JdkHome 'bin/jar.exe') tf $jarPath
    if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect distribution jar' }
    if ($entries -match 'io/github/(alphameo|shimeoki)/|javax/vecmath/|org/junit/') {
        throw 'Distribution contains a forbidden graphics or test dependency'
    }
    if (-not ($entries -contains 'com/cgengine/fxml/gui.fxml')) { throw 'FXML is missing' }
    foreach ($required in @('javafx/application/Platform.class', 'javafx/fxml/FXMLLoader.class', 'glass.dll', 'prism_d3d.dll', 'prism_sw.dll', 'javafx_font.dll', 'javafx_iio.dll')) {
        if (-not ($entries -contains $required)) { throw "Windows JavaFX runtime entry is missing: $required" }
    }
    New-Item -ItemType Directory -Force target/package-probe | Out-Null
    & (Join-Path $JdkHome 'bin/javac.exe') -encoding UTF-8 -cp $jarPath -d target/package-probe tests/com/cgengine/PackagedSmoke.java
    if ($LASTEXITCODE -ne 0) { throw 'Smoke probe compilation failed' }
    & (Join-Path $JdkHome 'bin/java.exe') -cp "$jarPath$([IO.Path]::PathSeparator)target/package-probe" com.cgengine.PackagedSmoke
    if ($LASTEXITCODE -ne 0) { throw 'Packaged application smoke failed' }
    foreach ($sample in @(
        '3DModels_DELETE/CaracalCube/caracal_cube.obj',
        '3DModels_DELETE/eo53jaydu328-Rigged_Hand/Rigged_Hand/Rigged_Hand_obj/Rigged Hand.obj'
    )) {
        if (Test-Path -LiteralPath $sample) {
            & (Join-Path $JdkHome 'bin/java.exe') -cp "$jarPath$([IO.Path]::PathSeparator)target/package-probe" com.cgengine.PackagedSmoke $sample
            if ($LASTEXITCODE -ne 0) { throw "Packaged sample smoke failed: $sample" }
        }
    }
} finally { Pop-Location }
