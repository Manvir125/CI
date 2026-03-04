
// PadSignatureDlg.h : header file
//

#pragma once


#include "afxwin.h"
#include "afxcolorbutton.h"
#include <GdiPlus.h>
using namespace Gdiplus;
#pragma  comment(lib,"Gdiplus.lib")

#include "../../../../../Libs/libSign.h"
#pragma comment(lib,"../../../../../Libs/libSign.lib")
using namespace UG_LIBSIGN;
#define			WM_SIGN_DATA			WM_USER+101
#define			WM_SIGN_NOTIFY			WM_USER+102

// CPadSignatureDlg dialog
class CPadSignatureDlg : public CDialogEx
{
// Construction
public:
	CPadSignatureDlg(CWnd* pParent = NULL);	// standard constructor
	~CPadSignatureDlg();
#ifdef AFX_DESIGN_TIME
	enum { IDD = IDD_PADSIGNATURE_DIALOG };
#endif

protected:
	// Generated message map functions
	virtual void DoDataExchange(CDataExchange* pDX);	// DDX/DDV support
	virtual BOOL OnInitDialog();
	afx_msg void OnPaint();
	afx_msg HCURSOR OnQueryDragIcon();
	afx_msg LRESULT OnSignData(WPARAM wParam, LPARAM lParam);
	afx_msg LRESULT OnSignNotify(WPARAM wParam, LPARAM lParam);
	afx_msg void OnBnClickedBtnOpen();
	afx_msg void OnDestroy();
	afx_msg void OnBnClickedBtnSave();
	afx_msg void OnBnClickedBtnClear();
	afx_msg void OnBnClickedMfccolorbutton1();
	afx_msg void OnEnChangeEditMaxWidth();
	afx_msg void OnBnClickedCheck1();
	afx_msg void OnBnClickedCheck2();
	afx_msg void OnBnClickedCheck3();
	afx_msg void OnBnClickedCheck4();
	DECLARE_MESSAGE_MAP()
private:
	const char* get_error_desc(int cmd);
	static	int _stdcall data_proc(DATAPACKET data);
	static	int _stdcall notify_proc(STATUSPACKET status);
	void			     AutoSize();
private:
	HICON				m_hIcon;
	CStatic             m_Static_Sign;
	CMFCColorButton		m_Btn_PenColor;
	CEdit				m_Edit_PenWidth;
	ULONG_PTR			m_gidpToken;
	Gdiplus::PointF		m_LastPt;
	CDC					m_MemDC;
	CBitmap				m_MemBitmap;
	UINT				m_MaxWidth;
	COLORREF			m_PenColor;
	TABLET_DEVICEINFO	m_DevInfo;
	CButton				m_Btn_OpenDevice;
	CRect				m_PicRec;
	bool				m_Rote;
	long				m_HandleDataProc;
	long				m_HandleNotifyProc;

};
