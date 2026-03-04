using System.Collections.Concurrent;
using System.Net;
using System.Net.WebSockets;
using System.Text;
using System.Text.Json;

namespace XPPenBridge;

/// <summary>
/// WebSocket bridge between XP Pen tablet SDK and the browser kiosk app.
/// Listens on ws://localhost:5100 and streams pen events as JSON.
/// </summary>
class Program
{
    static readonly ConcurrentDictionary<string, WebSocket> _clients = new();

    // SDK handles
    static int _dataHandle;
    static int _notifyHandle;

    // Keep delegates alive (prevent GC collection of the callback pointers)
    static DATAPACKETPROC? _dataCallback;
    static DEVNOTIFYPROC?  _notifyCallback;

    // Device info for coordinate scaling
    static TABLET_DEVICEINFO _devInfo;
    static bool _deviceOpen;

    static async Task Main(string[] args)
    {
        var port = 5100;
        Console.WriteLine($"╔══════════════════════════════════════════╗");
        Console.WriteLine($"║  XP Pen Bridge — WebSocket ::{port}       ║");
        Console.WriteLine($"╚══════════════════════════════════════════╝");

        // ── Initialize SDK ──────────────────────────────────────────────
        var initResult = SignAPI.signInitialize();
        Console.WriteLine($"[SDK] signInitialize → {initResult}");

        // Try to open device
        TryOpenDevice();

        // Register device-status callback to detect connect/disconnect
        _notifyCallback = OnDeviceNotify;
        _notifyHandle = SignAPI.signRegisterDevNotifyCallBack(_notifyCallback);
        Console.WriteLine($"[SDK] Notify callback registered (handle={_notifyHandle})");

        // ── Start WebSocket server ──────────────────────────────────────
        var listener = new HttpListener();
        listener.Prefixes.Add($"http://localhost:{port}/");
        listener.Start();
        Console.WriteLine($"[WS]  Listening on ws://localhost:{port}/");
        Console.WriteLine("      Ctrl+C to stop.\n");

        Console.CancelKeyPress += (_, e) =>
        {
            e.Cancel = true;
            Cleanup();
            Environment.Exit(0);
        };

        while (true)
        {
            var ctx = await listener.GetContextAsync();
            if (ctx.Request.IsWebSocketRequest)
            {
                _ = HandleClientAsync(ctx);
            }
            else
            {
                // Simple health-check / CORS preflight
                ctx.Response.StatusCode = 200;
                ctx.Response.Headers.Add("Access-Control-Allow-Origin", "*");
                var body = Encoding.UTF8.GetBytes("{\"status\":\"ok\"}");
                ctx.Response.ContentType = "application/json";
                await ctx.Response.OutputStream.WriteAsync(body);
                ctx.Response.Close();
            }
        }
    }

    // ── Device management ────────────────────────────────────────────────
    static void TryOpenDevice()
    {
        if (_deviceOpen) return;

        var status = SignAPI.signGetDeviceStatus();
        if (status != ErrorCode.OK)
        {
            Console.WriteLine($"[SDK] No device found (status={status})");
            return;
        }

        var openResult = SignAPI.signOpenDevice();
        if (openResult != ErrorCode.OK)
        {
            Console.WriteLine($"[SDK] signOpenDevice failed ({openResult})");
            return;
        }

        _devInfo = new TABLET_DEVICEINFO();
        SignAPI.signGetDeviceInfo(ref _devInfo);
        Console.WriteLine($"[SDK] Device opened: {_devInfo.vendor} {_devInfo.product}");
        Console.WriteLine($"      Axis X: 0–{_devInfo.axisX.max}  Y: 0–{_devInfo.axisY.max}  Pressure: {_devInfo.pressure}");

        // Switch to PEN mode so we receive raw data, not mouse events
        SignAPI.signChangeDeviceMode((int)DeviceRunMode.Pen);
        // Disable mouse control so the pen doesn't move the system cursor
        SignAPI.signMouseControl(false);

        // Register pen-data callback
        _dataCallback = OnPenData;
        _dataHandle = SignAPI.signRegisterDataCallBack(_dataCallback);
        Console.WriteLine($"[SDK] Data callback registered (handle={_dataHandle})");

        _deviceOpen = true;

        // Broadcast device-connected to all clients
        BroadcastAsync(new
        {
            type = "device",
            status = "connected",
            maxX = (int)_devInfo.axisX.max,
            maxY = (int)_devInfo.axisY.max,
            maxPressure = (int)_devInfo.pressure,
            product = _devInfo.product?.Trim(),
            vendor = _devInfo.vendor?.Trim()
        });
    }

