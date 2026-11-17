# V2 : génère le plan « Maison » (1600 x 2200 px), plus grand que l'écran sur les deux axes,
# pour tester le défilement. Fond crème, murs gris foncé, pièces nommées, ouvertures de portes.
#
# Usage (depuis la racine du projet) :
#   powershell -ExecutionPolicy Bypass -File outils\generer_plan_maison.ps1
# Pour utiliser un autre plan : remplacer simplement app\src\main\res\drawable-nodpi\plan_maison.png.

param(
    [string]$Sortie = (Join-Path $PSScriptRoot "..\app\src\main\res\drawable-nodpi\plan_maison.png")
)

Add-Type -AssemblyName System.Drawing

$largeur = 1600
$hauteur = 2200

$image = New-Object System.Drawing.Bitmap($largeur, $hauteur, [System.Drawing.Imaging.PixelFormat]::Format24bppRgb)
$g = [System.Drawing.Graphics]::FromImage($image)
$g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit

$creme = [System.Drawing.Color]::FromArgb(250, 244, 228)
$gris = [System.Drawing.Color]::FromArgb(60, 60, 60)
$g.Clear($creme)

$murInterieur = New-Object System.Drawing.Pen($gris, 12)
$murExterieur = New-Object System.Drawing.Pen($gris, 24)
$pinceauCreme = New-Object System.Drawing.SolidBrush($creme)
$pinceauTexte = New-Object System.Drawing.SolidBrush($gris)
$police = New-Object System.Drawing.Font("Arial", 30, [System.Drawing.FontStyle]::Bold, [System.Drawing.GraphicsUnit]::Pixel)
$centre = New-Object System.Drawing.StringFormat
$centre.Alignment = [System.Drawing.StringAlignment]::Center
$centre.LineAlignment = [System.Drawing.StringAlignment]::Center

# Accents écrits en code (le fichier .ps1 peut être lu en ANSI par PowerShell 5.1).
$e = [char]0x00E9

# Pièces : nom, x1, y1, x2, y2.
$pieces = @(
    @("Garage", 60, 60, 700, 700),
    @("Buanderie", 700, 60, 900, 700),
    @("Cuisine", 900, 60, 1540, 700),
    @("Entr${e}e", 60, 700, 700, 1100),
    @("Bureau", 60, 1100, 700, 1500),
    @("Chambre 1", 60, 1500, 700, 2140),
    @("Couloir", 700, 700, 900, 2140),
    @("Salon", 900, 700, 1540, 1400),
    @("Chambre 2", 900, 1400, 1540, 1780),
    @("Chambre 3", 900, 1780, 1240, 2140),
    @("Salle de bain", 1240, 1780, 1540, 2140)
)

foreach ($p in $pieces) {
    $g.DrawRectangle($murInterieur, $p[1], $p[2], $p[3] - $p[1], $p[4] - $p[2])
}
$g.DrawRectangle($murExterieur, 60, 60, 1480, 2080)

# Portes : rectangles crème qui coupent le mur (x1, y1, x2, y2).
$portes = @(
    @(200, 40, 560, 80),      # porte du garage (extérieur)
    @(40, 850, 80, 950),      # porte d'entrée (extérieur)
    @(680, 300, 720, 400),    # garage - buanderie
    @(880, 300, 920, 400),    # buanderie - cuisine
    @(750, 680, 850, 720),    # buanderie - couloir
    @(1100, 680, 1250, 720),  # cuisine - salon
    @(680, 850, 720, 950),    # entrée - couloir
    @(680, 1250, 720, 1350),  # bureau - couloir
    @(680, 1750, 720, 1850),  # chambre 1 - couloir
    @(880, 950, 920, 1100),   # couloir - salon
    @(880, 1550, 920, 1650),  # couloir - chambre 2
    @(880, 1900, 920, 2000),  # couloir - chambre 3
    @(1220, 1900, 1260, 2000) # chambre 3 - salle de bain
)
foreach ($d in $portes) {
    $g.FillRectangle($pinceauCreme, $d[0], $d[1], $d[2] - $d[0], $d[3] - $d[1])
}

foreach ($p in $pieces) {
    $zone = New-Object System.Drawing.RectangleF($p[1], $p[2], ($p[3] - $p[1]), ($p[4] - $p[2]))
    $g.DrawString($p[0], $police, $pinceauTexte, $zone, $centre)
}

$cheminComplet = [System.IO.Path]::GetFullPath($Sortie)
$image.Save($cheminComplet, [System.Drawing.Imaging.ImageFormat]::Png)
$g.Dispose()
$image.Dispose()
Write-Output "Plan genere : $cheminComplet"
