
// PadSignatureDlg.cpp : implementation file
//

#include "stdafx.h"
#include "PadSignature.h"
#include "PadSignatureDlg.h"
#include "afxdialogex.h"
#include <list>
using namespace std;

#ifdef _DEBUG
//#define new DEBUG_NEW
#endif

// CPadSignatureDlg dialog

CPadSignatureDlg * g_lpPadSignatureDlg = nullptr;
list<DATAPACKET >	g_listData;
HANDLE				g_eventData = ::CreateEvent(NULL, TRUE, TRUE, NULL);

int GetEncoderClsid(const WCHAR* format, CLSID* pClsid);

CPadSignatureDlg::CPadSignatureDlg(CWnd* pParent /*=NULL*/)
	: CDialogEx(IDD_PADSIGNATURE_DIALOG, pParent)
{
	m_hIcon = AfxGetApp()->LoadIcon(IDR_MAINFRAME);
}

void CPadSignatureDlg::DoDataExchange(CDataExchange* pDX)
{
	CDialogEx::DoDataExchange(pDX);
	DDX_Control(pDX, IDC_STATIC_SIGN, m_Static_Sign);
	DDX_Control(pDX, IDC_MFCCOLORBUTTON1, m_Btn_PenColor);
	DDX_Control(pDX, IDC_EDIT_MAX_WIDTH, m_Edit_PenWidth);
	DDX_Control(pDX, IDC_BTN_OPEN, m_Btn_OpenDevice);
}

BEGIN_MESSAGE_MAP(CPadSignatureDlg, CDialogEx)
	ON_WM_PAINT()
	ON_WM_QUERYDRAGICON()
	ON_BN_CLICKED(IDC_BTN_SAVE, &CPadSignatureDlg::OnBnClickedBtnSave)
	ON_BN_CLICKED(IDC_BTN_CLEAR, &CPadSignatureDlg::OnBnClickedBtnClear)
	ON_BN_CLICKED(IDC_BTN_OPEN, &CPadSignatureDlg::OnBnClickedBtnOpen)
	ON_WM_DESTROY()
	ON_MESSAGE(WM_SIGN_DATA, &CPadSignatureDlg::OnSignData)
	ON_MESSAGE(WM_SIGN_NOTIFY, &CPadSignatureDlg::OnSignNotify)
	ON_BN_CLICKED(IDC_MFCCOLORBUTTON1, &CPadSignatureDlg::OnBnClickedMfccolorbutton1)
	ON_EN_CHANGE(IDC_EDIT_MAX_WIDTH, &CPadSignatureDlg::OnEnChangeEditMaxWidth)
	ON_BN_CLICKED(IDC_CHECK1, &CPadSignatureDlg::OnBnClickedCheck1)
	ON_BN_CLICKED(IDC_CHECK2, &CPadSignatureDlg::OnBnClickedCheck2)
	ON_BN_CLICKED(IDC_CHECK3, &CPadSignatureDlg::OnBnClickedCheck3)
	ON_BN_CLICKED(IDC_CHECK4, &CPadSignatureDlg::OnBnClickedCheck4)
END_MESSAGE_MAP()


// CPadSignatureDlg message handlers
CPadSignatureDlg::~CPadSignatureDlg()
{

	if (m_MemDC.GetSafeHdc())
	{
		m_MemBitmap.DeleteObject();
		m_MemDC.DeleteDC();
	}

}
BOOL CPadSignatureDlg::OnInitDialog()
{
	CDialogEx::OnInitDialog();

	// Set the icon for this dialog.  The framework does this automatically
	//  when the application's main window is not a dialog
	SetIcon(m_hIcon, TRUE);			// Set big icon
	SetIcon(m_hIcon, FALSE);		// Set small icon

	// TODO: Add extra initialization here

	GdiplusStartupInput startupInput;
	GdiplusStartup(&m_gidpToken, &startupInput, NULL);

	m_HandleDataProc = 0;
	m_HandleNotifyProc = 0;
	g_lpPadSignatureDlg = this;
//	m_lpBitmapBuffer = nullptr;
	m_Edit_PenWidth.SetWindowText("2");

	m_PenColor = m_Btn_PenColor.GetColor();
	m_MaxWidth = 2;

	signInitialize();
	CWnd *pWnd = GetDlgItem(IDC_STATIC_SIGN);//参数为控件ID
	pWnd->GetClientRect(&m_PicRec);//rc为控件的大小。
	m_Rote = false;
	CRect rect;
	m_Static_Sign.GetClientRect(rect);
	if (!m_MemDC.GetSafeHdc()/*m_lpBitmapBuffer == nullptr*/)
	{
		//			m_lpBitmapBuffer = new Gdiplus::Bitmap(rect.Width(), rect.Height(), PixelFormat32bppARGB);
		//			Gdiplus::Graphics g(m_lpBitmapBuffer);
		CClientDC dc(this);
		m_MemDC.CreateCompatibleDC(&dc);
		m_MemBitmap.CreateCompatibleBitmap(&dc, rect.Width(), rect.Height());
		m_MemDC.SelectObject(&m_MemBitmap);
		Gdiplus::Graphics g(m_MemDC.GetSafeHdc());
		g.Clear(Gdiplus::Color(0x00, 0xff, 0xff, 0xff));
		m_MemDC.FillSolidRect(rect, RGB(0xff, 0xff, 0xff));
	}
	((CButton *)GetDlgItem(IDC_CHECK1))->SetCheck(true);
	return TRUE;  // return TRUE  unless you set the focus to a control
}