    static int OnPenData(DATAPACKET pkt)
    {
        if (pkt.eventtype != EventType.Pen) return 0;

        var msg = new
        {
            type = "pen",
            x = pkt.x,
            y = pkt.y,
            pressure = pkt.pressure,
            status = pkt.penstatus.ToString(), // "Hover","Down","Move","Up","Leave"
            maxX = (int)_devInfo.axisX.max,
            maxY = (int)_devInfo.axisY.max,
            maxPressure = (int)_devInfo.pressure
        };

        BroadcastAsync(msg);
        return 0;
    }

    static int OnDeviceNotify(STATUSPACKET packet)
    {
        var s = (DeviceStatus)packet.status;
        Console.WriteLine($"[SDK] Device status: {s}");

        if (s == DeviceStatus.Connected || s == DeviceStatus.Awake)
        {
            TryOpenDevice();
        }
        else if (s == DeviceStatus.Disconnected)
        {
            _deviceOpen = false;
            BroadcastAsync(new { type = "device", status = "disconnected" });
        }

        return 0;
    }

    // ── WebSocket handling ───────────────────────────────────────────────
    static async Task HandleClientAsync(HttpListenerContext ctx)
    {
        var wsCtx = await ctx.AcceptWebSocketAsync(null);
        var ws = wsCtx.WebSocket;
        var clientId = Guid.NewGuid().ToString("N")[..8];
        _clients[clientId] = ws;
        Console.WriteLine($"[WS]  Client {clientId} connected ({_clients.Count} total)");

        // Send current device status immediately
        if (_deviceOpen)
        {
            var info = new
            {
                type = "device",
                status = "connected",
                maxX = (int)_devInfo.axisX.max,
                maxY = (int)_devInfo.axisY.max,
                maxPressure = (int)_devInfo.pressure,
                product = _devInfo.product?.Trim(),
                vendor = _devInfo.vendor?.Trim()
            };
            await SendAsync(ws, info);
        }
        else
        {
            await SendAsync(ws, new { type = "device", status = "disconnected" });
        }

        // Read loop (for future commands from the browser)
        var buf = new byte[1024];
        try
        {
            while (ws.State == WebSocketState.Open)
            {
                var result = await ws.ReceiveAsync(buf, CancellationToken.None);
                if (result.MessageType == WebSocketMessageType.Close)
                    break;

                // Parse incoming command
                var json = Encoding.UTF8.GetString(buf, 0, result.Count);
                try
                {
                    var cmd = JsonSerializer.Deserialize<JsonElement>(json);
                    var cmdType = cmd.GetProperty("type").GetString();

                    if (cmdType == "ping")
                    {
                        await SendAsync(ws, new { type = "pong" });
                    }
                    else if (cmdType == "status")
                    {
                        await SendAsync(ws, new
                        {
                            type = "device",
                            status = _deviceOpen ? "connected" : "disconnected"
                        });
                    }
                }
                catch { /* ignore malformed messages */ }
            }
        }
        catch (WebSocketException) { /* client disconnected */ }
        finally
        {
            _clients.TryRemove(clientId, out _);
            Console.WriteLine($"[WS]  Client {clientId} disconnected ({_clients.Count} total)");
            if (ws.State != WebSocketState.Closed)
                try { await ws.CloseAsync(WebSocketCloseStatus.NormalClosure, "", CancellationToken.None); } catch { }
        }
    }

    // ── Broadcasting ────────────────────────────────────────────────────
    static readonly JsonSerializerOptions _jsonOpts = new()
    {
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase
    };

    static void BroadcastAsync(object message)
    {
        var json = JsonSerializer.Serialize(message, _jsonOpts);
        var bytes = Encoding.UTF8.GetBytes(json);
        var segment = new ArraySegment<byte>(bytes);

        foreach (var (id, ws) in _clients)
        {
            if (ws.State == WebSocketState.Open)
            {
                // Fire-and-forget; errors handled per-client
                _ = Task.Run(async () =>
                {
                    try
                    {
                        await ws.SendAsync(segment, WebSocketMessageType.Text, true, CancellationToken.None);
                    }
                    catch
                    {
                        _clients.TryRemove(id, out _);
                    }
                });
            }
            else
            {
                _clients.TryRemove(id, out _);
            }
        }
    }

    static async Task SendAsync(WebSocket ws, object message)
    {
        var json = JsonSerializer.Serialize(message, _jsonOpts);
        var bytes = Encoding.UTF8.GetBytes(json);
        await ws.SendAsync(bytes, WebSocketMessageType.Text, true, CancellationToken.None);
    }

    // ── Cleanup ─────────────────────────────────────────────────────────
    static void Cleanup()
    {
        Console.WriteLine("\n[SDK] Cleaning up...");
        if (_dataHandle > 0)   SignAPI.signUnregisterDataCallBack(_dataHandle);
        if (_notifyHandle > 0) SignAPI.signUnregisterDevNotifyCallBack(_notifyHandle);
        SignAPI.signCloseDevice();
        SignAPI.signClean();
    }
}
