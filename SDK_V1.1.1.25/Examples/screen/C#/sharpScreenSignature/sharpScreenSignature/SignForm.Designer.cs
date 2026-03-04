namespace sharpScreenSignature
{
    partial class SignForm
    {
        /// <summary>
        /// Required designer variable.
        /// </summary>
        private System.ComponentModel.IContainer components = null;

        /// <summary>
        /// Clean up any resources being used.
        /// </summary>
        /// <param name="disposing">true if managed resources should be disposed; otherwise, false.</param>
        protected override void Dispose(bool disposing)
        {
            if (disposing && (components != null))
            {
                components.Dispose();
            }
            base.Dispose(disposing);
        }

        #region Windows Form Designer generated code

        /// <summary>
        /// Required method for Designer support - do not modify
        /// the contents of this method with the code editor.
        /// </summary>
        private void InitializeComponent()
        {
            System.ComponentModel.ComponentResourceManager resources = new System.ComponentModel.ComponentResourceManager(typeof(SignForm));
            this.PanelWorkView = new System.Windows.Forms.Panel();
            this.SignBtnCancel = new System.Windows.Forms.Button();
            this.SignBtnClear = new System.Windows.Forms.Button();
            this.SignBtnConfirm = new System.Windows.Forms.Button();
            this.SuspendLayout();
            // 
            // PanelWorkView
            // 
            this.PanelWorkView.BorderStyle = System.Windows.Forms.BorderStyle.FixedSingle;
            this.PanelWorkView.Location = new System.Drawing.Point(79, 96);
            this.PanelWorkView.Name = "PanelWorkView";
            this.PanelWorkView.Size = new System.Drawing.Size(848, 462);
            this.PanelWorkView.TabIndex = 0;
            // 
            // SignBtnCancel
            // 
            this.SignBtnCancel.Font = new System.Drawing.Font("Arial Narrow", 21.75F, System.Drawing.FontStyle.Bold, System.Drawing.GraphicsUnit.Point, ((byte)(0)));
            this.SignBtnCancel.Location = new System.Drawing.Point(137, 600);
            this.SignBtnCancel.Name = "SignBtnCancel";
            this.SignBtnCancel.Size = new System.Drawing.Size(150, 81);
            this.SignBtnCancel.TabIndex = 1;
            this.SignBtnCancel.Text = "Cancel";
            this.SignBtnCancel.UseVisualStyleBackColor = true;
            this.SignBtnCancel.Click += new System.EventHandler(this.SignBtnCancel_Click);
            // 
            // SignBtnClear
            // 
            this.SignBtnClear.Font = new System.Drawing.Font("Arial Narrow", 21.75F, System.Drawing.FontStyle.Bold, System.Drawing.GraphicsUnit.Point, ((byte)(0)));
            this.SignBtnClear.Location = new System.Drawing.Point(375, 600);
            this.SignBtnClear.Name = "SignBtnClear";
            this.SignBtnClear.Size = new System.Drawing.Size(150, 81);
            this.SignBtnClear.TabIndex = 2;
            this.SignBtnClear.Text = "Clear";
            this.SignBtnClear.UseVisualStyleBackColor = true;
            this.SignBtnClear.Click += new System.EventHandler(this.SignBtnClear_Click);
            // 
            // SignBtnConfirm
            // 
            this.SignBtnConfirm.Font = new System.Drawing.Font("Arial Narrow", 21.75F, System.Drawing.FontStyle.Bold, System.Drawing.GraphicsUnit.Point, ((byte)(0)));
            this.SignBtnConfirm.Location = new System.Drawing.Point(613, 600);
            this.SignBtnConfirm.Name = "SignBtnConfirm";
            this.SignBtnConfirm.Size = new System.Drawing.Size(150, 81);
            this.SignBtnConfirm.TabIndex = 3;
            this.SignBtnConfirm.Text = "Confirm";
            this.SignBtnConfirm.UseVisualStyleBackColor = true;
            this.SignBtnConfirm.Click += new System.EventHandler(this.SignBtnConfirm_Click);
            // 
            // SignForm
            // 
            this.AutoScaleDimensions = new System.Drawing.SizeF(6F, 12F);
            this.AutoScaleMode = System.Windows.Forms.AutoScaleMode.Font;
            this.BackgroundImage = ((System.Drawing.Image)(resources.GetObject("$this.BackgroundImage")));
            this.ClientSize = new System.Drawing.Size(1013, 692);
            this.Controls.Add(this.SignBtnConfirm);
            this.Controls.Add(this.SignBtnClear);
            this.Controls.Add(this.SignBtnCancel);
            this.Controls.Add(this.PanelWorkView);
            this.FormBorderStyle = System.Windows.Forms.FormBorderStyle.None;
            this.Name = "SignForm";
            this.Text = "SignForm";
            this.TopMost = true;
            this.FormClosed += new System.Windows.Forms.FormClosedEventHandler(this.SignForm_FormClosed);
            this.ResumeLayout(false);

        }

        #endregion

        private System.Windows.Forms.Panel PanelWorkView;
        private System.Windows.Forms.Button SignBtnCancel;
        private System.Windows.Forms.Button SignBtnClear;
        private System.Windows.Forms.Button SignBtnConfirm;
    }
}