// If you add a minimize button to your dialog, you will need the code below
//  to draw the icon.  For MFC applications using the document/view model,
//  this is automatically done for you by the framework.

void CPadSignatureDlg::OnPaint()
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
		if (m_MemDC.GetSafeHdc() && m_MemBitmap.GetSafeHandle()/*m_lpBitmapBuffer*/)
		{
			CRect r;
			m_Static_Sign.GetClientRect(r);
			CDC *pViewDC = m_Static_Sign.GetDC();
			pViewDC->BitBlt(0, 0, r.Width(), r.Height(), &m_MemDC, 0, 0, SRCCOPY);
			m_Static_Sign.ReleaseDC(pViewDC);
		}
	}
}

// The system calls this function to obtain the cursor to display while the user drags
//  the minimized window.
HCURSOR CPadSignatureDlg::OnQueryDragIcon()
{
	return static_cast<HCURSOR>(m_hIcon);
}

void CPadSignatureDlg::OnBnClickedBtnOpen()
{
	CString str;
	m_Btn_OpenDevice.GetWindowTextA(str);
	if (str == "OpenDevice")
	{
		int nRet = signOpenDevice();
		if (nRet == ERR_OK)
		{
			//create bitmap buffer
			CRect rect;
			m_Static_Sign.GetClientRect(rect);
			if (m_MemDC.GetSafeHdc())
			{
				m_MemDC.DeleteDC();
				m_MemBitmap.DeleteObject();
			}
			CClientDC dc(this);
			m_MemDC.CreateCompatibleDC(&dc);

			m_MemBitmap.CreateCompatibleBitmap(&dc, rect.Width(), rect.Height());
			m_MemDC.SelectObject(&m_MemBitmap);
			Gdiplus::Graphics g(m_MemDC.GetSafeHdc());
			g.Clear(Gdiplus::Color(0x00, 0xff, 0xff, 0xff));
			m_MemDC.FillSolidRect(rect, RGB(0xff, 0xff, 0xff));

			AutoSize();
			m_HandleDataProc = signRegisterDataCallBack(data_proc);
			m_HandleNotifyProc = signRegisterDevNotifyCallBack(notify_proc);
			m_Btn_OpenDevice.SetWindowText("CloseDevice");

			CString title = "Pad Signature NO";
			SetWindowText(title);

			return;
		}
		CString str = get_error_desc(nRet);
		AfxMessageBox(CString("error:") + str);
	}
	else
	{
		/*if (m_HandleNotifyProc)
			signUnregisterDevNotifyCallBack(m_HandleNotifyProc);*/
		if (m_HandleDataProc)
			signUnregisterDataCallBack(m_HandleDataProc);

		signCloseDevice();

		m_Btn_OpenDevice.SetWindowText("OpenDevice");

		CString title = "Pad Signature OFF";
		SetWindowText(title);
	}
}

const char* CPadSignatureDlg::get_error_desc(int cmd)
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

int _stdcall CPadSignatureDlg::data_proc(DATAPACKET data)
{
	WaitForSingleObject(g_eventData, INFINITE);
	ResetEvent(g_eventData);
	g_listData.emplace_back(data);
	SetEvent(g_eventData);
	g_lpPadSignatureDlg->PostMessage(WM_SIGN_DATA,NULL, NULL);
	return 0;
}

