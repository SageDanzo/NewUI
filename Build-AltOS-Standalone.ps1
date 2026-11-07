$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot

if ($env:JAVA_HOME) {
    $jdkHome = $env:JAVA_HOME
} else {
    $javac = Get-Command javac.exe -ErrorAction Stop
    $jdkHome = Split-Path (Split-Path $javac.Source -Parent) -Parent
}

$javaRuntime = Join-Path $jdkHome 'jre'
if (-not (Test-Path (Join-Path $javaRuntime 'bin\javaw.exe'))) {
    throw "A 64-bit Java 8 JDK with a bundled jre folder is required. Set JAVA_HOME to that JDK. Checked: $jdkHome"
}
$dotnet = Get-Command dotnet.exe -ErrorAction SilentlyContinue
if (-not $dotnet) {
    throw '.NET 8 SDK is required to build the standalone launcher and recovery bridge.'
}

$buildDirectory = Join-Path $root 'build'
$javaBuildDirectory = Join-Path $buildDirectory 'java'
$classesDirectory = Join-Path $javaBuildDirectory 'classes'
$applicationJar = Join-Path $javaBuildDirectory 'altosui-redesigned.jar'
$launcherOutput = Join-Path $buildDirectory 'launcher'
$packageDirectory = Join-Path $buildDirectory 'AltOS-Modern-Portable'
New-Item -ItemType Directory -Force -Path $classesDirectory, $launcherOutput, $packageDirectory | Out-Null

$javac = Join-Path $jdkHome 'bin\javac.exe'
$jar = Join-Path $jdkHome 'bin\jar.exe'
if (-not (Test-Path $javac) -or -not (Test-Path $jar)) {
    throw "The Java 8 JDK compiler tools were not found in: $jdkHome"
}

$classPath = @(
    (Join-Path $root 'altosui-redesigned.jar'),
    (Join-Path $root 'altoslib_14.jar'),
    (Join-Path $root 'altosuilib_14.jar'),
    (Join-Path $root 'freetts.jar')
) -join ';'
& $javac -encoding UTF-8 -source 8 -target 8 -classpath $classPath -d $classesDirectory `
    (Join-Path $root 'AltosDesktopTheme.java') `
    (Join-Path $root 'AltosRecoveryLink.java') `
    (Join-Path $root 'AltosFlightVoice.java') `
    (Join-Path $root 'AltosUI.java')
if ($LASTEXITCODE -ne 0) {
    throw 'Compiling the redesigned AltOS application failed.'
}
Copy-Item (Join-Path $root 'altosui-redesigned.jar') $applicationJar -Force
& $jar uf $applicationJar -C $classesDirectory altosui -C $classesDirectory org\altusmetrum\altosuilib_14\AltosFlightVoice.class
if ($LASTEXITCODE -ne 0) {
    throw 'Updating the packaged AltOS application JAR failed.'
}

$bridgeOutput = Join-Path $root 'recovery-bridge\publish'
& $dotnet.Source publish (Join-Path $root 'recovery-bridge\AltosRecoveryBridge.csproj') `
    --configuration Release `
    --runtime win-x64 `
    --self-contained true `
    --output $bridgeOutput
if ($LASTEXITCODE -ne 0) {
    throw 'Building the AltOS recovery bridge failed.'
}

& dotnet.exe publish (Join-Path $root 'launcher\AltosModernLauncher.csproj') `
    --configuration Release `
    --runtime win-x64 `
    --self-contained true `
    --output $launcherOutput
if ($LASTEXITCODE -ne 0) {
    throw 'Publishing the AltOS launcher failed.'
}

Copy-Item (Join-Path $launcherOutput 'AltOS-Modern.exe') $packageDirectory -Force
Copy-Item $javaRuntime (Join-Path $packageDirectory 'runtime') -Recurse -Force

$assets = Get-ChildItem -LiteralPath $root -File |
    Where-Object { $_.Extension -in '.jar', '.dll', '.ihx', '.pdf', '.ico', '.cat', '.inf' }
foreach ($asset in $assets) {
    Copy-Item $asset.FullName $packageDirectory -Force
}
Copy-Item $applicationJar (Join-Path $packageDirectory 'altosui-redesigned.jar') -Force

$bridgeSource = Join-Path $root 'recovery-bridge\publish\AltosRecoveryBridge.exe'
if (-not (Test-Path $bridgeSource)) {
    throw "The recovery bridge executable was not produced: $bridgeSource"
}
$bridgeDirectory = Join-Path $packageDirectory 'recovery-bridge\publish'
New-Item -ItemType Directory -Force -Path $bridgeDirectory | Out-Null
Copy-Item $bridgeSource $bridgeDirectory -Force
Copy-Item (Join-Path $root 'README-FIRST.txt') $packageDirectory -Force

Write-Host "Portable AltOS package created at: $packageDirectory"
Write-Host 'Launch AltOS-Modern.exe from that folder. Java is bundled; no system Java install is needed.'
