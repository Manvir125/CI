//////////////////////////////////////////////////////////////////////////
//名称：数位板通用驱动库
//版本：1.1.0.9
//作者：UGEE Technology 
//日期：2020-09-09
//////////////////////////////////////////////////////////////////////////

#ifndef _libSign_H
#define _libSign_H

#ifndef  DLL_EXPORT
#define  DLL_EXPORT __declspec(dllexport)
#else
#define  DLL_EXPORT 
#endif

#include <windows.h>

namespace UG_LIBSIGN
{

	//枚举类型定义
	//事件状态枚举
	enum EventType
	{
		EventType_Pen = 1,
		EventType_Key = 2,
		EventType_Eraser = 3,
		EventType_Wheel = 4,
		EventType_ALL = 0xfe
	};

	//笔状态枚举
	enum  PenStatus
	{
		PenStatus_Hover,				//悬浮
		PenStatus_Down,					//按下
		PenStatus_Move,					//移动
		PenStatus_Up,					//抬起
		PenStatus_Leave					//离开
	};

	//按键状态枚举
	enum KeyStatus
	{
		KeyStatus_Up,					//按键抬起
		KeyStatus_Down					//按键按下
	};

	//笔按键枚举
	enum ButtonStatus
	{
		ButtonStatus_Up,				//笔上按键抬起
		ButtonStatus_Down				//笔上按键按下
	};

	//Touch状态
	enum  TouchStatus
	{
		TouchStatus_Up,
		TouchStatus_Down,
		TouchStatus_Move
	};

	//设备连接状态
	enum  DeviceStatus
	{
		DeviceStatus_Disconnected=0,
		DeviceStatus_Connected,
		DeviceStatus_Sleep,
		DeviceStatus_Awake
	};

	//设备运行模式
	enum DeviceRunMode
	{
		DeviceRunMode_Mouse = 1,						//系统鼠标
		DeviceRunMode_Pen = 2,							//笔数据
		DeviceRunMode_MousePen = 3,						//系统鼠标+笔数据
		DeviceRunMode_StdPen = 4						//标准数位笔
	};

	//设备样式
	enum DeviceType
	{
		DeviceType_Normal = 0,							//常规设备
		DeviceType_UG05 = 1,							//UG05设备
		DeviceType_HANKSON = 2,							//HANKSON 特殊版
	};


	//////////////////////////////////////////////////////////////////////////
	//返回值定义
	#define			ERR_OK						0
	#define			ERR_DEVICE_NOTFOUND			-1
	#define			ERR_DEVICE_OPENFAIL			-2
	#define			ERR_DEVICE_NOTCONNECTED		-3
	#define			ERR_DATFILEFAIL		        -4

	#define			ERR_INVALIDPARAM			-101
	#define			ERR_NOSUPPORTED				-102


	//往期没适配 但符合28bd mi02的通用设备id
	#define			GENERAL_DEVICES_INDEX		150

	//////////////////////////////////////////////////////////////////////////
	//数据结构定义
	//数位板X/Y轴范围
	typedef		struct  tagAXIS
	{
		unsigned long	min;
		unsigned long	max;
	}AXIS, *PAXIS;

	//数位板设备信息
	typedef struct tagTABLET_DEVICEINFO
	{
		AXIS				axisX;					//X轴范围
		AXIS				axisY;					//Y轴范围
		unsigned long		pressure;				//压感级别
		char				vendor[32];				//厂商名称
		char				product[32];			//产品名称
		unsigned long		version;				//驱动接口版本
		char				serialnum[32];			//设备序列号
	}TABLET_DEVICEINFO, *PTABLET_DEVICEINFO;