int _stdcall CPadSignatureDlg::notify_proc(STATUSPACKET status)
{
	STATUSPACKET *lpStatus = new STATUSPACKET();
	memcpy_s(lpStatus, sizeof(STATUSPACKET), &status, sizeof(STATUSPACKET));
	g_lpPadSignatureDlg->PostMessage(WM_SIGN_NOTIFY, (WPARAM)lpStatus, NULL);
	return 0;
}

void CPadSignatureDlg::AutoSize()
{
	signGetDeviceInfo(&m_DevInfo);
	CWnd *pWnd = GetDlgItem(IDC_STATIC_SIGN);//参数为控件ID
	float assX = ((float)m_PicRec.Width() / m_DevInfo.axisX.max);
	float assY = ((float)m_PicRec.Height() / m_DevInfo.axisY.max);
	if (assX > assY)
	{
		pWnd->MoveWindow(m_PicRec.left + (m_PicRec.Width() - m_DevInfo.axisX.max*assY) / 2, m_PicRec.top, m_DevInfo.axisX.max*assY, m_PicRec.Height());
	}
	else
	{
		pWnd->MoveWindow(m_PicRec.left, m_PicRec.top + (m_PicRec.Height() - m_DevInfo.axisY.max*assX) / 2, m_PicRec.Width(), m_DevInfo.axisY.max*assX);
	}
	CRect rect;
	m_Static_Sign.GetClientRect(rect);
	if (m_MemDC.GetSafeHdc())
	{
		m_MemBitmap.DeleteObject();
		m_MemDC.DeleteDC();
	}
	CClientDC dc(this);
	m_MemDC.CreateCompatibleDC(&dc);
	m_MemBitmap.CreateCompatibleBitmap(&dc, rect.Width(), rect.Height());
	m_MemDC.SelectObject(&m_MemBitmap);
	Gdiplus::Graphics g(m_MemDC.GetSafeHdc());
	g.Clear(Gdiplus::Color(0x00, 0xff, 0xff, 0xff));
	m_MemDC.FillSolidRect(rect, RGB(0xff, 0xff, 0xff));
}

void CPadSignatureDlg::OnBnClickedBtnSave()
{
	if (m_MemBitmap.m_hObject)
	{
		CString def_filename;
		CFileDialog dlg(FALSE,"png", def_filename, OFN_HIDEREADONLY | OFN_OVERWRITEPROMPT,"PNG File (*.png)|*.png");
		dlg.DoModal();
		CString path = dlg.GetPathName();
		if (path == def_filename) 
			return;

		CImage imgTemp;
		// CImage是MFC中的类。
		imgTemp.Attach(m_MemBitmap.operator HBITMAP());
		imgTemp.Save(path);
	}

// 	if (m_lpBitmapBuffer)
// 	{
// 		CString def_filename;
// 		CFileDialog dlg(FALSE,"png", def_filename, OFN_HIDEREADONLY | OFN_OVERWRITEPROMPT,"PNG File (*.png)|*.png");
// 		dlg.DoModal();
// 		CString path = dlg.GetPathName();
// 		if (path == def_filename) return;
// 
// 		CString strRet;
// 		WCHAR * pwstrFormat = L"image/png";
// 		
// 		int quality = 100;
// 		CLSID Clsid;
// 		GetEncoderClsid(pwstrFormat, &Clsid);
// 		EncoderParameters encoderParameters;
// 		encoderParameters.Count = 1;
// 		encoderParameters.Parameter[0].Guid = EncoderQuality;
// 		encoderParameters.Parameter[0].Type = EncoderParameterValueTypeLong;
// 		encoderParameters.Parameter[0].NumberOfValues = 1;
// 		encoderParameters.Parameter[0].Value = &quality;
// 
// 		USES_CONVERSION;
// 		WCHAR* lpFileName = A2W((LPCSTR)path);
// 		m_lpBitmapBuffer->Save(lpFileName, &Clsid, &encoderParameters);
// 	}
}


void CPadSignatureDlg::OnBnClickedBtnClear()
{
	if(m_MemDC.GetSafeHdc())
	{
		CRect r;
		m_Static_Sign.GetClientRect(r);
		m_MemDC.FillSolidRect(r, RGB(0xff, 0xff, 0xff));
		
		CDC *pViewDC = m_Static_Sign.GetDC();
		pViewDC->BitBlt(0, 0, r.Width(), r.Height(), &m_MemDC, 0, 0, SRCCOPY);
		m_Static_Sign.ReleaseDC(pViewDC);
	}
// 	if (m_lpBitmapBuffer)
// 	{
// 		Gdiplus::Graphics g(m_lpBitmapBuffer);
// 		g.Clear(Gdiplus::Color(0x00, 0xff, 0xff, 0xff));
// 
// 		CRect r;
// 		m_Static_Sign.GetClientRect(r);
// 		m_MemDC.FillSolidRect(r, RGB(0xff, 0xff, 0xff));
// 		
// 		CDC *pViewDC = m_Static_Sign.GetDC();
// 		pViewDC->BitBlt(0, 0, r.Width(), r.Height(), &m_MemDC, 0, 0, SRCCOPY);
// 		m_Static_Sign.ReleaseDC(pViewDC);
// 	}
}


