// WorkViewDlg.cpp : 实现文件
//

#include "stdafx.h"
#include "PadPainter.h"
#include "WorkViewDlg.h"
#include "afxdialogex.h"
#include "PadPainterDlg.h"
#include <MMSystem.h>
#pragma comment(lib,"Winmm.lib")
#include <list>
using namespace std;
// CWorkViewDlg 对话框

extern CPadPainterDlg*		g_lpPadPainterDlg;
CWorkViewDlg*				g_lpWorkViewDlg = nullptr;
extern list<DATAPACKET >	g_listData;
extern HANDLE				g_eventData;


IMPLEMENT_DYNAMIC(CWorkViewDlg, CDialogEx)

CWorkViewDlg::CWorkViewDlg(CWnd* pParent /*=NULL*/)
	: CDialogEx(IDD_DLG_WORKVIEW, pParent)
{

}

CWorkViewDlg::~CWorkViewDlg()
{
}

void CWorkViewDlg::DoDataExchange(CDataExchange* pDX)
{
	CDialogEx::DoDataExchange(pDX);
}


BEGIN_MESSAGE_MAP(CWorkViewDlg, CDialogEx)
	ON_WM_PAINT()
	ON_MESSAGE(WM_SIGN_DATA, &CWorkViewDlg::OnSignData)
END_MESSAGE_MAP()

void CWorkViewDlg::OnPaint()
{
	CPaintDC dc(this); // device context for painting

}


BOOL CWorkViewDlg::OnInitDialog()
{
	CDialogEx::OnInitDialog();

	g_lpWorkViewDlg = this;

	
	m_lpBitmapBuffer = nullptr;

	m_PenColor = RGB(0,0,0);
	m_MaxWidth = 5;

	signInitialize();

	return TRUE;  
}


void CWorkViewDlg::SetPenWidth(int size /*= 5*/)
{
	if (size <= 0)
		m_MaxWidth = 1;
	else if (size >= 10)
		m_MaxWidth = 10;
	else
		m_MaxWidth = size;
}

void CWorkViewDlg::SetPenColor(COLORREF color)
{
	m_PenColor = color;
}

void CWorkViewDlg::SetInfo(unsigned max_x, unsigned max_y,unsigned max_p)
{
	m_MaxX = max_x;
	m_MaxY = max_y;
	m_MaxP = max_p;
}

void CWorkViewDlg::InitPainter(bool recreate/*=false*/)
{
	CRect rect;
	g_lpPadPainterDlg->m_Static_Layout.GetClientRect(rect);
	if (m_lpBitmapBuffer == nullptr)
	{
		m_lpBitmapBuffer = new Gdiplus::Bitmap(rect.Width(), rect.Height(), PixelFormat32bppARGB);
		Gdiplus::Graphics g(m_lpBitmapBuffer);
		g.Clear(Gdiplus::Color(0x00, 0xff, 0xff, 0xff));

		CClientDC dc(this);
		m_MemDC.CreateCompatibleDC(&dc);
		m_MemBitmap.CreateCompatibleBitmap(&dc, rect.Width(), rect.Height());
		m_MemDC.SelectObject(&m_MemBitmap);

		m_MemDC.FillSolidRect(rect, RGB(0xff, 0xff, 0xff));
		dc.BitBlt(0, 0, rect.Width(), rect.Height(), &m_MemDC, 0, 0, SRCCOPY);
	}
}

void CWorkViewDlg::Clear()
{
	if (m_lpBitmapBuffer)
	{
		Gdiplus::Graphics g(m_lpBitmapBuffer);
		g.Clear(Gdiplus::Color(0x00, 0xff, 0xff, 0xff));

		CRect r;
		GetClientRect(r);
		m_MemDC.FillSolidRect(r, RGB(0xff, 0xff, 0xff));

		CDC *pViewDC = GetDC();
		pViewDC->BitBlt(0, 0, r.Width(), r.Height(), &m_MemDC, 0, 0, SRCCOPY);
		ReleaseDC(pViewDC);
	}
}

afx_msg LRESULT CWorkViewDlg::OnSignData(WPARAM wParam, LPARAM lParam)
{
	WaitForSingleObject(g_eventData, INFINITE);
	ResetEvent(g_eventData);
	DATAPACKET data = g_listData.front();
	g_listData.pop_front();
	SetEvent(g_eventData);
	/*
	int w = ::GetSystemMetrics(SM_CXSCREEN);
	int h = ::GetSystemMetrics(SM_CYSCREEN);

	float fScaleX = w*1.0f / m_MaxX;
	float fScaleY = h*1.0f / m_MaxY;*/
	float fScaleX = m_lpBitmapBuffer->GetWidth()*1.0f / m_MaxX;
	float fScaleY = m_lpBitmapBuffer->GetHeight()*1.0f / m_MaxY;


	CPoint CurPt((LONG)(fScaleX*data.x), (LONG)(fScaleY*data.y));

	//ScreenToClient(&CurPt);
	CRect r;
	GetClientRect(r);

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
			if (m_LastPt.x != 0 && m_LastPt.x != 0 )//&& r.PtInRect(CurPt))
			{
				Gdiplus::Graphics g1(m_lpBitmapBuffer);
				g1.SetSmoothingMode(SmoothingModeHighQuality);
				const float max_pen_width = m_MaxWidth*1.0f;
				float cur_pen_wdith = (max_pen_width / m_MaxP)*data.pressure;
				Gdiplus::Pen pen(Gdiplus::Color(0xff, GetRValue(m_PenColor), GetGValue(m_PenColor), GetBValue(m_PenColor)), cur_pen_wdith);
				pen.SetStartCap(Gdiplus::LineCapRound);
				pen.SetEndCap(Gdiplus::LineCapRound);
				
				g1.DrawLine(&pen, Point(m_LastPt.x,m_LastPt.y), Point(CurPt.x,CurPt.y));

				static DWORD dwLastTime = 0;
				DWORD dwCurTime = timeGetTime();
				if (dwCurTime - dwLastTime >= 33)
				{
					m_MemDC.FillSolidRect(r, RGB(0xff, 0xff, 0xff));
					Gdiplus::Graphics g2(m_MemDC);
					g2.DrawImage(m_lpBitmapBuffer, 0, 0);
					dwLastTime = dwCurTime;
				}
				CDC *pViewDC = GetDC();
				pViewDC->BitBlt(0, 0, r.Width(), r.Height(), &m_MemDC, 0, 0, SRCCOPY);
				ReleaseDC(pViewDC);
			}

		}break;
		case PenStatus_Up:
		{
			CRect r;
			GetClientRect(r);
			m_MemDC.FillSolidRect(r, RGB(0xff, 0xff, 0xff));
			Gdiplus::Graphics g2(m_MemDC);
			g2.DrawImage(m_lpBitmapBuffer, 0, 0);
			CDC *pViewDC = GetDC();
			pViewDC->BitBlt(0, 0, r.Width(), r.Height(), &m_MemDC, 0, 0, SRCCOPY);
			ReleaseDC(pViewDC);
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



