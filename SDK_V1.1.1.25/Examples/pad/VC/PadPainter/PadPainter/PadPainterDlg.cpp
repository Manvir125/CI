
// PadPainterDlg.cpp : implementation file
//

#include "stdafx.h"
#include "PadPainter.h"
#include "PadPainterDlg.h"
#include "afxdialogex.h"
#include <list>
using namespace std;

#ifdef _DEBUG
//#define new DEBUG_NEW
#endif

CPadPainterDlg*				g_lpPadPainterDlg = nullptr;
extern CWorkViewDlg*		g_lpWorkViewDlg;
list<DATAPACKET >			g_listData;
string						strcode;
HANDLE				g_eventData = ::CreateEvent(NULL,TRUE,TRUE,NULL);
// CPadPainterDlg dialog


CPadPainterDlg::CPadPainterDlg(CWnd* pParent /*=NULL*/)
	: CDialogEx(IDD_PADPAINTER_DIALOG, pParent)
{
	m_hIcon = AfxGetApp()->LoadIcon(IDR_MAINFRAME);
}

void CPadPainterDlg::DoDataExchange(CDataExchange* pDX)
{
	CDialogEx::DoDataExchange(pDX);
	DDX_Control(pDX, IDC_STATIC_LAYOUT, m_Static_Layout);
	DDX_Control(pDX, IDC_BTN_PENCOLOR, m_Btn_PenColor);
	DDX_Control(pDX, IDC_EDIT_PENWIDTH, m_Edit_PenWidth);
	DDX_Control(pDX, IDC_BTN_PENDEVICE, m_Btn_OpenDevice);
	DDX_Control(pDX, IDC_BTN_CLOSEDEVICE, m_Btn_CloseDevice);
	DDX_Control(pDX, IDC_EDIT2, m_Edit_PKey);
	DDX_Control(pDX, IDC_EDIT3, m_Edit_VKey);
	DDX_Control(pDX, IDC_EDIT4, m_Edit_Button);
}

BEGIN_MESSAGE_MAP(CPadPainterDlg, CDialogEx)
	ON_WM_PAINT()
//	ON_WM_QUERYDRAGICON()
	ON_WM_DESTROY()
	ON_BN_CLICKED(IDC_BTN_PENCOLOR, &CPadPainterDlg::OnBnClickedBtnPencolor)
	ON_EN_CHANGE(IDC_EDIT_PENWIDTH, &CPadPainterDlg::OnEnChangeEditPenwidth)
	ON_BN_CLICKED(IDC_BTN_PENDEVICE, &CPadPainterDlg::OnBnClickedBtnPendevice)
	ON_BN_CLICKED(IDC_BTN_CLOSEDEVICE, &CPadPainterDlg::OnBnClickedBtnClosedevice)
	ON_MESSAGE(WM_SIGN_NOTIFY, &CPadPainterDlg::OnSignNotify)
	ON_BN_CLICKED(IDC_BTN_CLEAR, &CPadPainterDlg::OnBnClickedBtnClear)
	ON_MESSAGE(WM_SIGN_DATA, &CPadPainterDlg::OnSignData)
	ON_MESSAGE(WM_SIGN_CODEDATA, &CPadPainterDlg::OnCodeData)
	ON_MESSAGE(WM_PEN_BUTTON, &CPadPainterDlg::OnPenButton)
	ON_BN_CLICKED(IDC_BTN_READCODE_ON, &CPadPainterDlg::OnBnClickedButton1)
	ON_BN_CLICKED(IDC_BTN_READCODE_OFF, &CPadPainterDlg::OnBnClickedBtnReadcodeOff)
END_MESSAGE_MAP()


// CPadPainterDlg message handlers

BOOL CPadPainterDlg::OnInitDialog()
{
	CDialogEx::OnInitDialog();

	// Set the icon for this dialog.  The framework does this automatically
	//  when the application's main window is not a dialog
	SetIcon(m_hIcon, TRUE);			// Set big icon
	SetIcon(m_hIcon, FALSE);		// Set small icon

	// TODO: Add extra initialization here
	GdiplusStartupInput startupInput;
	GdiplusStartup(&m_gidpToken, &startupInput, NULL);

	m_WorkViewDlg.Create(IDD_DLG_WORKVIEW, this);
	CRect rectLayout;
	m_Static_Layout.GetWindowRect(rectLayout);
	ScreenToClient(rectLayout);
	m_WorkViewDlg.MoveWindow(rectLayout);

	m_Edit_PenWidth.SetWindowText("5");
	g_lpPadPainterDlg = this;
	m_HandleDataProc = 0;
	m_HandleNotifyProc = 0;

	return TRUE;  // return TRUE  unless you set the focus to a control
}

