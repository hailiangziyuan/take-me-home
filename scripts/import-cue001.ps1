param(
    [Parameter(Mandatory = $true)]
    [string]$SourceMp3
)

$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
$source = Resolve-Path -LiteralPath $SourceMp3
$target = Join-Path $projectRoot "app\src\main\res\raw\cue_001.mp3"

Copy-Item -LiteralPath $source -Destination $target -Force
Write-Host "Imported $source -> $target"
