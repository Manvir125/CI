
// ScreenSignatureDlg.cpp : implementation file
//

#include "stdafx.h"
#include "ScreenSignature.h"
#include "ScreenSignatureDlg.h"
#include "afxdialogex.h"
#include <list>
using namespace std;

#ifdef _DEBUG
#define new DEBUG_NEW
#endif


// CScreenSignatureDlg dialog
CScreenSignatureDlg *g_lpScreenSignatureDlg = nullptr;
list<DATAPACKET >	g_listData;
HANDLE				g_eventData = ::CreateEvent(NULL, TRUE, TRUE, NULL);
bool				m_bPenWorking = false;

CScreenSignatureDlg::CScreenSignatureDlg(CWnd* pParent /*=NULL*/)
	: CDialogEx(IDD_SCREENSIGNATURE_DIALOG, pParent)
{
	m_hIcon = AfxGetApp()->LoadIcon(IDR_MAINFRAME);
}

void CScreenSignatureDlg::DoDataExchange(CDataExchange* pDX)
{
	CDialogEx::DoDataExchange(pDX);
	DDX_Control(pDX, IDC_STATIC_RESULT_PIC, m_Static_ResultPic);
	DDX_Control(pDX, IDC_MFCCOLORBUTTON_PENCOLOR, m_Btn_PenColor);
	DDX_Control(pDX, IDC_EDIT_PENWIDTH, m_Edit_PenWidth);
}

BEGIN_MESSAGE_MAP(CScreenSignatureDlg, CDialogEx)
	ON_WM_PAINT()
	ON_WM_QUERYDRAGICON()
	ON_BN_CLICKED(IDC_BTN_BEGINSIGN, &CScreenSignatureDlg::OnBnClickedBtnBeginsign)
	ON_BN_CLICKED(IDC_BTN_ENDSIGN, &CScreenSignatureDlg::OnBnClickedBtnEndsign)
	ON_WM_DESTROY()
	ON_MESSAGE(WM_SIGN_DATA, &CScreenSignatureDlg::OnSigndata)
	ON_MESSAGE(WM_SIGN_NOTIFY, &CScreenSignatureDlg::OnSignnotify)
	ON_EN_CHANGE(IDC_EDIT_PENWIDTH, &CScreenSignatureDlg::OnEnChangeEditPenwidth)
	ON_BN_CLICKED(IDC_CHECK1, &CScreenSignatureDlg::OnBnClickedCheck1)
	ON_BN_CLICKED(IDC_CHECK2, &CScreenSignatureDlg::OnBnClickedCheck2)
END_MESSAGE_MAP()


// CScreenSignatureDlg message handlers

BOOL CScreenSignatureDlg::OnInitDialog()
{
	CDialogEx::OnInitDialog();

	// Set the icon for this dialog.  The framework does this automatically
	//  when the application's main window is not a dialog
	SetIcon(m_hIcon, TRUE);			// Set big icon
	SetIcon(m_hIcon, FALSE);		// Set small icon

	//ShowWindow(SW_MINIMIZE);

	// TODO: Add extra initialization here

	GdiplusStartupInput startupInput;
	GdiplusStartup(&m_gidpToken, &startupInput, NULL);
	signInitialize();
	signRegisterDevNotifyCallBack(notify_proc);
	if (signGetDeviceStatus()==0)
	{
		GetDlgItem(IDC_DeviceStatus)->SetWindowText(_T("Device ON"));
	}
	else
	{
		GetDlgItem(IDC_DeviceStatus)->SetWindowText(_T("Device Off"));
	}

	m_HandleDataProc = 0;
	m_HandleNotifyProc = 0;
	g_lpScreenSignatureDlg = this;
	m_Edit_PenWidth.SetWindowText("5");
	
	((CButton *)GetDlgItem(IDC_CHECK1))->SetCheck(1);
	m_Rote = 0;

	return TRUE;  // return TRUE  unless you set the focus to a control
}

// If you add a minimize button to your dialog, you will need the code below
//  to draw the icon.  For MFC applications using the document/view model,
//  this is automatically done for you by the framework.