void CPadSignatureDlg::OnDestroy()
{
	CDialogEx::OnDestroy();

	signCloseDevice();
	signClean();

	GdiplusShutdown(m_gidpToken);
}


afx_msg LRESULT CPadSignatureDlg::OnSignData(WPARAM wParam, LPARAM lParam)
{
	WaitForSingleObject(g_eventData, INFINITE);
	ResetEvent(g_eventData);
	DATAPACKET data = g_listData.front();
	g_listData.pop_front();
	SetEvent(g_eventData);

	CRect r;
	m_Static_Sign.GetClientRect(r);
// 	float fScaleX = m_lpBitmapBuffer->GetWidth()*1.0f / m_DevInfo.axisX.max;
// 	float fScaleY = m_lpBitmapBuffer->GetHeight()*1.0f / m_DevInfo.axisY.max;

	int		x;						//x	value				2 bytes
	int		y;						//y	value				2 bytes
	float fScaleX;
	float fScaleY;

	//if (m_Rote)
	//{
	//	fScaleX = r.Width()*1.0f / m_DevInfo.axisY.max;
	//	fScaleY = r.Height()*1.0f / m_DevInfo.axisX.max;

	//	x = (m_DevInfo.axisY.max-data.y);
	//	y = data.x;
	//}
	//else
	{
		 fScaleX = r.Width()*1.0f / m_DevInfo.axisX.max;
		 fScaleY = r.Height()*1.0f / m_DevInfo.axisY.max;
		 x =data.x;
		 y=data.y;	 
	}

	Gdiplus::PointF CurPt = Gdiplus::PointF(fScaleX*x, fScaleY*y);
	

	switch (data.eventtype)
	{
	case EventType_Pen:
	{
		switch (data.penstatus)
		{
		case PenStatus_Down:
		{

		}break;
		case PenStatus_Move:
		{
			if (!(m_LastPt.X == 0 && m_LastPt.Y == 0))
			{
//				Gdiplus::Graphics g1(m_lpBitmapBuffer);
				Gdiplus::Graphics g1(m_MemDC.GetSafeHdc());
				g1.SetSmoothingMode(SmoothingModeHighQuality);
				const float max_pen_width = m_MaxWidth*1.0f;
				float cur_pen_wdith = (max_pen_width / m_DevInfo.pressure)*data.pressure;
				Gdiplus::Pen pen(Gdiplus::Color(0xff, GetRValue(m_PenColor), GetGValue(m_PenColor), GetBValue(m_PenColor)), cur_pen_wdith);
				pen.SetStartCap(Gdiplus::LineCapRound);
				pen.SetEndCap(Gdiplus::LineCapRound);
				g1.DrawLine(&pen, m_LastPt, CurPt);

 				CRect r;
 				m_Static_Sign.GetClientRect(r);

				CDC *pViewDC = m_Static_Sign.GetDC();
				pViewDC->BitBlt(0, 0, r.Width(), r.Height(), &m_MemDC, 0, 0, SRCCOPY);
				m_Static_Sign.ReleaseDC(pViewDC);

			}
		}break;
		case PenStatus_Up:
		{

		}break;
		}
		m_LastPt = CurPt;
	}break;
	case EventType_Key:
	{
		
	}break;
	}

	return 0;
}


afx_msg LRESULT CPadSignatureDlg::OnSignNotify(WPARAM wParam, LPARAM lParam)
{
	if (wParam == NULL) return 0;

	STATUSPACKET *lpStatus = (STATUSPACKET *)wParam;
	
	//auto connect

	OnBnClickedBtnOpen();

	delete lpStatus;
	lpStatus = NULL;
	return 0;
}


void CPadSignatureDlg::OnBnClickedMfccolorbutton1()
{
	m_PenColor = m_Btn_PenColor.GetColor();
}


