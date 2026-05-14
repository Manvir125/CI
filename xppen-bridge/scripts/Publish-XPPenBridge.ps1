param(
    [string]$Configuration = "Release",
    [string]$OutputPath = "$(Split-Path -Parent $PSScriptRoot)\publish",
    [string]$Runtime = "win-x64",
    [switch]$SelfContained,
    [switch]$NoRestore
)

$ErrorActionPreference = "Stop"

$projectPath = Join-Path (Split-Path -Parent $PSScriptRoot) "xppen-bridge.csproj"
$selfContainedValue = if ($SelfContained.IsPresent) { "true" } else { "false" }

$publishArgs = @(
    "publish",
    $projectPath,
    "-c", $Configuration,
    "--self-contained", $selfContainedValue,
    "-p:RestoreIgnoreFailedSources=true",
    "-p:NuGetAudit=false",
    "-o", $OutputPath
)

if ($SelfContained) {
    $publishArgs += @(
        "-r", $Runtime,
        "-p:PublishSingleFile=true",
        "-p:IncludeNativeLibrariesForSelfExtract=true"
    )
}

if ($NoRestore) {
    $publishArgs += "--no-restore"
}

dotnet @publishArgs

if ($LASTEXITCODE -ne 0) {
    throw "dotnet publish fallo con codigo $LASTEXITCODE"
}

Write-Host "XP Pen Bridge publicado en: $OutputPath"
Write-Host "Ejecutable: $(Join-Path $OutputPath 'xppen-bridge.exe')"