void CScreenSignatureDlg::OnPaint()
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

// The system calls this function to obtain the cursor to display while the user drags
//  the minimized window.
HCURSOR CScreenSignatureDlg::OnQueryDragIcon()
{
	return static_cast<HCURSOR>(m_hIcon);
}



void CScreenSignatureDlg::OnBnClickedBtnBeginsign()
{
	int nRet = signGetDeviceStatus();
	if (nRet == ERR_OK)
	{
		RECT r = { 0 };
		nRet = signGetScreenRect(&r);
		nRet = signOpenDevice();
		if (nRet == ERR_OK)
		{
			signRotateMode(m_Rote);

			TABLET_DEVICEINFO ti = { 0 };
			signGetDeviceInfo(&ti);
			if (m_lpSignWindowDlg != NULL)
			{
				m_lpSignWindowDlg->DestroyWindow();
				delete m_lpSignWindowDlg;

			}

			CDC* lpDC = m_Static_ResultPic.GetDC();
			CRect rect_pic;
			m_Static_ResultPic.GetClientRect(rect_pic);
			lpDC->FillSolidRect(&rect_pic, RGB(0xff, 0xff, 0xff));
			m_Static_ResultPic.ReleaseDC(lpDC);

			m_lpSignWindowDlg = new CSignWindowDlg();
			m_lpSignWindowDlg->Create(IDD_DLG_SIGN, this);
			m_lpSignWindowDlg->MoveWindow(r.left, r.top, r.right - r.left, r.bottom - r.top);
			m_lpSignWindowDlg->ShowWindow(SW_SHOW);
			m_lpSignWindowDlg->SetInfo(ti.axisX.max, ti.axisY.max, ti.pressure, m_MaxWidth, m_Btn_PenColor.GetColor(), r);
			m_lpSignWindowDlg->ResetLayout();
			m_lpSignWindowDlg->SetResultWindow(&m_Static_ResultPic);

			m_HandleDataProc = signRegisterDataCallBack(data_proc);
			m_HandleTouchProc = signRegisterTouchCallBack(touch_proc);
			return;
		}
		CString str = get_error_desc(nRet);
		AfxMessageBox(CString("error:") + str);
	}
	else
	{
		if (m_HandleDataProc)
			signUnregisterDataCallBack(m_HandleDataProc);

		signCloseDevice();
	}
	
}


void CScreenSignatureDlg::OnBnClickedBtnEndsign()
{
	if (m_HandleDataProc > 0)
		signUnregisterDataCallBack(m_HandleDataProc);
	signCloseDevice();
	if (m_lpSignWindowDlg)
	{
		m_lpSignWindowDlg->DestroyWindow();
		delete m_lpSignWindowDlg;
		m_lpSignWindowDlg = nullptr;
	}
}


const char* CScreenSignatureDlg::get_error_desc(int cmd)
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



int _stdcall CScreenSignatureDlg::data_proc(DATAPACKET data)
{
	if (data.penstatus == PenStatus_Leave)
		m_bPenWorking = false;
	if (data.penstatus != PenStatus_Up && data.penstatus != PenStatus_Leave)
		m_bPenWorking = true;

	WaitForSingleObject(g_eventData, INFINITE);
	ResetEvent(g_eventData);
	g_listData.emplace_back(data);
	SetEvent(g_eventData);
	g_lpScreenSignatureDlg->PostMessage(WM_SIGN_DATA, NULL, NULL);

	return 0;
}