	//数位板数据包
	typedef struct  tagDATAPACKET
	{
		EventType			eventtype;				//事件类型	4
		unsigned short		physical_key;			//物理按键	2
		unsigned short		virtual_key;			//虚拟按键	2
		KeyStatus			keystatus;				//按键状态	4
		PenStatus			penstatus;				//笔尖状态	4
		int 				x;// unsigned short		x;	//x坐标		2
		int 				y;//unsigned short		y;	//y坐标		2
		int 				pressure;// unsigned short		pressure;		//压感		2
		short				wheel_direction;		//转盘旋转方向标识	2
		unsigned short		button;					//笔身按键序列		2
		char				tiltX;					//斜率X
		char				tiltY;					//斜率Y
	}DATAPACKET, *PDATAPACKET;

	typedef struct tagTOUCHDATA
	{
		TouchStatus			status[10];
		unsigned int		x[10];
		unsigned int		y[10];

	}TOUCHDATA, *PTOUCHDATA;

	//设备连接状态
	typedef struct  tagSTATUSPACKET
	{
		int			penAlive;
		int			penBattery;
		int			status;						//0  DISCONNECTED  1 CONNECTED  2 SLEEP  3 AWAKE
	}STATUSPACKET, *PSTATUSPACKET;

	typedef struct  tagETAINFO
	{
		char		barcode[32];			//
	}ETAINFO;

	typedef		int (_stdcall * PACKDATAPROC)(DATAPACKET pktObj);
	typedef		int (_stdcall * DEVNOTIFYPROC)(STATUSPACKET pktObj);
	typedef		int(_stdcall * DEVOPENFYPROC)(int bOpen);
	typedef		int (_stdcall * TOUCHPROC)(TOUCHDATA td);
	typedef		int(_stdcall * ETAINFOPROC)(ETAINFO pktObj);

	#ifdef __cplusplus
	extern "C" {
	#endif 
		DLL_EXPORT	int		_stdcall	signInitialize();
		DLL_EXPORT	void	_stdcall	signClean();
		DLL_EXPORT	int		_stdcall	signGetDeviceStatus();
		DLL_EXPORT	int		_stdcall	signGetDeviceOpenStatus();
		DLL_EXPORT	int		_stdcall	signOpenDevice();
		DLL_EXPORT	int		_stdcall	signCloseDevice();

		DLL_EXPORT	int		_stdcall	signGetDeviceInfo(TABLET_DEVICEINFO* lpDeviceInfo);
		DLL_EXPORT	long	_stdcall	signRegisterDataCallBack(PACKDATAPROC lpPackDataProc);
		DLL_EXPORT	void	_stdcall	signUnregisterDataCallBack(long handler);
		DLL_EXPORT	long	_stdcall	signRegisterDevNotifyCallBack(DEVNOTIFYPROC lpDevNotifyProc);
		DLL_EXPORT	long	_stdcall	signRegisterDevOpenCallBack(DEVOPENFYPROC lpDevNotifyProc);
		
		DLL_EXPORT	void	_stdcall	signUnregisterDevOpenCallBack(long handler);
		DLL_EXPORT	void	_stdcall	signUnregisterDevNotifyCallBack(long handler);
		DLL_EXPORT  long	_stdcall	signRegisterTouchCallBack(TOUCHPROC lpTouchProc);
		DLL_EXPORT	void	_stdcall	signUnregisterTouchCallBack(long handler);

		DLL_EXPORT	int		_stdcall	signChangeDeviceMode(int mode);
		//屏幕支持API
		DLL_EXPORT	int		_stdcall	signGetScreenRect(RECT* lpRect);
		DLL_EXPORT	bool	_stdcall	signMouseControl(bool bControlled);
		DLL_EXPORT	void	_stdcall	signSetExtendDisplay(bool bExtendDisplay);
		DLL_EXPORT	int		_stdcall	signRotateMode(int mode);
		DLL_EXPORT	long	_stdcall	RegisterReadBarCodeCallBack(ETAINFOPROC lpReadBarCodeProc);
		DLL_EXPORT	void	_stdcall	OpenReadBarCode();
		DLL_EXPORT	void	_stdcall	CloseReadBarCode();
	#ifdef __cplusplus
	}
	#endif

}
#endif