# XP Pen Bridge

Puente local WebSocket para la tableta XP Pen. Expone `ws://localhost:5100` para que el frontend de firma presencial reciba eventos del lapiz.

## Publicar ejecutable

```powershell
.\scripts\Publish-XPPenBridge.ps1
```

El ejecutable queda en:

```text
xppen-bridge\publish\xppen-bridge.exe
```

Este modo requiere tener instalado el runtime de .NET usado por el proyecto. Para generar un ejecutable autocontenido:

```powershell
.\scripts\Publish-XPPenBridge.ps1 -SelfContained
```

El modo autocontenido puede necesitar acceso a NuGet para descargar los runtimes de Windows.

## Arrancar al iniciar Windows

Ejecutar PowerShell como el usuario que usara la tableta:

```powershell
.\scripts\Install-XPPenBridgeStartupTask.ps1
```

La tarea se registra al inicio de sesion como `CHPC XP Pen Bridge`.

Para arrancarla sin reiniciar:

```powershell
Start-ScheduledTask -TaskName "CHPC XP Pen Bridge"
```

Para quitarla:

```powershell
.\scripts\Uninstall-XPPenBridgeStartupTask.ps1
```