// If you add a minimize button to your dialog, you will need the code below
//  to draw the icon.  For MFC applications using the document/view model,
//  this is automatically done for you by the framework.

void CPadPainterDlg::OnPaint()
{
	if (IsIconic())
	{
		CPaintDC dc(this); // device context for painting

		SendMessage(WM_ICONERASEBKGND, reinterpret_cast<WPARAM>(dc.GetSafeHdc()), 0);

		// Center icon in client rectangle
		int cxIcon = GetSystemMetrics(SM_CXICON);
		int cyIcon = GetSystemMetrics(SM_CYICON);
		CRect rect;
		GetClientRect(&rect);
		int x = (rect.Width() - cxIcon + 1) / 2;
		int y = (rect.Height() - cyIcon + 1) / 2;

		// Draw the icon
		dc.DrawIcon(x, y, m_hIcon);
	}
	else
	{
		CDialogEx::OnPaint();
	}
}

void CPadPainterDlg::OnDestroy()
{
	CDialogEx::OnDestroy();
	
	signCloseDevice();
	signClean();

	GdiplusShutdown(m_gidpToken);
}


void CPadPainterDlg::OnBnClickedBtnPencolor()
{
	m_WorkViewDlg.SetPenColor(m_Btn_PenColor.GetColor());
}


void CPadPainterDlg::OnEnChangeEditPenwidth()
{
	CString str;
	m_Edit_PenWidth.GetWindowText(str);

	if (str.GetLength() > 0)
	{
		int size = atol((LPCSTR)str);
		m_WorkViewDlg.SetPenWidth(size);
	}
}


void CPadPainterDlg::OnBnClickedBtnPendevice()
{
	int nRet = 0;
	m_WorkViewDlg.InitPainter(false);

	nRet = signOpenDevice();
	if (nRet == ERR_OK)
	{
		//create bitmap buffer
		signGetDeviceInfo(&m_DevInfo);
		m_HandleDataProc = signRegisterDataCallBack(data_proc);
		m_HandleNotifyProc = signRegisterDevNotifyCallBack(notify_proc);
		m_HandleReadCodeProc = RegisterReadBarCodeCallBack(readcode_proc);
		m_WorkViewDlg.SetInfo(m_DevInfo.axisX.max, m_DevInfo.axisY.max, m_DevInfo.pressure);

		//use the mouse
		signChangeDeviceMode(DeviceRunMode_Pen);
	}
	if ( nRet== ERR_OK)
	{
		m_Btn_OpenDevice.EnableWindow(FALSE);
	}
	else
	{
		CString err = get_error_desc(nRet);
		AfxMessageBox(CString("error:") + err);
	}
}


void CPadPainterDlg::OnBnClickedBtnClosedevice()
{
	if (m_HandleDataProc)
		signUnregisterDataCallBack(m_HandleDataProc);

	signCloseDevice();

	m_Btn_OpenDevice.EnableWindow(TRUE);
}


const char* CPadPainterDlg::get_error_desc(int cmd)
{
	switch (cmd)
	{
	case ERR_OK:
	{
		return "Succeed.";
	}break;
	case ERR_DEVICE_NOTFOUND:
	{
		return "No found any device.";
	}break;
	case ERR_DEVICE_OPENFAIL:
	{
		return "Failure.";
	}break;
	case ERR_DEVICE_NOTCONNECTED:
	{
		return "Device disconnect.";
	}break;
	case ERR_INVALIDPARAM:
	{
		return "Invalid parameter.";
	}break;
	case ERR_NOSUPPORTED:
	{
		return "This operation is not supported.";
	}break;
	}
	return "Unkown error.";
}

