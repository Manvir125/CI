
// PadPainterDlg.h : header file
//

#pragma once

#include "WorkViewDlg.h"
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
#define			WM_PEN_BUTTON			WM_USER+103
#define			WM_SIGN_CODEDATA		WM_USER+104

// CPadPainterDlg dialog
class CPadPainterDlg : public CDialogEx
{
// Construction
public:
	CPadPainterDlg(CWnd* pParent = NULL);	// standard constructor

// Dialog Data
#ifdef AFX_DESIGN_TIME
	enum { IDD = IDD_PADPAINTER_DIALOG };
#endif

	protected:
	virtual void DoDataExchange(CDataExchange* pDX);	// DDX/DDV support

private:
	CWorkViewDlg		m_WorkViewDlg;
// Implementation
protected:
	HICON m_hIcon;

	// Generated message map functions
	virtual BOOL OnInitDialog();
	afx_msg void OnPaint();
//	afx_msg HCURSOR OnQueryDragIcon();
	DECLARE_MESSAGE_MAP()
private:

	ULONG_PTR			m_gidpToken;

	long	m_HandleDataProc;
	long	m_HandleNotifyProc;
	long	m_HandleReadCodeProc;
	TABLET_DEVICEINFO	m_DevInfo;
public:
	const char* get_error_desc(int cmd);
	static	int _stdcall data_proc(DATAPACKET data);
	static	int _stdcall notify_proc(STATUSPACKET status);
	static  int _stdcall readcode_proc(ETAINFO code_data);

	afx_msg void OnDestroy();
	afx_msg void OnBnClickedBtnPencolor();
	CStatic m_Static_Layout;
private:
	CMFCColorButton m_Btn_PenColor;
	CEdit m_Edit_PenWidth;
public:
	afx_msg void OnEnChangeEditPenwidth();
private:
	CButton m_Btn_OpenDevice;
	CButton m_Btn_CloseDevice;
public:
	afx_msg void OnBnClickedBtnPendevice();
	afx_msg void OnBnClickedBtnClosedevice();
protected:
	afx_msg LRESULT OnSignNotify(WPARAM wParam, LPARAM lParam);
public:
	afx_msg void OnBnClickedBtnClear();
protected:
	afx_msg LRESULT OnSignData(WPARAM wParam, LPARAM lParam);
private:
	CEdit m_Edit_PKey;
	CEdit m_Edit_VKey;
	CEdit m_Edit_Button;
protected:
	afx_msg LRESULT OnPenButton(WPARAM wParam, LPARAM lParam);
public:
	afx_msg void OnBnClickedButton1();
protected:
	afx_msg LRESULT OnCodeData(WPARAM wParam, LPARAM lParam);
public:
	afx_msg void OnBnClickedBtnReadcodeOff();
	void ShowPenData(DATAPACKET data);
};
