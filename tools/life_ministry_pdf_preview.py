from pathlib import Path
from reportlab.pdfgen import canvas
from reportlab.lib.colors import HexColor, black, white
from reportlab.pdfbase.pdfmetrics import stringWidth

OUT=Path(__file__).resolve().parents[1]/"output"/"pdf"/"Vida_Ministerio_vista_previa.pdf"
OUT.parent.mkdir(parents=True,exist_ok=True)
W,H=792,612
weeks=[]
for week,day in enumerate((7,14,21,28,35),1):
    date=f"miércoles, {day if day<32 else 4} de octubre de 2026 · Presidente: MANUEL ROMERO"
    rows=[]
    for number in range(1,9):
        name="Estudio del Libro" if number==8 else f"Asignación semanal {number}"
        reader="LECTOR DEL ESTUDIO" if number==8 else "—"
        topic="Tema extenso de ejemplo para comprobar que el contenido permanezca dentro de su celda sin invadir columnas adyacentes."
        rows.append((number,10 if number<8 else 30,name,"PARTICIPANTE ASIGNADO",reader,topic))
    weeks.append((date,rows))

def wrap(text,width,size,max_lines,bold=False):
    font="Helvetica-Bold" if bold else "Helvetica"; lines=[]; line=""
    for word in text.split():
        nxt=word if not line else line+" "+word
        if line and stringWidth(nxt,font,size)>width:lines.append(line);line=word
        else:line=nxt
    if line:lines.append(line)
    clipped=len(lines)>max_lines;lines=lines[:max_lines] or [""]
    if clipped:
        value=lines[-1]
        while value and stringWidth(value+"…",font,size)>width:value=value[:-1]
        lines[-1]=value+"…"
    return lines

def cell(pdf,value,x,y,w,h,size,color=black,bold=False,max_lines=3):
    font="Helvetica-Bold" if bold else "Helvetica";pdf.setFont(font,size);pdf.setFillColor(color)
    lines=wrap(value,w-6,size,max_lines,bold);leading=size*1.05;base=y+h/2+(len(lines)-1)*leading/2-size*.32
    for i,line in enumerate(lines):pdf.drawCentredString(x+w/2,base-i*leading,line)

def box(pdf,x,y,w,h,fill):
    pdf.setFillColor(fill);pdf.setStrokeColor(black);pdf.setLineWidth(.6);pdf.rect(x,y,w,h,fill=1,stroke=1)

pdf=canvas.Canvas(str(OUT),pagesize=(W,H));pdf.setFillColor(white);pdf.rect(0,0,W,H,fill=1,stroke=0)
y=H-32;box(pdf,0,y,W,32,HexColor("#00B050"));cell(pdf,"PROGRAMA VIDA Y MINISTERIO",0,y,W,32,14,white,True,1)
headers=[0,48,190,400,550,792];section=17;count=sum(len(rows) for _,rows in weeks);row_h=max(8.5,min(36,(H-32-len(weeks)*section*2-2)/count))
for title,rows in weeks:
    y-=section;box(pdf,0,y,W,section,HexColor("#0D6EFD"));cell(pdf,title,0,y,W,section,8.7,white,True,1)
    y-=section
    labels=("NÚM. / TIEMPO","ASIGNACIÓN","PARTICIPANTE","LECTOR","TEMA")
    fills=("#BFBFBF","#BFBFBF","#ED7D31","#FFC000","#955FEF")
    for i,label in enumerate(labels):box(pdf,headers[i],y,headers[i+1]-headers[i],section,HexColor(fills[i]));cell(pdf,label,headers[i],y,headers[i+1]-headers[i],section,7,white if i==4 else black,True,2)
    for number,minutes,name,participant,reader,topic in rows:
        y-=row_h
        values=(f"{number} · {minutes} min.",name,participant,reader,topic)
        for i,value in enumerate(values):box(pdf,headers[i],y,headers[i+1]-headers[i],row_h,HexColor("#F8F8F8"));cell(pdf,value,headers[i],y,headers[i+1]-headers[i],row_h,max(4.8,min(6.5,row_h*.27)),black,i in (1,2,3),4)
pdf.showPage();pdf.save();print(OUT)
