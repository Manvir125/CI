// SignWindowDlg.cpp :
//

#include "stdafx.h"
#include "ScreenSignature.h"
#include "SignWindowDlg.h"
#include "afxdialogex.h"

#include <Mmsystem.h>
#pragma comment(lib,"Winmm.lib")

// CSignWindowDlg 

IMPLEMENT_DYNAMIC(CSignWindowDlg, CDialogEx)

CSignWindowDlg::CSignWindowDlg(CWnd* pParent /*=NULL*/)
	: CDialogEx(IDD_DLG_SIGN, pParent)
{

}

CSignWindowDlg::~CSignWindowDlg()
{
}

void CSignWindowDlg::DoDataExchange(CDataExchange* pDX)
{
	CDialogEx::DoDataExchange(pDX);
	DDX_Control(pDX, IDC_STATIC_SIGN, m_Static_Sign);

	DDX_Control(pDX, IDC_BTN_CANCEL, m_Btn_Cancel);
	DDX_Control(pDX, IDC_BTN_CLEAR, m_Btn_Clear);
	DDX_Control(pDX, IDC_BTN_CONFIRM, m_Btn_Confirm);
}


void CSignWindowDlg::BtnHitTest(DATAPACKET data)
{
	if (data.penstatus == PenStatus_Down)
	{
		float fScaleX = (m_ScreenRect.right - m_ScreenRect.left)*1.0f / m_MaxX;
		float fScaleY = (m_ScreenRect.bottom - m_ScreenRect.top)*1.0f / m_MaxY;

		CPoint pt = CPoint((LONG)(fScaleX*data.x), (LONG)(fScaleY*data.y));

		pt.x += m_ScreenRect.left;
		pt.y += m_ScreenRect.top;

		CRect r1, r2, r3;
		m_Btn_Cancel.GetWindowRect(r1);
		m_Btn_Clear.GetWindowRect(r2);
		m_Btn_Confirm.GetWindowRect(r3);

		//virtual mouse click
		if (r1.PtInRect(pt))
		{
			OnBnClickedBtnCancel();
		}
		if (r2.PtInRect(pt))
		{
			OnBnClickedBtnClear();
		}
		if (r3.PtInRect(pt))
		{
			OnBnClickedBtnConfirm();
		}

	}
}

void CSignWindowDlg::SetInfo(ULONG x, ULONG y, ULONG p, int pen_width, COLORREF pen_color, RECT screen_rect)
{
	m_MaxX = x;
	m_MaxY = y;
	m_MaxP = p;
	m_nPenWidth = pen_width;
	m_dwPenColor = pen_color;
	m_ScreenRect = screen_rect;
}

void CSignWindowDlg::ResetLayout()
{
	CRect rect_client, rect_view;
	GetClientRect(&rect_client);

	m_Static_Sign.GetWindowRect(&rect_view);
	ScreenToClient(rect_view);
	
	m_Static_Sign.MoveWindow((rect_client.Width() - rect_view.Width()) / 2, rect_view.top + 100, rect_view.Width(), rect_client.Height()*2/3);

	m_Static_Sign.GetWindowRect(&rect_view);
	ScreenToClient(rect_view);

	CRect r1, r2, r3;
	m_Btn_Cancel.GetClientRect(&r1);
	m_Btn_Clear.GetClientRect(&r2);
	m_Btn_Confirm.GetClientRect(&r3);

	m_Btn_Cancel.MoveWindow(rect_view.left, rect_view.bottom + 60, r1.Width(), r1.Height());
	m_Btn_Clear.MoveWindow(rect_view.left + (rect_view.Width() - r2.Width()) / 2, rect_view.bottom + 60, r1.Width(), r1.Height());
	m_Btn_Confirm.MoveWindow(rect_view.right - r2.Width(), rect_view.bottom + 60, r1.Width(), r1.Height());


	if (m_lpBitmapBuffer == nullptr)
	{
		m_lpBitmapBuffer = new Gdiplus::Bitmap(rect_view.Width(), rect_view.Height(), PixelFormat32bppARGB);
		Gdiplus::Graphics g(m_lpBitmapBuffer);
		g.Clear(Gdiplus::Color(0x00, 0xff, 0xff, 0xff));

		CClientDC dc(this);
		m_MemDC.CreateCompatibleDC(&dc);
		m_MemBitmap.CreateCompatibleBitmap(&dc, rect_view.Width(), rect_view.Height());
		m_MemDC.SelectObject(&m_MemBitmap);

		m_MemDC.FillSolidRect(0,0,rect_view.Width(),rect_view.Height(), RGB(0xff, 0xff, 0xff));
	}
}

void CSignWindowDlg::PenEvent(DATAPACKET packet)
{
	DrawSign(packet);

	
	BtnHitTest(packet);
	
}

