Add-Type -AssemblyName System.Drawing
$bmp = New-Object System.Drawing.Bitmap(512, 512)
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.SmoothingMode = 'AntiAlias'
$g.TextRenderingHint = 'AntiAlias'

# 背景渐变 深蓝->浅蓝
$r = New-Object System.Drawing.RectangleF(0, 0, 512, 512)
$b = New-Object System.Drawing.Drawing2D.LinearGradientBrush((New-Object System.Drawing.Point(0,0)), (New-Object System.Drawing.Point(512,512)), [System.Drawing.Color]::FromArgb(21,101,192), [System.Drawing.Color]::FromArgb(33,150,243))
$g.FillRectangle($b, $r); $b.Dispose()

# 圆角矩形边框 - 用FillRectangle代替(半透明白色内边框)
$innerRect = New-Object System.Drawing.RectangleF(30, 30, 452, 452)
$g.FillRectangle((New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255,255,255,20))), $innerRect)
$g.DrawRectangle((New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(255,255,255,80),3)), 30,30,452,452)

# 摄像头大圆 (镜头)
$g.FillEllipse((New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(80,255,255,255))), 200,160,112,112)
$g.DrawEllipse((New-Object System.Drawing.Pen([System.Drawing.Color]::White,5)), 200,160,112,112)
$g.FillEllipse((New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(25,118,210))), 226,186,60,60)
# 高光
$g.FillEllipse((New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(220,255,255,255))), 240,196,16,16)
$g.FillEllipse((New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(150,255,255,255))), 256,212,8,8)

# WiFi 波纹 (3层弧线)
$pen1 = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(180,255,255,255),3.5)
$pen2 = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(140,255,255,255),3)
$pen3 = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(100,255,255,255),2.5)
$g.DrawArc($pen1, 110,260,100,100,20,140)
$g.DrawArc($pen2, 88,238,144,144,15,150)
$g.DrawArc($pen3, 66,216,188,188,10,160)
$pen1.Dispose(); $pen2.Dispose(); $pen3.Dispose()

# 盾牌 - 用绿色填充多边形
$p1 = New-Object System.Drawing.PointF(256,338)
$p2 = New-Object System.Drawing.PointF(210,362)
$p3 = New-Object System.Drawing.PointF(230,410)
$p4 = New-Object System.Drawing.PointF(282,410)
$p5 = New-Object System.Drawing.PointF(302,362)
$points = @($p1,$p2,$p3,$p4,$p5)
$g.FillPolygon((New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(76,175,80,230))), $points)
$g.DrawPolygon((New-Object System.Drawing.Pen([System.Drawing.Color]::White,3)), $points)

# 勾号
$ck = New-Object System.Drawing.Pen([System.Drawing.Color]::White,4)
$ck.StartCap = 'Round'; $ck.EndCap = 'Round'
$g.DrawLine($ck, 232,370, 254,394)
$g.DrawLine($ck, 254,394, 280,358)
$ck.Dispose()

# 底部文字 MONITOR
$fnt = New-Object System.Drawing.Font("Segoe UI",28,[System.Drawing.FontStyle]::Bold)
$sf = New-Object System.Drawing.StringFormat
$sf.Alignment = 'Center'
$sf.LineAlignment = 'Center'
$g.DrawString("MONITOR",$fnt,(New-Object System.Drawing.SolidBrush([System.Drawing.Color]::White)),(New-Object System.Drawing.RectangleF(15,440,482,60)),$sf)

$g.Dispose()
$bmp.Save("F:\android\andwin\docs\icon-512.png",[System.Drawing.Imaging.ImageFormat]::Png)
$bmp.Dispose()
Write-Host "OK - icon generated"
Write-Host ("Size: " + [math]::Round((Get-Item "F:\android\andwin\docs\icon-512.png").Length / 1KB, 2) + " KB")
