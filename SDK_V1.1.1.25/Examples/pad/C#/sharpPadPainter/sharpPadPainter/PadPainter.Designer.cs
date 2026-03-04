namespace sharpPadPainter
{
    partial class PadPainter
    {
        /// <summary>
        /// 必需的设计器变量。
        /// </summary>
        private System.ComponentModel.IContainer components = null;

        /// <summary>
        /// 清理所有正在使用的资源。
        /// </summary>
        /// <param name="disposing">如果应释放托管资源，为 true；否则为 false。</param>
        protected override void Dispose(bool disposing)
        {
            if (disposing && (components != null))
            {
                components.Dispose();
            }
            base.Dispose(disposing);
        }

        #region Windows 窗体设计器生成的代码

        /// <summary>
        /// 设计器支持所需的方法 - 不要修改
        /// 使用代码编辑器修改此方法的内容。
        /// </summary>
        private void InitializeComponent()
        {
            this.groupBox1 = new System.Windows.Forms.GroupBox();
            this.Save = new System.Windows.Forms.Button();
            this.BtnClear = new System.Windows.Forms.Button();
            this.BtnClose = new System.Windows.Forms.Button();
            this.BtnOpen = new System.Windows.Forms.Button();
            this.TextPenWidth = new System.Windows.Forms.TextBox();
            this.BtnPenColor = new System.Windows.Forms.Button();
            this.label2 = new System.Windows.Forms.Label();
            this.label1 = new System.Windows.Forms.Label();
            this.groupBox2 = new System.Windows.Forms.GroupBox();
            this.textPress = new System.Windows.Forms.TextBox();
            this.label6 = new System.Windows.Forms.Label();
            this.TextPenButton = new System.Windows.Forms.TextBox();
            this.TextVKey = new System.Windows.Forms.TextBox();
            this.TextPKey = new System.Windows.Forms.TextBox();
            this.label5 = new System.Windows.Forms.Label();
            this.label4 = new System.Windows.Forms.Label();
            this.label3 = new System.Windows.Forms.Label();
            this.PanelWorkView = new System.Windows.Forms.Panel();
            this.titlX = new System.Windows.Forms.Label();
            this.label8 = new System.Windows.Forms.Label();
            this.texttitlX = new System.Windows.Forms.TextBox();
            this.texttitlY = new System.Windows.Forms.TextBox();
            this.groupBox1.SuspendLayout();
            this.groupBox2.SuspendLayout();
            this.SuspendLayout();
            // 
            // groupBox1
            // 
            this.groupBox1.Controls.Add(this.Save);
            this.groupBox1.Controls.Add(this.BtnClear);
            this.groupBox1.Controls.Add(this.BtnClose);
            this.groupBox1.Controls.Add(this.BtnOpen);
            this.groupBox1.Controls.Add(this.TextPenWidth);
            this.groupBox1.Controls.Add(this.BtnPenColor);
            this.groupBox1.Controls.Add(this.label2);
            this.groupBox1.Controls.Add(this.label1);
            this.groupBox1.Location = new System.Drawing.Point(12, 12);
            this.groupBox1.Name = "groupBox1";
            this.groupBox1.Size = new System.Drawing.Size(171, 259);
            this.groupBox1.TabIndex = 0;
            this.groupBox1.TabStop = false;
            this.groupBox1.Text = "Configuration";
            // 
            // Save
            // 
            this.Save.Location = new System.Drawing.Point(6, 166);
            this.Save.Name = "Save";
            this.Save.Size = new System.Drawing.Size(75, 23);
            this.Save.TabIndex = 8;
            this.Save.Text = "Save";
            this.Save.UseVisualStyleBackColor = true;
            this.Save.Click += new System.EventHandler(this.Save_Click);
            // 
            // BtnClear
            // 
            this.BtnClear.Location = new System.Drawing.Point(87, 166);
            this.BtnClear.Name = "BtnClear";
            this.BtnClear.Size = new System.Drawing.Size(75, 23);
            this.BtnClear.TabIndex = 7;
            this.BtnClear.Text = "Clear";
            this.BtnClear.UseVisualStyleBackColor = true;
            this.BtnClear.Click += new System.EventHandler(this.BtnClear_Click);
            // 
            // BtnClose
            // 
            this.BtnClose.Location = new System.Drawing.Point(87, 124);
            this.BtnClose.Name = "BtnClose";
            this.BtnClose.Size = new System.Drawing.Size(75, 23);
            this.BtnClose.TabIndex = 6;
            this.BtnClose.Text = "Close";
            this.BtnClose.UseVisualStyleBackColor = true;
            this.BtnClose.Click += new System.EventHandler(this.BtnClose_Click);
            // 
            // BtnOpen
            // 
            this.BtnOpen.Location = new System.Drawing.Point(6, 124);
            this.BtnOpen.Name = "BtnOpen";
            this.BtnOpen.Size = new System.Drawing.Size(75, 23);
            this.BtnOpen.TabIndex = 5;
            this.BtnOpen.Text = "Open";
            this.BtnOpen.UseVisualStyleBackColor = true;
            this.BtnOpen.Click += new System.EventHandler(this.BtnOpen_Click);
            // 
            // TextPenWidth
            // 
            this.TextPenWidth.Location = new System.Drawing.Point(96, 72);
            this.TextPenWidth.Name = "TextPenWidth";
            this.TextPenWidth.Size = new System.Drawing.Size(69, 21);
            this.TextPenWidth.TabIndex = 4;
            this.TextPenWidth.Text = "5";
            this.TextPenWidth.TextChanged += new System.EventHandler(this.TextPenWidth_TextChanged);
            this.TextPenWidth.KeyPress += new System.Windows.Forms.KeyPressEventHandler(this.TextPenWidth_KeyPress);
            // 
            // BtnPenColor
            // 
            this.BtnPenColor.BackColor = System.Drawing.SystemColors.ActiveCaptionText;
            this.BtnPenColor.FlatStyle = System.Windows.Forms.FlatStyle.Flat;
            this.BtnPenColor.Location = new System.Drawing.Point(96, 32);
            this.BtnPenColor.Name = "BtnPenColor";
            this.BtnPenColor.Size = new System.Drawing.Size(69, 18);
            this.BtnPenColor.TabIndex = 3;
            this.BtnPenColor.UseVisualStyleBackColor = false;
            this.BtnPenColor.Click += new System.EventHandler(this.BtnPenColor_Click);
            // 
            // label2
            // 
            this.label2.AutoSize = true;
            this.label2.Location = new System.Drawing.Point(6, 81);
            this.label2.Name = "label2";
            this.label2.Size = new System.Drawing.Size(53, 12);
            this.label2.TabIndex = 1;
            this.label2.Text = "PenWidth";
            // 
            // label1
            // 
            this.label1.AutoSize = true;
            this.label1.Location = new System.Drawing.Point(6, 38);
            this.label1.Name = "label1";
            this.label1.Size = new System.Drawing.Size(53, 12);
            this.label1.TabIndex = 0;
            this.label1.Text = "PenColor";
            // 
            // groupBox2
            // 
            this.groupBox2.Controls.Add(this.texttitlY);
            this.groupBox2.Controls.Add(this.texttitlX);
            this.groupBox2.Controls.Add(this.label8);
            this.groupBox2.Controls.Add(this.titlX);
            this.groupBox2.Controls.Add(this.textPress);
            this.groupBox2.Controls.Add(this.label6);
            this.groupBox2.Controls.Add(this.TextPenButton);
            this.groupBox2.Controls.Add(this.TextVKey);
            this.groupBox2.Controls.Add(this.TextPKey);
            this.groupBox2.Controls.Add(this.label5);
            this.groupBox2.Controls.Add(this.label4);
            this.groupBox2.Controls.Add(this.label3);
            this.groupBox2.Location = new System.Drawing.Point(12, 301);
            this.groupBox2.Name = "groupBox2";
            this.groupBox2.Size = new System.Drawing.Size(171, 259);
            this.groupBox2.TabIndex = 1;
            this.groupBox2.TabStop = false;
            this.groupBox2.Text = "Status";
            // 
            // textPress
            // 
            this.textPress.Location = new System.Drawing.Point(96, 149);
            this.textPress.Name = "textPress";
            this.textPress.Size = new System.Drawing.Size(66, 21);
            this.textPress.TabIndex = 6;
            // 
            // label6
            // 
            this.label6.AutoSize = true;
            this.label6.Location = new System.Drawing.Point(6, 149);
            this.label6.Name = "label6";
            this.label6.Size = new System.Drawing.Size(35, 12);
            this.label6.TabIndex = 5;
            this.label6.Text = "Press";
            // 
            // TextPenButton
            // 
            this.TextPenButton.Location = new System.Drawing.Point(96, 102);
            this.TextPenButton.Name = "TextPenButton";
            this.TextPenButton.Size = new System.Drawing.Size(66, 21);
            this.TextPenButton.TabIndex = 4;
            // 
            // TextVKey
            // 
            this.TextVKey.Location = new System.Drawing.Point(96, 61);
            this.TextVKey.Name = "TextVKey";
            this.TextVKey.Size = new System.Drawing.Size(66, 21);
            this.TextVKey.TabIndex = 4;
            // 
            // TextPKey
            // 
            this.TextPKey.Location = new System.Drawing.Point(96, 27);
            this.TextPKey.Name = "TextPKey";
            this.TextPKey.Size = new System.Drawing.Size(66, 21);
            this.TextPKey.TabIndex = 4;
            // 
            // label5
            // 
            this.label5.AutoSize = true;
            this.label5.Location = new System.Drawing.Point(6, 111);
            this.label5.Name = "label5";
            this.label5.Size = new System.Drawing.Size(59, 12);
            this.label5.TabIndex = 3;
            this.label5.Text = "PenButton";
            // 
            // label4
            // 
            this.label4.AutoSize = true;
            this.label4.Location = new System.Drawing.Point(6, 70);
            this.label4.Name = "label4";
            this.label4.Size = new System.Drawing.Size(29, 12);
            this.label4.TabIndex = 2;
            this.label4.Text = "VKey";
            // 
            // label3
            // 
            this.label3.AutoSize = true;
            this.label3.Location = new System.Drawing.Point(6, 30);
            this.label3.Name = "label3";
            this.label3.Size = new System.Drawing.Size(29, 12);
            this.label3.TabIndex = 1;
            this.label3.Text = "PKey";
            // 
            // PanelWorkView
            // 
            this.PanelWorkView.BorderStyle = System.Windows.Forms.BorderStyle.FixedSingle;
            this.PanelWorkView.Location = new System.Drawing.Point(216, 22);
            this.PanelWorkView.Name = "PanelWorkView";
            this.PanelWorkView.Size = new System.Drawing.Size(787, 538);
            this.PanelWorkView.TabIndex = 2;
            this.PanelWorkView.Paint += new System.Windows.Forms.PaintEventHandler(this.PanelWorkView_Paint);
            // 
            // titlX
            // 
            this.titlX.AutoSize = true;
            this.titlX.Location = new System.Drawing.Point(6, 189);
            this.titlX.Name = "titlX";
            this.titlX.Size = new System.Drawing.Size(35, 12);
            this.titlX.TabIndex = 7;
            this.titlX.Text = "titlX";
            // 
            // label8
            // 
            this.label8.AutoSize = true;
            this.label8.Location = new System.Drawing.Point(6, 227);
            this.label8.Name = "label8";
            this.label8.Size = new System.Drawing.Size(35, 12);
            this.label8.TabIndex = 8;
            this.label8.Text = "titlY";
            // 
            // texttitlX
            // 
            this.texttitlX.Location = new System.Drawing.Point(96, 185);
            this.texttitlX.Name = "texttitlX";
            this.texttitlX.Size = new System.Drawing.Size(66, 21);
            this.texttitlX.TabIndex = 9;
            // 
            // texttitlY
            // 
            this.texttitlY.Location = new System.Drawing.Point(96, 222);
            this.texttitlY.Name = "texttitlY";
            this.texttitlY.Size = new System.Drawing.Size(66, 21);
            this.texttitlY.TabIndex = 10;
            // 
            // PadPainter
            // 
            this.AutoScaleDimensions = new System.Drawing.SizeF(6F, 12F);
            this.AutoScaleMode = System.Windows.Forms.AutoScaleMode.Font;
            this.ClientSize = new System.Drawing.Size(1026, 572);
            this.Controls.Add(this.PanelWorkView);
            this.Controls.Add(this.groupBox2);
            this.Controls.Add(this.groupBox1);
            this.MaximizeBox = false;
            this.Name = "PadPainter";
            this.StartPosition = System.Windows.Forms.FormStartPosition.CenterScreen;
            this.Text = "PadPainter";
            this.FormClosed += new System.Windows.Forms.FormClosedEventHandler(this.PadPainter_FormClosed);
            this.Load += new System.EventHandler(this.PadPainter_Load);
            this.groupBox1.ResumeLayout(false);
            this.groupBox1.PerformLayout();
            this.groupBox2.ResumeLayout(false);
            this.groupBox2.PerformLayout();
            this.ResumeLayout(false);

        }

        #endregion

        private System.Windows.Forms.GroupBox groupBox1;
        private System.Windows.Forms.TextBox TextPenWidth;
        private System.Windows.Forms.Button BtnPenColor;
        private System.Windows.Forms.Label label2;
        private System.Windows.Forms.Label label1;
        private System.Windows.Forms.GroupBox groupBox2;
        private System.Windows.Forms.Label label5;
        private System.Windows.Forms.Label label4;
        private System.Windows.Forms.Label label3;
        private System.Windows.Forms.Panel PanelWorkView;
        private System.Windows.Forms.Button BtnClear;
        private System.Windows.Forms.Button BtnClose;
        private System.Windows.Forms.Button BtnOpen;
        private System.Windows.Forms.TextBox TextPenButton;
        private System.Windows.Forms.TextBox TextVKey;
        private System.Windows.Forms.TextBox TextPKey;
        private System.Windows.Forms.Button Save;
        private System.Windows.Forms.TextBox textPress;
        private System.Windows.Forms.Label label6;
        private System.Windows.Forms.TextBox texttitlY;
        private System.Windows.Forms.TextBox texttitlX;
        private System.Windows.Forms.Label label8;
        private System.Windows.Forms.Label titlX;
    }
}

