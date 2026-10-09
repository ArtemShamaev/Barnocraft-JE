$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
$candidates = @()
if ($env:JAVA_HOME) { $candidates += Join-Path $env:JAVA_HOME 'bin\java.exe' }
$candidates += @(Get-Command java.exe -All -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Source)
$roots = @($env:ProgramFiles, ${env:ProgramFiles(x86)}, $env:LOCALAPPDATA)
foreach ($root in $roots) {
    if (!$root) { continue }
    foreach ($vendor in @('Java', 'Eclipse Adoptium', 'Microsoft', 'Amazon Corretto', 'BellSoft', 'Azul Systems', 'Programs\Eclipse Adoptium')) {
        $folder = Join-Path $root $vendor
        $candidates += @(Get-ChildItem -Path "$folder\*\bin\java.exe" -ErrorAction SilentlyContinue | Select-Object -ExpandProperty FullName)
    }
}
$java = $null
foreach ($candidate in ($candidates | Select-Object -Unique)) {
    if (!(Test-Path -LiteralPath $candidate)) { continue }
    # Old Windows PowerShell treats native stderr as an error stream.
    $ErrorActionPreference = 'Continue'
    $version = (& $candidate -version 2>&1 | Out-String)
    $code = $LASTEXITCODE
    $ErrorActionPreference = 'Stop'
    if ($code -eq 0 -and $version -match 'version\s+"(?<major>\d+)(?:\.(?<minor>\d+))?') {
        $major = [int]$Matches.major
        if ($major -eq 1) { $major = [int]$Matches.minor }
        if ($major -ge 17) { $java = $candidate; break }
    }
}
if (!$java) {
    Write-Host 'Java 17+ was not found. Set JAVA_HOME to your JDK folder and run run.bat again.'
    Write-Host 'Use where java and java -version to inspect the Java selected by PATH.'
    exit 1
}
if (!(Test-Path 'target\barnocraft.jar')) {
    Write-Host 'Missing target\barnocraft.jar. Build with mvn package first.'
    exit 1
}
Write-Host "Using Java: $java"
& $java -jar 'target\barnocraft.jar' @args
exit $LASTEXITCODE