int _stdcall CPadPainterDlg::data_proc(DATAPACKET data)
{
	WaitForSingleObject(g_eventData, INFINITE);
	ResetEvent(g_eventData);
	g_listData.emplace_back(data);
	SetEvent(g_eventData);
	if (data.eventtype == EventType_Pen)
	{
		g_lpPadPainterDlg->ShowPenData(data);
		g_lpWorkViewDlg->PostMessage(WM_SIGN_DATA, NULL, NULL);
		
		g_lpPadPainterDlg->PostMessage(WM_PEN_BUTTON, (WPARAM)data.button, NULL);
	}
	else
	{
		g_lpPadPainterDlg->PostMessage(WM_SIGN_DATA, NULL, NULL);
	}
	
	return 0;
}

int _stdcall CPadPainterDlg::notify_proc(STATUSPACKET status)
{
	STATUSPACKET *lpStatus = new STATUSPACKET();
	memcpy_s(lpStatus, sizeof(STATUSPACKET), &status, sizeof(STATUSPACKET));
	g_lpPadPainterDlg->PostMessage(WM_SIGN_NOTIFY, (WPARAM)lpStatus, NULL);
	return 0;
}


afx_msg LRESULT CPadPainterDlg::OnSignNotify(WPARAM wParam, LPARAM lParam)
{
	if (wParam == NULL) return 0;

	STATUSPACKET *lpStatus = (STATUSPACKET *)wParam;

	//auto connect
	if (lpStatus->status == 1)
	{
		OnBnClickedBtnPendevice();
	}
	else
	{
		OnBnClickedBtnClosedevice();
	}

	delete lpStatus;
	lpStatus = NULL;
	return 0;
}


void CPadPainterDlg::OnBnClickedBtnClear()
{
	m_WorkViewDlg.Clear();
}


afx_msg LRESULT CPadPainterDlg::OnSignData(WPARAM wParam, LPARAM lParam)
{
	WaitForSingleObject(g_eventData, INFINITE);
	ResetEvent(g_eventData);
	DATAPACKET data = g_listData.front();
	g_listData.pop_front();
	SetEvent(g_eventData);
	switch (data.eventtype)
	{
	case EventType_Key:
	{
		if (data.keystatus == KeyStatus_Down)
		{
			if (data.physical_key > 0)
			{
				CString pkey_mask = "0";
				pkey_mask.Format("%d", data.physical_key);
				m_Edit_PKey.SetWindowText(pkey_mask);
			}
			if (data.virtual_key > 0)
			{
				CString vkey;
				vkey.Format("%d", data.virtual_key);
				m_Edit_VKey.SetWindowText(vkey);
			}
		}
		else
		{
			m_Edit_PKey.SetWindowText("0");
			m_Edit_VKey.SetWindowText("0");
		}
	}break;
	}
	return 0;
}


afx_msg LRESULT CPadPainterDlg::OnPenButton(WPARAM wParam, LPARAM lParam)
{
	static unsigned last_button = 0;

	if (((unsigned)wParam) != last_button)
	{
		CString button;
		button.Format("%d", (unsigned)wParam);
		m_Edit_Button.SetWindowText(button);

		//use the button do something
		if (wParam == 1)
		{
			OnBnClickedBtnClear();
		}

		last_button = wParam;
	}

	return 0;
}

int _stdcall CPadPainterDlg::readcode_proc(ETAINFO code_data)
{
	strcode = code_data.barcode;
	g_lpPadPainterDlg->PostMessage(WM_SIGN_CODEDATA, NULL, NULL);
	return 0;
}

afx_msg LRESULT CPadPainterDlg::OnCodeData(WPARAM wParam, LPARAM lParam)
{
	CString strCode;
	strCode = strcode.c_str();
	GetDlgItem(IDC_EDT_Code)->SetWindowText(strCode);
	return 0;
}

void CPadPainterDlg::OnBnClickedButton1()
{
	// TODO: 在此添加控件通知处理程序代码
	OpenReadBarCode();
}


void CPadPainterDlg::OnBnClickedBtnReadcodeOff()
{
	CloseReadBarCode();
}

void CPadPainterDlg::ShowPenData(DATAPACKET data)
{
	//显示斜率
	CString strTiltX, strTiltY,strPress;
	strTiltX.Format(_T("%hhd"), data.tiltX);
	strTiltY.Format(_T("%hhd "), data.tiltY);
	strPress.Format(_T("%d "), data.pressure);
	GetDlgItem(IDC_EDT_TILT_X)->SetWindowText(strTiltX);
	GetDlgItem(IDC_EDT_TILT_Y)->SetWindowText(strTiltY);
	GetDlgItem(IDC_EDT_Press)->SetWindowText(strPress);
}