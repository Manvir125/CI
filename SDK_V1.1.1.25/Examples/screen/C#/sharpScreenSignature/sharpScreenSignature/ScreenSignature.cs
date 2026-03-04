using System;
using System.Collections.Generic;
using System.ComponentModel;
using System.Data;
using System.Drawing;
using System.Linq;
using System.Text;
using System.Windows.Forms;
using SignAPI;

namespace sharpScreenSignature
{
    public partial class ScreenSignature : Form
    {
        private int PenWidth = 5;
        private Color PenColor = Color.Black;
    
        private TABLET_DEVICEINFO DevInfo;
        private static DATAPACKETPRCO Delegate_DataPacket = null;
        private static DEVNOTIFYPROC Delegate_Notify = null;

        private int HandleDataProc = 0;
        private int HandleNotifyProc = 0;

        SignForm sign_form =null;
        public ScreenSignature()
        {
            InitializeComponent();
        }

        private void BtnBeginSign_Click(object sender, EventArgs e)
        {
            if(sign_form!=null)
            {
                sign_form.Close();
                PicSignResult.Image = null;
            }
            if (Win32SignAPI.signGetDeviceStatus() == ErrorCode.ERR_OK)
            {
                RECT ScreenRect = new RECT();
                if (Win32SignAPI.signGetScreenRect(ref ScreenRect)==ErrorCode.ERR_OK)
                {
                    if(Win32SignAPI.signOpenDevice()==ErrorCode.ERR_OK)
                    {                      
                        sign_form = new SignForm();
                        sign_form.Show(this);
                        sign_form.Left = ScreenRect.left;
                        sign_form.Top = ScreenRect.top;
                        sign_form.Width = ScreenRect.Width;
                        sign_form.Height = ScreenRect.Height;

                        Win32SignAPI.signGetDeviceInfo(ref DevInfo);
                        Delegate_DataPacket = new DATAPACKETPRCO(DataPacketHandler);
                        Win32SignAPI.signRegisterDataCallBack(Delegate_DataPacket);

                        sign_form.SetResultPictureBox(PicSignResult);
                        sign_form.SetInfo(DevInfo.axisX.max, DevInfo.axisY.max, DevInfo.pressure,PenWidth,PenColor,ScreenRect);
                        sign_form.ResetLayout();
                    }
                }
                else
                {
                    MessageBox.Show("NO display device availabel.");
                }
            }
            else
            {
                MessageBox.Show("NO signature device availabel.");
            }
        }

        public int DataPacketHandler(DATAPACKET packet)
        {
            if (packet.eventtype == EventType.EventType_Pen)
            {
                //DrawSign(packet.x, packet.y, packet.pressure, packet.penstatus);
                if(!sign_form.IsDisposed)
                {
                    sign_form.PenEvent(packet);
                }
            }
            return 0;
        }

        private void BtnEndSign_Click(object sender, EventArgs e)
        {
            if (HandleDataProc > 0)
                Win32SignAPI.signUnregisterDataCallBack(HandleDataProc);

            Win32SignAPI.signCloseDevice();

            if (sign_form != null)
                sign_form.Close();
        }

        private void ScreenSignature_FormClosed(object sender, FormClosedEventArgs e)
        {
            if (HandleDataProc > 0)
                Win32SignAPI.signUnregisterDataCallBack(HandleDataProc);
            if (HandleNotifyProc > 0)
                Win32SignAPI.signUnregisterDevNotifyCallBack(HandleNotifyProc);

            Win32SignAPI.signCloseDevice();
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

        private void TextPenWidth_KeyPress(object sender, KeyPressEventArgs e)
        {
            if (e.KeyChar != (int)Keys.Back && !Char.IsDigit(e.KeyChar))
            {
                e.Handled = true;
            }
        }
    }
}
