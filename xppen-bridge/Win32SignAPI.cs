using System;
using System.Runtime.InteropServices;

namespace XPPenBridge;

// ── Event types ──────────────────────────────────────────────────────────────
public enum EventType
{
    Pen    = 1,
    Key    = 2,
    Eraser = 3,
    Wheel  = 4,
    All    = 0xFE
}

public enum PenStatus
{
    Hover,
    Down,
    Move,
    Up,
    Leave
}

public enum KeyStatus
{
    Up,
    Down
}

public enum DeviceStatus
{
    Disconnected,
    Connected,
    Sleep,
    Awake
}

public enum DeviceRunMode
{
    Mouse    = 1,
    Pen      = 2,
    MousePen = 3,
    StdPen   = 4
}

// ── Structs ──────────────────────────────────────────────────────────────────
[StructLayout(LayoutKind.Sequential)]
public struct RECT
{
    public int left, top, right, bottom;
    public int Width  => right - left;
    public int Height => bottom - top;
}

[StructLayout(LayoutKind.Sequential)]
public struct AXIS
{
    public uint min, max;
}

[StructLayout(LayoutKind.Sequential)]
public struct TABLET_DEVICEINFO
{
    public AXIS axisX;
    public AXIS axisY;
    public uint pressure;
    [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 32)]
    public string vendor;
    [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 32)]
    public string product;
    public uint version;
    [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 32)]
    public string serialnum;
}

[StructLayout(LayoutKind.Sequential)]
public struct DATAPACKET
{
    public EventType eventtype;
    public ushort physical_key;
    public ushort virtual_key;
    public KeyStatus keystatus;
    public PenStatus penstatus;
    public int x;
    public int y;
    public int pressure;
    public short wheel_direction;
    public ushort button;
    public byte tiltX;
    public byte tiltY;
}

[StructLayout(LayoutKind.Sequential)]
public struct STATUSPACKET
{
    public int penAlive;
    public int penBattery;
    public int status; // 0=DISCONNECTED, 1=CONNECTED, 2=SLEEP, 3=AWAKE
}

// ── Delegates ────────────────────────────────────────────────────────────────
public delegate int DATAPACKETPROC(DATAPACKET pkt);
public delegate int DEVNOTIFYPROC(STATUSPACKET status);

// ── Error codes ──────────────────────────────────────────────────────────────
public static class ErrorCode
{
    public const int OK                 =    0;
    public const int DEVICE_NOTFOUND   =   -1;
    public const int DEVICE_OPENFAIL   =   -2;
    public const int DEVICE_NOTCONNECTED = -3;
    public const int INVALIDPARAM      = -101;
    public const int NOSUPPORTED       = -102;
}

// ── P/Invoke wrapper ─────────────────────────────────────────────────────────
public static class SignAPI
{
    [DllImport("libSign.dll")] public static extern int  signInitialize();
    [DllImport("libSign.dll")] public static extern void signClean();
    [DllImport("libSign.dll")] public static extern int  signGetDeviceStatus();
    [DllImport("libSign.dll")] public static extern int  signOpenDevice();
    [DllImport("libSign.dll")] public static extern int  signCloseDevice();

    [DllImport("libSign.dll", CharSet = CharSet.Ansi)]
    public static extern int signGetDeviceInfo(ref TABLET_DEVICEINFO info);

    [DllImport("libSign.dll")]
    public static extern int signRegisterDataCallBack(DATAPACKETPROC proc);
    [DllImport("libSign.dll")]
    public static extern void signUnregisterDataCallBack(int handle);

    [DllImport("libSign.dll")]
    public static extern int signRegisterDevNotifyCallBack(DEVNOTIFYPROC proc);
    [DllImport("libSign.dll")]
    public static extern void signUnregisterDevNotifyCallBack(int handle);

    [DllImport("libSign.dll")]
    public static extern int signGetScreenRect(ref RECT rect);
    [DllImport("libSign.dll")]
    public static extern int signMouseControl(bool enabled);
    [DllImport("libSign.dll")]
    public static extern int signChangeDeviceMode(int mode);
}
