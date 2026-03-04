using System;
using System.Collections.Generic;
using System.ComponentModel;
using System.Data;
using System.Drawing;
using System.Linq;
using System.Text;
using System.Threading.Tasks;
using System.Windows.Forms;
using System.Drawing.Imaging;
using System.Drawing.Drawing2D;
using System.Threading;
using SignAPI;

namespace sharpPadPainter
{
    public partial class PadPainter : Form
    {
        private int PenWidth = 5;
        private Color PenColor = Color.Black;
        private Bitmap BufferImage = null;
        private PointF LastPt = new PointF(0, 0);
        private TABLET_DEVICEINFO DevInfo;
        private static DATAPACKETPRCO Delegate_DataPacket = null;
        private static DEVNOTIFYPROC Delegate_Notify = null;
        
        private int HandleDataProc = 0;
        private int HandleNotifyProc = 0;
        private ushort LastPenButton = 0;

        Queue<DATAPACKET> qData = new Queue<DATAPACKET>();
        public PadPainter()
        {
            InitializeComponent();
        }

        private void BtnOpen_Click(object sender, EventArgs e)
        {
            int nRet = Win32SignAPI.signOpenDevice();
            if (nRet == ErrorCode.ERR_OK)
            {
                Win32SignAPI.signGetDeviceInfo(ref DevInfo);
                Delegate_DataPacket = new DATAPACKETPRCO(DataPacketHandler);
                Win32SignAPI.signRegisterDataCallBack(Delegate_DataPacket);

                //use the pad mouse
               /* nRet =Win32SignAPI.signChangeDeviceMode((int)DeviceRunMode.DeviceRunMode_Pen);
                if(nRet==ErrorCode.ERR_NOSUPPORTED)
                {
                    // use software virtual mouse
                    Win32SignAPI.signChangeDeviceMode((int)DeviceRunMode.DeviceRunMode_Pen);
                    Win32SignAPI.signMouseControl(true);
                }*/

                //initialize buffer image
                BufferImage = new Bitmap(PanelWorkView.Width, PanelWorkView.Height);
                Graphics g = Graphics.FromImage(BufferImage);
                g.Clear(Color.FromArgb(0x00, 0xff, 0xff, 0xff));

                PanelWorkView.Refresh();

                BtnOpen.Enabled = false;

                this.Text = "PadSignature NO";
            }
            else
            {
                String s = "error:" + GetErrorDesc(nRet);
                MessageBox.Show(s);
            }
        }

        private void BtnClose_Click(object sender, EventArgs e)
        {
            if (HandleDataProc > 0)
                Win32SignAPI.signUnregisterDataCallBack(HandleDataProc);


            //If use the pad mouse to click the close button, create a new thread to call the signCloseDevice();
            Thread t = new Thread(() => { Win32SignAPI.signCloseDevice(); });
            t.Start();
          

            BtnOpen.Enabled = true;

            this.Text = "PadSignature OFF";
        }

        private void BtnClear_Click(object sender, EventArgs e)
        {
            if (BufferImage != null)
            {
                using (Graphics g = Graphics.FromImage(BufferImage))
                {
                    g.Clear(Color.FromArgb(0x00, 0xff, 0xff, 0xff));
                }
            }
            PanelWorkView.Refresh();
            Win32SignAPI.signClean();
        }

        private void PadPainter_FormClosed(object sender, FormClosedEventArgs e)
        {
            if (HandleNotifyProc > 0)
                Win32SignAPI.signUnregisterDevNotifyCallBack(HandleNotifyProc);

            BtnClose_Click(null, null);
        }

        public int DataPacketHandler(DATAPACKET packet)
        {
            qData.Enqueue(packet);
            //Console.WriteLine("{0},{1},{2},{3},", packet.x, packet.y, packet.pressure, packet.penstatus);
            if (packet.eventtype == EventType.EventType_Pen)
            {
                DrawSign(packet.x, packet.y, packet.pressure, packet.penstatus);

                PenButtonEvent(packet.button);
                //textPress.Text = packet.pressure.ToString();
                ShowPacketInfo(packet);
            }

            if (packet.eventtype == EventType.EventType_Key)
            {
                PadKeyEvent(packet.physical_key, packet.virtual_key, packet.keystatus);
            }


            return 0;
        }

        public void DrawSign(int x, int y, int pressure, SignAPI.PenStatus status)
        {
            if (PanelWorkView.InvokeRequired)
            {
                Action<int, int, int, SignAPI.PenStatus> action = new Action<int, int, int, PenStatus>(DrawSign);
                PanelWorkView.Invoke(action, x, y, pressure, status);
            }
            else
            {
                PointF CurPt = new PointF(0, 0);
                //float fScaleX = Screen.PrimaryScreen.Bounds.Width * 1.0f / DevInfo.axisX.max;
                //float fScaleY = Screen.PrimaryScreen.Bounds.Height * 1.0f / DevInfo.axisY.max;
                float fScaleX = PanelWorkView.Width * 1.0f / DevInfo.axisX.max;
                float fScaleY = PanelWorkView.Height * 1.0f / DevInfo.axisY.max;
                float fScaleP = PenWidth * 1.0f / DevInfo.pressure;
                CurPt = new PointF((x * fScaleX), (y * fScaleY));

                //changed to client point
               // CurPt = PanelWorkView.PointToClient(CurPt);
                if (PanelWorkView.ClientRectangle.Contains((int)CurPt.X, (int)CurPt.Y))
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

                            }
                            break;
                        case PenStatus.PenStatus_Leave:
                            {
                                LastPt = new PointF(0, 0);
                            }
                            break;
                    }
                }

