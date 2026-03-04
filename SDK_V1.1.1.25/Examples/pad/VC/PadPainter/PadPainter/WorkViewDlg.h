#pragma once



// CWorkViewDlg 对话框

class CWorkViewDlg : public CDialogEx
{
	DECLARE_DYNAMIC(CWorkViewDlg)

private:
	Gdiplus::Bitmap		*m_lpBitmapBuffer;	//gdi+ bitmap
	CPoint				m_LastPt;
	CDC					m_MemDC;
	CBitmap				m_MemBitmap;
	UINT				m_MaxWidth;
	COLORREF			m_PenColor;
	unsigned			m_MaxX;
	unsigned			m_MaxY;
	unsigned			m_MaxP;
public:
	CWorkViewDlg(CWnd* pParent = NULL);   // 标准构造函数
	virtual ~CWorkViewDlg();

// 对话框数据
#ifdef AFX_DESIGN_TIME
	enum { IDD = IDD_DLG_WORKVIEW };
#endif

protected:
	virtual void DoDataExchange(CDataExchange* pDX);    // DDX/DDV 支持

	DECLARE_MESSAGE_MAP()
public:
	afx_msg void OnPaint();
	virtual BOOL OnInitDialog();

	void	SetPenWidth(int size  = 5);
	void	SetPenColor(COLORREF color);
	void	SetInfo(unsigned max_x, unsigned max_y,unsigned max_p);
	void	InitPainter(bool recreate=false);
	void	Clear();
protected:
	afx_msg LRESULT OnSignData(WPARAM wParam, LPARAM lParam);
	
};