void CPadSignatureDlg::OnEnChangeEditMaxWidth()
{
	CString width;
	((CEdit*)GetDlgItem(IDC_EDIT_MAX_WIDTH))->GetWindowText(width);

	UINT w = atoi(width);
	if (w < 1 )
	{
		w = 1;
		m_MaxWidth = w;
	}
	else if (w > 10)
	{
		w = 10;
		m_MaxWidth = w;
	}
	else
	{
		m_MaxWidth = w;
	}
}


int GetEncoderClsid(const WCHAR* format, CLSID* pClsid) //GDI+ get image encoder
{
	UINT  num = 0;          // number of image encoders
	UINT  size = 0;         // size of the image encoder array in bytes

	ImageCodecInfo* pImageCodecInfo = NULL;

	GetImageEncodersSize(&num, &size);
	if (size == 0)
		return -1;  // Failure

	pImageCodecInfo = (ImageCodecInfo*)(malloc(size));
	if (pImageCodecInfo == NULL)
		return -1;  // Failure

	GetImageEncoders(num, size, pImageCodecInfo);

	for (UINT j = 0; j < num; ++j)
	{
		if (wcscmp(pImageCodecInfo[j].MimeType, format) == 0)
		{
			*pClsid = pImageCodecInfo[j].Clsid;
			free(pImageCodecInfo);
			return j;  // Success
		}
	}

	free(pImageCodecInfo);
	return -1;  // Failure

}

void CPadSignatureDlg::OnBnClickedCheck1()
{
	// TODO: 在此添加控件通知处理程序代码
	m_Rote = ((CButton *)GetDlgItem(IDC_CHECK1))->GetCheck();

	if (m_Rote)
	{
		signRotateMode(0);
		AutoSize();

		((CButton *)GetDlgItem(IDC_CHECK4))->SetCheck(0);
		((CButton *)GetDlgItem(IDC_CHECK2))->SetCheck(0);
		((CButton *)GetDlgItem(IDC_CHECK3))->SetCheck(0);
		OnBnClickedBtnClear();
	}
	
}


void CPadSignatureDlg::OnBnClickedCheck2()
{
	// TODO: 在此添加控件通知处理程序代码
	m_Rote = ((CButton *)GetDlgItem(IDC_CHECK2))->GetCheck();
	CWnd *pWnd = GetDlgItem(IDC_STATIC_SIGN);//参数为控件ID

	if (m_Rote)
	{
		signRotateMode(1);
		//pWnd->MoveWindow(m_PicRec.left + m_PicRec.Width() / 4, m_PicRec.top, m_PicRec.Width() / 2, m_PicRec.Height());
		AutoSize();
		((CButton *)GetDlgItem(IDC_CHECK4))->SetCheck(0);
		((CButton *)GetDlgItem(IDC_CHECK1))->SetCheck(0);
		((CButton *)GetDlgItem(IDC_CHECK3))->SetCheck(0);
		OnBnClickedBtnClear();
	}
}


void CPadSignatureDlg::OnBnClickedCheck3()
{
	// TODO: 在此添加控件通知处理程序代码
	m_Rote = ((CButton *)GetDlgItem(IDC_CHECK3))->GetCheck();
	CWnd *pWnd = GetDlgItem(IDC_STATIC_SIGN);//参数为控件ID

	if (m_Rote)
	{
		signRotateMode(2);
		//pWnd->MoveWindow(m_PicRec.left, m_PicRec.top, m_PicRec.Width(), m_PicRec.Height());

		AutoSize();
		((CButton *)GetDlgItem(IDC_CHECK4))->SetCheck(0);
		((CButton *)GetDlgItem(IDC_CHECK1))->SetCheck(0);
		((CButton *)GetDlgItem(IDC_CHECK2))->SetCheck(0);
		OnBnClickedBtnClear();
	}
}


void CPadSignatureDlg::OnBnClickedCheck4()
{
	// TODO: 在此添加控件通知处理程序代码
	m_Rote = ((CButton *)GetDlgItem(IDC_CHECK4))->GetCheck();
	CWnd *pWnd = GetDlgItem(IDC_STATIC_SIGN);//参数为控件ID

	if (m_Rote)
	{
		signRotateMode(3);
		//pWnd->MoveWindow(m_PicRec.left + m_PicRec.Width() / 4, m_PicRec.top, m_PicRec.Width() / 2, m_PicRec.Height());

		AutoSize();
		((CButton *)GetDlgItem(IDC_CHECK2))->SetCheck(0);
		((CButton *)GetDlgItem(IDC_CHECK1))->SetCheck(0);
		((CButton *)GetDlgItem(IDC_CHECK3))->SetCheck(0);
		OnBnClickedBtnClear();
	}
}
