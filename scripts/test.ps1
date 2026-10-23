# Compiles the main and test sources, then runs the whole test suite.
$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
$mainOutput = Join-Path $root 'out\main'
$testOutput = Join-Path $root 'out\test'
$mainSources = Join-Path $root 'src\main\java'
$testSources = Join-Path $root 'src\test\java'

& (Join-Path $PSScriptRoot 'compile.ps1')
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

New-Item -ItemType Directory -Force -Path $testOutput | Out-Null
$sources = Get-ChildItem -Recurse -File -Path $testSources -Filter *.java |
    ForEach-Object { $_.FullName }

& javac --release 17 -encoding UTF-8 -Xlint:all -cp $mainOutput -d $testOutput $sources
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

& java -cp "$mainOutput;$testOutput" com.eventmanagement.tests.TestRunner
exit $LASTEXITCODE
