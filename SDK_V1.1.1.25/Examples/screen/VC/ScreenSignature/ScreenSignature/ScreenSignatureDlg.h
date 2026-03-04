
// ScreenSignatureDlg.h : header file
//

#pragma once
#include "afxwin.h"
#include "afxcolorbutton.h"
#include "SignWindowDlg.h"
#include <GdiPlus.h>
using namespace Gdiplus;
#pragma  comment(lib,"Gdiplus.lib")

#include "../../../../../Libs/libSign.h"
#pragma comment(lib,"../../../../../Libs/libSign.lib")
using namespace UG_LIBSIGN;
#define			WM_SIGN_DATA			WM_USER+101
#define			WM_SIGN_NOTIFY			WM_USER+102


// CScreenSignatureDlg dialog
class CScreenSignatureDlg : public CDialogEx
{
// Construction
public:
	CScreenSignatureDlg(CWnd* pParent = NULL);	// standard constructor

// Dialog Data
#ifdef AFX_DESIGN_TIME
	enum { IDD = IDD_SCREENSIGNATURE_DIALOG };
#endif

	protected:
	virtual void DoDataExchange(CDataExchange* pDX);	// DDX/DDV support


// Implementation
protected:
	HICON m_hIcon;

	// Generated message map functions
	virtual BOOL OnInitDialog();
	afx_msg void OnPaint();
	afx_msg HCURSOR OnQueryDragIcon();
	DECLARE_MESSAGE_MAP()
public:
	afx_msg void OnBnClickedBtnBeginsign();
	afx_msg void OnBnClickedBtnEndsign();
private:
	CStatic m_Static_ResultPic;
	CMFCColorButton m_Btn_PenColor;
	CEdit m_Edit_PenWidth;

	ULONG_PTR			m_gidpToken;

	int					m_MaxWidth;
	COLORREF			m_PenColor;
	TABLET_DEVICEINFO	m_DevInfo;
	long	m_HandleDataProc;
	long	m_HandleNotifyProc;
	long	m_HandleTouchProc;

	int		m_Rote{0};

	CSignWindowDlg	*m_lpSignWindowDlg;

	const char* get_error_desc(int cmd);
	static	int _stdcall data_proc(DATAPACKET data);
	static	int _stdcall notify_proc(STATUSPACKET status);
	static	int _stdcall touch_proc(TOUCHDATA data);
public:
	afx_msg void OnDestroy();
protected:
	afx_msg LRESULT OnSigndata(WPARAM wParam, LPARAM lParam);
	afx_msg LRESULT OnSignnotify(WPARAM wParam, LPARAM lParam);
public:
	afx_msg void OnEnChangeEditPenwidth();
	afx_msg void OnBnClickedCheck1();
	afx_msg void OnBnClickedCheck2();
};