                LastPt = CurPt;
            }
        }

        private String GetErrorDesc(int code)
        {
            switch (code)
            {
                case ErrorCode.ERR_OK:
                    {
                        return "Succeed.";
                    }
                // break;
                case ErrorCode.ERR_DEVICE_NOTFOUND:
                    {
                        return "No found any device.";
                    }
                // break;
                case ErrorCode.ERR_DEVICE_OPENFAIL:
                    {
                        return "Failure.";
                    }
                // break;
                case ErrorCode.ERR_DEVICE_NOTCONNECTED:
                    {
                        return "Device disconnect.";
                    }
                //break;
                case ErrorCode.ERR_INVALIDPARAM:
                    {
                        return "Invalid parameter.";
                    }
                // break;
                case ErrorCode.ERR_NOSUPPORTED:
                    {
                        return "This operation is not supported.";
                    }
                    // break;
            }
            return "Unkown error.";
        }

        public void PadKeyEvent(UInt32 pkey, UInt32 vkey, KeyStatus s)
        {
            if (this.InvokeRequired)
            {
                Action<UInt32, UInt32, KeyStatus> action = new Action<uint, uint, KeyStatus>(PadKeyEvent);
                this.Invoke(action, pkey, vkey, s);
            }
            else
            {
                if (s == KeyStatus.KeyStatus_Down)
                {
                    if (pkey > 0)
                    {
                        TextPKey.Text = pkey.ToString();
                    }
                    if (vkey > 0)
                    {
                        TextVKey.Text = vkey.ToString();
                        if(vkey == 1)//签名界面点击重签
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
                        else if (vkey == 2)//签名界面点击完成 
                        {
                            //
                        }
                        else if (vkey == 3)//签名界面点击取消
                        {
                            //
                        }
                    }

                }
                else
                {
                    TextPKey.Text = "0";
                    TextVKey.Text = "0";
                }
            }
        }

        public void ShowPacketInfo(DATAPACKET packet)
        {
            
                Action<DATAPACKET> action = new Action<DATAPACKET>(ShowPacketInfo);
                if (textPress.InvokeRequired)
                {
                    textPress.Invoke(action, packet);
                }
                else
                {
                    textPress.Text = packet.pressure.ToString();
                    texttitlX.Text = packet.tiltX.ToString();
                    texttitlY.Text = packet.tiltX.ToString();
            }
              
            
        }

        public void PenButtonEvent(UInt16 value)
        {
            if (value != LastPenButton)
            {
                Action<UInt16> action = new Action<ushort>(PenButtonEvent);
                if (TextPenButton.InvokeRequired)
                {
                    TextPenButton.Invoke(action, value);
                }
                else
                {
                    TextPenButton.Text = value.ToString();

                    //use the button do somthing
                    if (value == 1)
                    {
                        BtnClear_Click(null, null);
                    }
                }
                LastPenButton = value;
            }
        }

        private void BtnPenColor_Click(object sender, EventArgs e)
        {
            ColorDialog dlg = new ColorDialog();
            if (dlg.ShowDialog() == DialogResult.OK)
            {
                Color color = dlg.Color;
                BtnPenColor.BackColor = color;
                BtnPenColor.ForeColor = color;
                PenColor = color;
            }
        }
        private void TextPenWidth_KeyPress(object sender, KeyPressEventArgs e)
        {
            if (e.KeyChar != (int)Keys.Back && !Char.IsDigit(e.KeyChar))
            {
                e.Handled = true;
            }
        }
        private void PadPainter_Load(object sender, EventArgs e)
        {
            Win32SignAPI.signInitialize();
            Delegate_Notify = new DEVNOTIFYPROC(NotifyHandler);
            HandleNotifyProc = Win32SignAPI.signRegisterDevNotifyCallBack(Delegate_Notify);
        }

        public int NotifyHandler(STATUSPACKET status)
        {
            if (status.status == 1)
            {
                if (BtnOpen.InvokeRequired)
                {
                    Action<object, EventArgs> action = new Action<object, EventArgs>(BtnOpen_Click);
                    BtnOpen.Invoke(action, BtnOpen, null);
                }
                else
                {
                    BtnOpen_Click(BtnOpen, null);
                }
            }
            else
            {
                if (BtnClose.InvokeRequired)
                {
                    Action<object, EventArgs> action = new Action<object, EventArgs>(BtnClose_Click);
                    BtnClose.Invoke(action, BtnClose, null);
                }
                else
                {
                    BtnOpen_Click(BtnClose, null);
                }
            }
            return 0;
        }

        private void TextPenWidth_TextChanged(object sender, EventArgs e)
        {
            if (TextPenWidth.Text.Trim().Length > 0)
            {
                int value = Int32.Parse(TextPenWidth.Text);
                PenWidth = value;

                if (value < 1)
                {
                    PenWidth = 1;
                    TextPenWidth.Text = "1";
                }
                if (value > 10)
                {
                    PenWidth = 10;
                    TextPenWidth.Text = "10";
                }
            }
        }

        private void Save_Click(object sender, EventArgs e)
        {
            if (BufferImage != null)
            {
                SaveFileDialog dlg = new SaveFileDialog();
                dlg.Filter = "PNG(*.png)|*png";
                dlg.DefaultExt = "png";
                dlg.AddExtension = true;
                if (dlg.ShowDialog() == DialogResult.OK)
                {
                    String path = dlg.FileName;

                    BufferImage.Save(path, ImageFormat.Png);
                }
            }
        }

        private void PanelWorkView_Paint(object sender, PaintEventArgs e)
        {
            if (BufferImage != null)
            {
                e.Graphics.Clear(Color.FromArgb(0xff, 0xff, 0xff, 0xff));
                e.Graphics.DrawImage(BufferImage, new Point(0, 0));
            }
        }
    }
}
