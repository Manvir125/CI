param(
    [string]$ExePath = "$(Split-Path -Parent $PSScriptRoot)\publish\xppen-bridge.exe",
    [string]$TaskName = "CHPC XP Pen Bridge"
)

$ErrorActionPreference = "Stop"

$resolvedExe = Resolve-Path -LiteralPath $ExePath
$workingDirectory = Split-Path -Parent $resolvedExe.Path

$action = New-ScheduledTaskAction `
    -Execute $resolvedExe.Path `
    -WorkingDirectory $workingDirectory

$trigger = New-ScheduledTaskTrigger -AtLogOn
$settings = New-ScheduledTaskSettingsSet `
    -AllowStartIfOnBatteries `
    -DontStopIfGoingOnBatteries `
    -ExecutionTimeLimit (New-TimeSpan -Days 365)

Register-ScheduledTask `
    -TaskName $TaskName `
    -Action $action `
    -Trigger $trigger `
    -Settings $settings `
    -Description "Arranca el puente local WebSocket de XP Pen para la firma presencial CHPC." `
    -Force | Out-Null

Write-Host "Tarea registrada: $TaskName"
Write-Host "Ejecutable: $($resolvedExe.Path)"
Write-Host "Puedes iniciarla ahora con: Start-ScheduledTask -TaskName `"$TaskName`""