int _stdcall CScreenSignatureDlg::touch_proc(TOUCHDATA data)
{
	if (m_bPenWorking)//非笔工作时
		return 0;

	//手触数据转成笔数据
	DATAPACKET dpt;
	switch (data.status[0])
	{
	case TouchStatus_Down:
	{
		dpt.penstatus = PenStatus_Down;
	}
	break;
	case  TouchStatus_Move:
	{
		dpt.penstatus = PenStatus_Move;
	}
	break;
	case TouchStatus_Up:
	{
		dpt.penstatus = PenStatus_Up;
	}
	break;
	}

	dpt.eventtype = EventType_Pen;


	CRect r;
	(g_lpScreenSignatureDlg->m_lpSignWindowDlg)->GetWindowRect(r);
	float fScaleX, fScaleY;
	fScaleX = r.Width()*1.0f / g_lpScreenSignatureDlg->m_lpSignWindowDlg->m_MaxX;
	fScaleY = r.Height()*1.0f / g_lpScreenSignatureDlg->m_lpSignWindowDlg->m_MaxY;

	dpt.x = (USHORT)(data.x[0])/ fScaleX;
	dpt.y = (USHORT)(data.y[0])/ fScaleY;
	dpt.pressure = (dpt.penstatus == PenStatus_Up ? 0 : max(1600, (int)(g_lpScreenSignatureDlg->m_lpSignWindowDlg->m_MaxP*0.8f)));
	WaitForSingleObject(g_eventData, INFINITE);
	ResetEvent(g_eventData);
	g_listData.emplace_back(dpt);
	SetEvent(g_eventData);
	g_lpScreenSignatureDlg->PostMessage(WM_SIGN_DATA, NULL, NULL);

	return 0;
}

int _stdcall CScreenSignatureDlg::notify_proc(STATUSPACKET status)
{
	STATUSPACKET *lpStatus = new STATUSPACKET();
	memcpy_s(lpStatus, sizeof(STATUSPACKET), &status, sizeof(STATUSPACKET));
	if (g_lpScreenSignatureDlg)
	{
		g_lpScreenSignatureDlg->PostMessage(WM_SIGN_NOTIFY, (WPARAM)lpStatus, NULL);
	}
	if (status.status==1)
	{
		g_lpScreenSignatureDlg->GetDlgItem(IDC_DeviceStatus)->SetWindowText(_T("Device ON"));

	}
	else
	{
		g_lpScreenSignatureDlg->GetDlgItem(IDC_DeviceStatus)->SetWindowText(_T("Device Off"));
	}

	
	return 0;
}

void CScreenSignatureDlg::OnDestroy()
{
	CDialogEx::OnDestroy();

	signCloseDevice();
	signClean();

	GdiplusShutdown(m_gidpToken);

}


afx_msg LRESULT CScreenSignatureDlg::OnSigndata(WPARAM wParam, LPARAM lParam)
{
	WaitForSingleObject(g_eventData, INFINITE);
	ResetEvent(g_eventData);
	DATAPACKET data = g_listData.front();
	g_listData.pop_front();
	SetEvent(g_eventData);

	m_lpSignWindowDlg->PenEvent(data);

	return 0;
}

afx_msg LRESULT CScreenSignatureDlg::OnSignnotify(WPARAM wParam, LPARAM lParam)
{
	STATUSPACKET *lpStatus = (STATUSPACKET *)wParam;
	if (lpStatus->status == 1)
	{
		
	}
	else
	{

	}
	delete lpStatus;

	return 0;
}


void CScreenSignatureDlg::OnEnChangeEditPenwidth()
{
	CString width;
	m_Edit_PenWidth.GetWindowText(width);

	UINT w = atoi(width);
	if (w < 1)
	{
		w = 1;
		m_Edit_PenWidth.SetWindowText("5");
	}
	else if (w > 10)
	{
		w = 10;
		m_Edit_PenWidth.SetWindowText("10");
	}
	else
	{
		m_MaxWidth = w;
	}
}


void CScreenSignatureDlg::OnBnClickedCheck1()
{
	// TODO: 在此添加控件通知处理程序代码
	bool ret = ((CButton *)GetDlgItem(IDC_CHECK1))->GetCheck();
	if (ret)
	{
		m_Rote = 0;
		((CButton *)GetDlgItem(IDC_CHECK2))->SetCheck(0);
	}
}


void CScreenSignatureDlg::OnBnClickedCheck2()
{
	// TODO: 在此添加控件通知处理程序代码
	bool ret = ((CButton *)GetDlgItem(IDC_CHECK2))->GetCheck();
	if (ret)
	{
		m_Rote = 1;
		((CButton *)GetDlgItem(IDC_CHECK1))->SetCheck(0);
	}
}
