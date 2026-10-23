# Compiles the main sources into out/main.
$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
$sourceRoot = Join-Path $root 'src\main\java'
$outputRoot = Join-Path $root 'out\main'

if (-not (Test-Path $sourceRoot)) {
    Write-Error "Source directory not found: $sourceRoot"
    exit 1
}

New-Item -ItemType Directory -Force -Path $outputRoot | Out-Null
$sources = Get-ChildItem -Recurse -File -Path $sourceRoot -Filter *.java |
    ForEach-Object { $_.FullName }

if (-not $sources) {
    Write-Error "No Java sources found under $sourceRoot"
    exit 1
}

& javac --release 17 -encoding UTF-8 -Xlint:all -d $outputRoot $sources
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

Write-Host "Compiled $($sources.Count) source files into $outputRoot"
