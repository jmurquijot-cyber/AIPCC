from PIL import Image, ImageDraw, ImageFont

W, H = 1400, 1860
img = Image.new("RGB", (W, H), "white")
d = ImageDraw.Draw(img)
regular = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
bold = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"

def font(size, strong=False):
    return ImageFont.truetype(bold if strong else regular, size)

def center(text, xy, size, fill="black", strong=False):
    d.text(xy, text, font=font(size, strong), fill=fill, anchor="mm")

for x in range(0, W, 44): d.line((x, 0, x, H), fill="#e1e1e1")
for y in range(0, H, 44): d.line((0, y, W, y), fill="#e1e1e1")

# JW.ORG logo and heading
d.rectangle((60, 18, 320, 253), fill="#3e5b91")
center("JW", (190, 90), 82, "white")
center(".ORG", (190, 190), 68, "white")
center("CONGREGACIÓN VILLA RICA", (875, 62), 48, "#051ccf")
center("Boca del Río, Ver.", (875, 112), 30, strong=True)
center("(ASAMBLEA DE CIRCUITO)", (875, 178), 34, "#7e1870")
center("(Domingo, 20 de septiembre de 2026)", (875, 225), 26, strong=True)
center("HORA DE SALIDA: (6:30 A.M.)  (SALÓN DEL REINO)", (875, 273), 28, "red", True)
d.line((540, 294, 1372, 294), fill="black", width=2)

# Bus and passenger heading
d.rectangle((545, 318, 1372, 482), fill="#ccffcc", outline="black", width=3)
center("AUTOBÚS No. 1", (958, 360), 40, strong=True)
center("Capitán: MANUEL URQUIJO", (958, 410), 27, strong=True)
center("Auxiliar: ANA HERNÁNDEZ", (958, 454), 27, strong=True)

# Top-down bus body
bx, by, bw, bh = 68, 390, 409, 1370
d.rounded_rectangle((bx, by, bx+bw, by+bh), radius=92, fill="#cdcdcd", outline="black", width=9)
d.polygon([(bx+55,by+54),(bx+100,by+175),(bx+bw-100,by+175),(bx+bw-55,by+54)], fill="#eef5f8", outline="black")
d.ellipse((bx+bw-110,by+102,bx+bw-46,by+166), fill="black")
d.ellipse((bx+bw-98,by+114,bx+bw-58,by+154), fill="#d3d3d3")
d.rectangle((bx-22,by+105,bx+5,by+195), fill="black")
d.rectangle((bx+bw-5,by+105,bx+bw+22,by+195), fill="black")

names = [
    "MANUEL URQUIJO", "ANA HERNÁNDEZ", "JOSÉ RAMÍREZ", "MARÍA GÓMEZ",
    "SAMUEL TEMICH", "ELENA CRUZ", "DAVID MARTÍNEZ", "LAURA PÉREZ",
    "MISAEL VITAL", "SOFÍA RAMOS", "ANDRÉ HERNÁNDEZ", "PAOLA REYES",
    "JARED CARMONA", "RUBY FREYRE", "RODOLFO MARTÍNEZ", "CLAUDIA GÓMEZ",
    "JOSÍAS URQUIJO", "ESTHER PÉREZ", "FRANCISCO GÓMEZ", "GUADALUPE RAMÍREZ",
    "NOHADÍAS VITAL", "XIMENA HERNÁNDEZ", "ARMANDO TEMICH", "MAYRA ISIDRO",
    "JEHOSAFAT VITAL", "MONSERRAT OCAMPO", "EZEQUIEL VENTURA", "JAEL CRUZ",
    "SUSANA MORALES", "ELIZABETH RODRÍGUEZ", "SEGUNDINO DAMIÁN", "MILAGROS SORIANO",
    "PETRA SOLÍS", "VICTORIA GARCÍA", "FRANCISCO MARTÍNEZ", "LUCERO VITAL",
    "AGUSTINA SÁNCHEZ", "BERTHA RAMOS", "JESICA VENTURA", "TERESA TABAL", "RUTH JIMÉNEZ"
]

def seat_icon(x, y, n):
    captain, assistant = n == 1, n == 2
    d.rounded_rectangle((x,y+12,x+58,y+70), radius=7, fill="#ffd24c" if captain else "#a6e6a0" if assistant else "white", outline="#444", width=3)
    d.rectangle((x+7,y,x+51,y+18), fill="#414141")
    center(str(n), (x+29,y+52), 19, strong=True)

top = by + 205
step = 104
positions = [bx+43, bx+127, bx+221, bx+308]
n = 1
for row in range(9):
    for px in positions:
        seat_icon(px, top+row*step, n); n += 1
for px in [bx+13,bx+89,bx+165,bx+241,bx+317]:
    seat_icon(px, top+9*step, n); n += 1
d.rectangle((bx+46,by+bh-43,bx+bw-46,by+bh-21), fill="#e01919")

# Passenger list: 1/3, 2/4 in each block, with rear row 37-41.
def list_seat(x, y, n):
    captain, assistant = n == 1, n == 2
    d.rectangle((x,y,x+48,y+40), fill="#ffd24c" if captain else "#a6e6a0" if assistant else "#67c9eb", outline="black", width=2)
    d.rectangle((x+48,y,x+400,y+40), fill="#fafafa", outline="black", width=2)
    center(str(n), (x+24,y+20), 22)
    text = names[n-1] + (" · CAPITÁN" if captain else " · AUXILIAR" if assistant else "")
    d.text((x+58,y+20), text, font=font(17, captain or assistant), fill="black", anchor="lm")

cy = 505
for base in range(1, 37, 4):
    list_seat(545,cy,base); list_seat(972,cy,base+2)
    list_seat(545,cy+44,base+1); list_seat(972,cy+44,base+3)
    cy += 104
list_seat(545,cy,37); list_seat(972,cy,40)
list_seat(545,cy+44,38); list_seat(972,cy+44,41)
list_seat(545,cy+88,39)

img.save("/workspace/scratch/8d9b3f5d421a/transport-report-preview-v1.8.2.jpg", quality=96)
