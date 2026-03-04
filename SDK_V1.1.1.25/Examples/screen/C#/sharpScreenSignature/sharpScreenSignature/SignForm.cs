using System;
using System.Collections.Generic;
using System.ComponentModel;
using System.Data;
using System.Drawing;
using System.Linq;
using System.Text;
using System.Windows.Forms;
using System.Drawing.Drawing2D;
using System.Drawing.Imaging;
using SignAPI;

namespace sharpScreenSignature
{
    public partial class SignForm : Form
    {
        private int PenWidth = 5;
        private Color PenColor = Color.Black;
        private Bitmap BufferImage = null;
        private Point LastPt = new Point(0, 0);
        private int MaxX = 0;
        private int MaxY = 0;
        private int MaxPressure = 0;
        private RECT ScreenRect;
        private PictureBox ResultPic = null;
        public SignForm()
        {
            InitializeComponent();
            ScreenRect.left = ScreenRect.top = ScreenRect.right = ScreenRect.bottom = 0;
        }

        private void SignForm_FormClosed(object sender, FormClosedEventArgs e)
        {
           
        }
        public void ResetLayout()
        {
            PanelWorkView.Left = (this.Width - PanelWorkView.Width) / 2;
            
            SignBtnCancel.Left = PanelWorkView.Left;
            SignBtnClear.Left = PanelWorkView.Left + (PanelWorkView.Width - SignBtnClear.Width) / 2;
            SignBtnConfirm.Left = PanelWorkView.Right - SignBtnConfirm.Width;

            //initialize buffer bitmap
            BufferImage = new Bitmap(PanelWorkView.Width, PanelWorkView.Height);
            Graphics g = Graphics.FromImage(BufferImage);
            g.Clear(Color.FromArgb(0x00, 0xff, 0xff, 0xff));
        }
        public void SetInfo(int x,int y,int p,int pen_width, Color pen_color,RECT screen_rect)
        {
            MaxX = x; MaxY = y; MaxPressure = p;PenWidth = pen_width;PenColor = pen_color;ScreenRect = screen_rect;
        }
        public void SetResultPictureBox(PictureBox pic)
        {
            ResultPic = pic;
        }
        public void PenEvent(DATAPACKET packet)
        {
            //draw sign
            DrawSign(packet.x, packet.y, packet.pressure, packet.penstatus);
            
            if(packet.penstatus==PenStatus.PenStatus_Down)
            {
                InvokeVirtualClick(packet.x, packet.y, packet.penstatus);
            }
        }
        private void DrawSign(int x, int y, int pressure, SignAPI.PenStatus status)
        {
            if (PanelWorkView.InvokeRequired)
            {
                Action<int, int, int, SignAPI.PenStatus> action = new Action<int, int, int, PenStatus>(DrawSign);
                PanelWorkView.Invoke(action, x, y, pressure, status);
            }
            else
            {
                Point CurPt = new Point(0, 0);
                float fScaleX = ScreenRect.Width * 1.0f / MaxX;
                float fScaleY = ScreenRect.Height * 1.0f / MaxY;
                float fScaleP = PenWidth * 1.0f / MaxPressure;
                CurPt = new Point((int)(x * fScaleX), (int)(y * fScaleY));
                CurPt.X += ScreenRect.left;
                CurPt.Y += ScreenRect.top;

                //changed to panel client point
                CurPt = PanelWorkView.PointToClient(CurPt);
               
                if (PanelWorkView.ClientRectangle.Contains(CurPt))
                {
                    switch (status)
                    {
                        case PenStatus.PenStatus_Hover:
                            {

                            }
                            break;
                        case PenStatus.PenStatus_Down:
                            {

                            }
                            break;
                        case PenStatus.PenStatus_Move:
                            {
                                if (LastPt.X != 0 && LastPt.Y != 0)
                                {
                                    using (Graphics g = PanelWorkView.CreateGraphics())
                                    {
                                        float CurPenWidth = pressure * fScaleP;
                                        Pen pen = new Pen(PenColor, CurPenWidth);
                                        pen.StartCap = LineCap.Round;
                                        pen.EndCap = LineCap.Round;
                                        g.SmoothingMode = SmoothingMode.HighQuality;
                                        g.DrawLine(pen, LastPt, CurPt);

                                        //drawing sign to the buffer bitmap
                                        using (Graphics g2 = Graphics.FromImage(BufferImage))
                                        {
                                            g2.SmoothingMode = SmoothingMode.HighQuality;
                                            g2.DrawLine(pen, LastPt, CurPt);
                                        }
                                    }
                                }
                            }
                            break;
                        case PenStatus.PenStatus_Up:
                            {
                                LastPt = new Point(0, 0);
                            }
                            break;
                        case PenStatus.PenStatus_Leave:
                            {
                                LastPt = new Point(0, 0);
                            }
                            break;
                    }
                }
                LastPt = CurPt;
            }
        }

        private void InvokeVirtualClick(int x, int y, SignAPI.PenStatus status)
        {
            // Does not interfere with physical mouse movements
            if (this.InvokeRequired)
            {
                Action<int, int, SignAPI.PenStatus> action = new Action<int, int, PenStatus>(InvokeVirtualClick);
                PanelWorkView.Invoke(action, x, y, status);
            }
            else
            {
                Point CurPt = new Point(0, 0);
                float fScaleX = ScreenRect.Width * 1.0f / MaxX;
                float fScaleY = ScreenRect.Height * 1.0f / MaxY;
                float fScaleP = PenWidth * 1.0f / MaxPressure;
                CurPt = new Point((int)(x * fScaleX), (int)(y * fScaleY));
                CurPt.X += ScreenRect.left;
                CurPt.Y += ScreenRect.top;
                if(status==PenStatus.PenStatus_Down)
                {
                    Point pt = SignBtnCancel.PointToClient(CurPt);
                    if (SignBtnCancel.ClientRectangle.Contains(pt))
                    {
                        //close the signature window
                        SignBtnCancel_Click(null, null);
                        return;
                    }

                    pt = SignBtnClear.PointToClient(CurPt);
                    if (SignBtnClear.ClientRectangle.Contains(pt))
                    {
                        SignBtnClear_Click(null, null);
                        return;
                    }

                    pt = SignBtnConfirm.PointToClient(CurPt);
                    if (SignBtnConfirm.ClientRectangle.Contains(pt))
                    {
                        SignBtnConfirm_Click(null, null);
                        return;
                    }
                }
               
            }
        }

        private void SignBtnCancel_Click(object sender, EventArgs e)
        {
            Close();
        }

        private void SignBtnClear_Click(object sender, EventArgs e)
        {
            if (BufferImage != null)
            {
                using (Graphics g = Graphics.FromImage(BufferImage))
                {
                    g.Clear(Color.FromArgb(0x00, 0xff, 0xff, 0xff));
                }
            }
            PanelWorkView.Refresh();
        }

        private void SignBtnConfirm_Click(object sender, EventArgs e)
        {
           if(ResultPic!=null)
            {
                ResultPic.Image = BufferImage;
                Close();
            }
        }
    }
}