void CSignWindowDlg::DrawSign(DATAPACKET data)
{
	float fScaleX = (m_ScreenRect.right-m_ScreenRect.left)*1.0f / m_MaxX;
	float fScaleY = (m_ScreenRect.bottom - m_ScreenRect.top)*1.0f / m_MaxY;

	CPoint pt= CPoint((LONG)(fScaleX*data.x), (LONG)(fScaleY*data.y));

	pt.x += m_ScreenRect.left;
	pt.y += m_ScreenRect.top;

	m_Static_Sign.ScreenToClient(&pt);
	Gdiplus::PointF CurPt(pt.x*1.0f, pt.y*1.0f);

	CRect rect_view;
	m_Static_Sign.GetClientRect(&rect_view);

	if (rect_view.PtInRect(pt))
	{
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
				if (m_LastPt.X != 0 && m_LastPt.Y != 0)
				{
					Gdiplus::Graphics g1(m_lpBitmapBuffer);
					g1.SetSmoothingMode(SmoothingModeHighQuality);
					const float max_pen_width = m_nPenWidth*1.0f;
					float cur_pen_wdith = (max_pen_width / m_MaxP)*data.pressure;
					Gdiplus::Pen pen(Gdiplus::Color(0xff, GetRValue(m_dwPenColor), GetGValue(m_dwPenColor), GetBValue(m_dwPenColor)), cur_pen_wdith);
					pen.SetStartCap(Gdiplus::LineCapRound);
					pen.SetEndCap(Gdiplus::LineCapRound);
					g1.DrawLine(&pen, m_LastPt, CurPt);

					CRect r;
					m_Static_Sign.GetClientRect(r);

					static DWORD t1 = timeGetTime();

					DWORD t2 = timeGetTime();
					if (t2 - t1 > 30)
					{
						m_MemDC.FillSolidRect(r, RGB(0xff, 0xff, 0xff));
						Gdiplus::Graphics g2(m_MemDC);
						g2.DrawImage(m_lpBitmapBuffer, 0, 0);
						t1 = t2;
					}
					
					CDC *pViewDC = m_Static_Sign.GetDC();
					pViewDC->BitBlt(0, 0, r.Width(), r.Height(), &m_MemDC, 0, 0, SRCCOPY);
					m_Static_Sign.ReleaseDC(pViewDC);

				}
			}break;
			case PenStatus_Up:
			{
				CRect r;
				m_Static_Sign.GetClientRect(r);
				m_MemDC.FillSolidRect(r, RGB(0xff, 0xff, 0xff));
				Gdiplus::Graphics g2(m_MemDC);
				g2.DrawImage(m_lpBitmapBuffer, 0, 0);

				CDC *pViewDC = m_Static_Sign.GetDC();
				pViewDC->BitBlt(0, 0, r.Width(), r.Height(), &m_MemDC, 0, 0, SRCCOPY);
				m_Static_Sign.ReleaseDC(pViewDC);

			}break;
			}
			
		}break;
		case EventType_Key:
		{

		}break;
		}
	}
	else
	{
		
	}
	m_LastPt = CurPt;
}

void CSignWindowDlg::SetResultWindow(CWnd* lpWnd)
{
	m_lpResultWnd = lpWnd;
}

BEGIN_MESSAGE_MAP(CSignWindowDlg, CDialogEx)
	
	ON_BN_CLICKED(IDC_BTN_CANCEL, &CSignWindowDlg::OnBnClickedBtnCancel)
	ON_BN_CLICKED(IDC_BTN_CLEAR, &CSignWindowDlg::OnBnClickedBtnClear)
	ON_BN_CLICKED(IDC_BTN_CONFIRM, &CSignWindowDlg::OnBnClickedBtnConfirm)
	ON_WM_PAINT()
	ON_WM_DESTROY()
END_MESSAGE_MAP()


// 


void CSignWindowDlg::OnBnClickedBtnCancel()
{
	PostMessage(WM_CLOSE, 0, 0);
}


void CSignWindowDlg::OnBnClickedBtnClear()
{
	if (m_lpBitmapBuffer)
	{
		Gdiplus::Graphics g(m_lpBitmapBuffer);
		g.Clear(Gdiplus::Color(0x00, 0xff, 0xff, 0xff));

		CRect r;
		m_Static_Sign.GetClientRect(r);
		m_MemDC.FillSolidRect(r, RGB(0xff, 0xff, 0xff));

		CDC *pViewDC = m_Static_Sign.GetDC();
		pViewDC->BitBlt(0, 0, r.Width(), r.Height(), &m_MemDC, 0, 0, SRCCOPY);
		m_Static_Sign.ReleaseDC(pViewDC);
	}
}


void CSignWindowDlg::OnBnClickedBtnConfirm()
{
	if (m_lpResultWnd)
	{
		if (m_lpBitmapBuffer)
		{
			Gdiplus::Graphics g(m_lpResultWnd->GetSafeHwnd());

			CRect r;
			m_lpResultWnd->GetClientRect(r);

			g.DrawImage(m_lpBitmapBuffer, 0, 0, r.Width(),r.Height());
		}
	}
	PostMessage(WM_CLOSE, 0, 0);
}


BOOL CSignWindowDlg::OnInitDialog()
{
	CDialogEx::OnInitDialog();

	m_lpBitmapBuffer = nullptr;
	m_lpResultWnd = nullptr;
	m_ButtonFont.CreatePointFont(180, "Arial");
	m_Btn_Cancel.SetFont(&m_ButtonFont);
	m_Btn_Clear.SetFont(&m_ButtonFont);
	m_Btn_Confirm.SetFont(&m_ButtonFont);

	return TRUE;  
}


void CSignWindowDlg::OnPaint()
{
	CPaintDC dc(this); // device context for painting
	if (m_lpBitmapBuffer)
	{
		CRect r;
		m_Static_Sign.GetClientRect(r);
		CDC *pViewDC = m_Static_Sign.GetDC();
		pViewDC->BitBlt(0, 0, r.Width(), r.Height(), &m_MemDC, 0, 0, SRCCOPY);
		m_Static_Sign.ReleaseDC(pViewDC);
	}
}


void CSignWindowDlg::OnDestroy()
{
	CDialogEx::OnDestroy();

	if (m_lpBitmapBuffer)
	{
		delete m_lpBitmapBuffer;
		m_lpBitmapBuffer = nullptr;
	}
}
