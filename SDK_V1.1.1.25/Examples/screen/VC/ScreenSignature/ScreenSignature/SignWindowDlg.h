#pragma once
#include "afxwin.h"
#include "../../../../../Libs/libSign.h"
#include <GdiPlus.h>
using namespace Gdiplus;
// CSignWindowDlg 
using namespace UG_LIBSIGN;
class CSignWindowDlg : public CDialogEx
{
	DECLARE_DYNAMIC(CSignWindowDlg)

public:
	CSignWindowDlg(CWnd* pParent = NULL);   //
	virtual ~CSignWindowDlg();

// 对话框数据
#ifdef AFX_DESIGN_TIME
	enum { IDD = IDD_DLG_SIGN };
#endif

protected:
	virtual void DoDataExchange(CDataExchange* pDX);    // DDX/DDV 

	DECLARE_MESSAGE_MAP()
public:
	ULONG		m_MaxX;
	ULONG		m_MaxY;
	ULONG		m_MaxP;

	CStatic		m_Static_Sign;
	int			m_nPenWidth;
	COLORREF	m_dwPenColor;
	RECT		m_ScreenRect;

	Gdiplus::Bitmap		*m_lpBitmapBuffer;	//gdi+ bitmap
	Gdiplus::PointF		m_LastPt;
	CDC					m_MemDC;
	CBitmap				m_MemBitmap;
	CFont				m_ButtonFont;
	CWnd*				m_lpResultWnd;
private:
	void	BtnHitTest(DATAPACKET packet);
public:
	void	SetInfo(ULONG x, ULONG y, ULONG p,int pen_width,COLORREF pen_color,RECT screen_rect);
	void	ResetLayout();
	void	PenEvent(DATAPACKET packet);
	void	DrawSign(DATAPACKET packet);
	void	SetResultWindow(CWnd* lpWnd);
private:
	CButton m_Btn_Cancel;
	CButton m_Btn_Clear;
	CButton m_Btn_Confirm;
public:
	afx_msg void OnBnClickedBtnCancel();
	afx_msg void OnBnClickedBtnClear();
	afx_msg void OnBnClickedBtnConfirm();
	virtual BOOL OnInitDialog();
	afx_msg void OnPaint();
	afx_msg void OnDestroy();
};
