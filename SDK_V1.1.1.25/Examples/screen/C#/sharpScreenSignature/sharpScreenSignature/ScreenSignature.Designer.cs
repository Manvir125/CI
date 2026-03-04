namespace sharpScreenSignature
{
    partial class ScreenSignature
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
            this.PicSignResult = new System.Windows.Forms.PictureBox();
            this.groupBox1 = new System.Windows.Forms.GroupBox();
            this.TextPenWidth = new System.Windows.Forms.TextBox();
            this.BtnPenColor = new System.Windows.Forms.Button();
            this.label2 = new System.Windows.Forms.Label();
            this.label1 = new System.Windows.Forms.Label();
            this.BtnBeginSign = new System.Windows.Forms.Button();
            this.BtnEndSign = new System.Windows.Forms.Button();
            ((System.ComponentModel.ISupportInitialize)(this.PicSignResult)).BeginInit();
            this.groupBox1.SuspendLayout();
            this.SuspendLayout();
            // 
            // PicSignResult
            // 
            this.PicSignResult.BorderStyle = System.Windows.Forms.BorderStyle.FixedSingle;
            this.PicSignResult.Location = new System.Drawing.Point(12, 12);
            this.PicSignResult.Name = "PicSignResult";
            this.PicSignResult.Size = new System.Drawing.Size(443, 274);
            this.PicSignResult.SizeMode = System.Windows.Forms.PictureBoxSizeMode.StretchImage;
            this.PicSignResult.TabIndex = 0;
            this.PicSignResult.TabStop = false;
            // 
            // groupBox1
            // 
            this.groupBox1.Controls.Add(this.TextPenWidth);
            this.groupBox1.Controls.Add(this.BtnPenColor);
            this.groupBox1.Controls.Add(this.label2);
            this.groupBox1.Controls.Add(this.label1);
            this.groupBox1.Location = new System.Drawing.Point(482, 12);
            this.groupBox1.Name = "groupBox1";
            this.groupBox1.Size = new System.Drawing.Size(199, 274);
            this.groupBox1.TabIndex = 2;
            this.groupBox1.TabStop = false;
            this.groupBox1.Text = "Configuration";
            // 
            // TextPenWidth
            // 
            this.TextPenWidth.Location = new System.Drawing.Point(110, 66);
            this.TextPenWidth.Name = "TextPenWidth";
            this.TextPenWidth.Size = new System.Drawing.Size(69, 21);
            this.TextPenWidth.TabIndex = 3;
            this.TextPenWidth.Text = "5";
            this.TextPenWidth.TextChanged += new System.EventHandler(this.TextPenWidth_TextChanged);
            this.TextPenWidth.KeyPress += new System.Windows.Forms.KeyPressEventHandler(this.TextPenWidth_KeyPress);
            // 
            // BtnPenColor
            // 
            this.BtnPenColor.BackColor = System.Drawing.SystemColors.ActiveCaptionText;
            this.BtnPenColor.FlatStyle = System.Windows.Forms.FlatStyle.Flat;
            this.BtnPenColor.Location = new System.Drawing.Point(110, 34);
            this.BtnPenColor.Name = "BtnPenColor";
            this.BtnPenColor.Size = new System.Drawing.Size(69, 18);
            this.BtnPenColor.TabIndex = 2;
            this.BtnPenColor.UseVisualStyleBackColor = false;
            this.BtnPenColor.Click += new System.EventHandler(this.BtnPenColor_Click);
            // 
            // label2
            // 
            this.label2.AutoSize = true;
            this.label2.Location = new System.Drawing.Point(19, 75);
            this.label2.Name = "label2";
            this.label2.Size = new System.Drawing.Size(53, 12);
            this.label2.TabIndex = 1;
            this.label2.Text = "PenWidth";
            // 
            // label1
            // 
            this.label1.AutoSize = true;
            this.label1.Location = new System.Drawing.Point(19, 37);
            this.label1.Name = "label1";
            this.label1.Size = new System.Drawing.Size(53, 12);
            this.label1.TabIndex = 0;
            this.label1.Text = "PenColor";
            // 
            // BtnBeginSign
            // 
            this.BtnBeginSign.Location = new System.Drawing.Point(482, 303);
            this.BtnBeginSign.Name = "BtnBeginSign";
            this.BtnBeginSign.Size = new System.Drawing.Size(75, 23);
            this.BtnBeginSign.TabIndex = 3;
            this.BtnBeginSign.Text = "BeginSign";
            this.BtnBeginSign.UseVisualStyleBackColor = true;
            this.BtnBeginSign.Click += new System.EventHandler(this.BtnBeginSign_Click);
            // 
            // BtnEndSign
            // 
            this.BtnEndSign.Location = new System.Drawing.Point(606, 303);
            this.BtnEndSign.Name = "BtnEndSign";
            this.BtnEndSign.Size = new System.Drawing.Size(75, 23);
            this.BtnEndSign.TabIndex = 4;
            this.BtnEndSign.Text = "EndSign";
            this.BtnEndSign.UseVisualStyleBackColor = true;
            this.BtnEndSign.Click += new System.EventHandler(this.BtnEndSign_Click);
            // 
            // ScreenSignature
            // 
            this.AutoScaleDimensions = new System.Drawing.SizeF(6F, 12F);
            this.AutoScaleMode = System.Windows.Forms.AutoScaleMode.Font;
            this.ClientSize = new System.Drawing.Size(701, 338);
            this.Controls.Add(this.BtnEndSign);
            this.Controls.Add(this.BtnBeginSign);
            this.Controls.Add(this.groupBox1);
            this.Controls.Add(this.PicSignResult);
            this.FormBorderStyle = System.Windows.Forms.FormBorderStyle.FixedSingle;
            this.MaximizeBox = false;
            this.Name = "ScreenSignature";
            this.StartPosition = System.Windows.Forms.FormStartPosition.CenterScreen;
            this.Text = "ScreenSignature";
            this.FormClosed += new System.Windows.Forms.FormClosedEventHandler(this.ScreenSignature_FormClosed);
            ((System.ComponentModel.ISupportInitialize)(this.PicSignResult)).EndInit();
            this.groupBox1.ResumeLayout(false);
            this.groupBox1.PerformLayout();
            this.ResumeLayout(false);

        }

        #endregion

        private System.Windows.Forms.PictureBox PicSignResult;
        private System.Windows.Forms.GroupBox groupBox1;
        private System.Windows.Forms.TextBox TextPenWidth;
        private System.Windows.Forms.Button BtnPenColor;
        private System.Windows.Forms.Label label2;
        private System.Windows.Forms.Label label1;
        private System.Windows.Forms.Button BtnBeginSign;
        private System.Windows.Forms.Button BtnEndSign;
    }
}

