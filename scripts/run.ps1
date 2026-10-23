# Compiles and starts the interactive console application.
$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot

& (Join-Path $PSScriptRoot 'compile.ps1')
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

& java -cp (Join-Path $root 'out\main') com.eventmanagement.Main @args
exit $LASTEXITCODE
