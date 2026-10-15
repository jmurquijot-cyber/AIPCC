from pathlib import Path
from reportlab.pdfgen import canvas
from reportlab.lib.colors import HexColor, black, white
from reportlab.pdfbase.pdfmetrics import stringWidth

OUT=Path(__file__).resolve().parents[1]/"output"/"pdf"/"Programa_mensual_vista_previa.pdf"
OUT.parent.mkdir(parents=True,exist_ok=True)

weeks=[
 ("miércoles, 7 de octubre de 2026 · Presidente: MANUEL ROMERO",[
  (4,"Lectura de la Biblia","CUAUHTÉMOC MISARTE","SAMUEL TEMICH","Jeremías capítulo 40 versículos 1 a 12 · Lectura y explicación de los acontecimientos principales · Lección: 5"),
  (2,"Empiece conversaciones","ELIZABETH DE RODRÍGUEZ ↔ HORTENCIA DEL RÍO","PENDIENTE","Lección: Hacer discípulos usando una obra de amor, lección 2 punto 3. Tema de práctica para la conversación inicial."),
  (2,"Empiece conversaciones","HORTENCIA DEL RÍO ↔ ELIZABETH DE RODRÍGUEZ","PENDIENTE","Lección: Hacer discípulos usando una obra de amor, lección 2 punto 5. Información extensa para comprobar el ajuste automático."),
  (4,"Empiece conversaciones","PENDIENTE","PENDIENTE","Una persona se queda mirando la sección de publicaciones. Inicie una conversación breve y natural."),
  (3,"Explique sus creencias","EZEQUIEL VENTURA ↔ MANUEL URQUIJO","PENDIENTE","Preguntas sobre la Biblia. ¿Qué dice la Biblia acerca de los últimos días? Lección: 8."),]),
 ("miércoles, 14 de octubre de 2026",[
  (4,"Lectura de la Biblia","PENDIENTE","PENDIENTE","Jeremías capítulo 43 versículos 1 a 13 · Lección: 10"),
  (3,"Empiece conversaciones","PENDIENTE","PENDIENTE","Lección: Hacer discípulos usando una obra de amor, lección 1 punto 5."),
  (4,"Haga revisitas","PENDIENTE","PENDIENTE","Una de las verdades del apéndice. Ayude a la persona a comprenderla usando preguntas."),
  (6,"¿Qué diría?","PENDIENTE","PENDIENTE","Mencione una imagen y luego pregunte cómo podría empezar una conversación con alguien que está leyendo la Biblia."),]),
 ("miércoles, 21 de octubre de 2026",[
  (4,"Lectura de la Biblia","PENDIENTE","PENDIENTE","Jeremías capítulo 46 versículos 1 a 28 · Lección: 3"),
  (3,"Empiece conversaciones","PENDIENTE","PENDIENTE","Predicación pública. Inicie una conversación y ofrezca un curso bíblico."),
  (4,"Haga revisitas","PENDIENTE","PENDIENTE","Continúe una conversación anterior y muestre cómo encontrar una respuesta en la Biblia."),
  (5,"Haga discípulos","PENDIENTE","PENDIENTE","Anime a su estudiante a asistir a las reuniones. Use información de la guía de actividades."),]),
 ("miércoles, 28 de octubre de 2026",[
  (4,"Lectura de la Biblia","PENDIENTE","PENDIENTE","Jeremías capítulo 48 versículos 1 a 20 · Lección: 6"),
  (3,"Empiece conversaciones","PENDIENTE","PENDIENTE","Lección: Hacer discípulos usando una obra de amor, lección 5 punto 4."),
  (4,"Haga revisitas","PENDIENTE","PENDIENTE","Una de las verdades del apéndice. Continúe la conversación con tacto."),
  (5,"Haga discípulos","PENDIENTE","PENDIENTE","Anime a su estudiante de la Biblia a prepararse para las reuniones."),]),
]

def wrap(text,width,size,max_lines,bold=False):
    font="Helvetica-Bold" if bold else "Helvetica"
    words=text.split(); lines=[]; line=""
    for word in words:
        nxt=word if not line else line+" "+word
        if line and stringWidth(nxt,font,size)>width:
            lines.append(line); line=word
        else: line=nxt
    if line: lines.append(line)
    truncated=len(lines)>max_lines; lines=lines[:max_lines]
    if truncated:
        value=lines[-1]
        while value and stringWidth(value+"…",font,size)>width:value=value[:-1]
        lines[-1]=value+"…"
    return lines or [""]

def cell_text(pdf,text,x,y,w,h,size,max_lines=2,bold=False,color=black):
    font="Helvetica-Bold" if bold else "Helvetica"; pdf.setFont(font,size);pdf.setFillColor(color)
    lines=wrap(text,w-6,size,max_lines,bold);leading=size*1.1
    baseline=y+h/2+(len(lines)-1)*leading/2-size*.32
    for i,line in enumerate(lines):pdf.drawCentredString(x+w/2,baseline-i*leading,line)

pdf=canvas.Canvas(str(OUT),pagesize=(792,612));pdf.setFillColor(white);pdf.rect(0,0,792,612,fill=1,stroke=0)
y=612
def box(x,top,w,h,fill):
    pdf.setFillColor(fill);pdf.setStrokeColor(black);pdf.setLineWidth(.6);pdf.rect(x,top-h,w,h,fill=1,stroke=1)

box(0,y,792,30,HexColor("#00B050"));cell_text(pdf,"PROGRAMA DE ACTIVIDADES PARA LA REUNIÓN VIDA Y MINISTERIO CRISTIANOS",0,y-30,792,30,12,1,True,white);y-=30
section=18;rows=sum(len(items) for _,items in weeks);row_h=min(24,(580-len(weeks)*section*2)/rows)
xs=[0,38,136,342,548,792]
for title,items in weeks:
    box(0,y,792,section,HexColor("#0D6EFD"));cell_text(pdf,title,0,y-section,792,section,9,1,True,white);y-=section
    fills=["#BFBFBF","#ED7D31","#FFC000","#955FEF"]
    box(xs[0],y,xs[2]-xs[0],section,HexColor(fills[0]));box(xs[2],y,xs[3]-xs[2],section,HexColor(fills[1]));box(xs[3],y,xs[4]-xs[3],section,HexColor(fills[2]));box(xs[4],y,xs[5]-xs[4],section,HexColor(fills[3]))
    cell_text(pdf,"ASIGNACIÓN",xs[0],y-section,xs[2]-xs[0],section,8,1,True);cell_text(pdf,"SALA PRINCIPAL",xs[2],y-section,xs[3]-xs[2],section,8,1,True);cell_text(pdf,"SALA AUXILIAR",xs[3],y-section,xs[4]-xs[3],section,8,1,True);cell_text(pdf,"FUENTE DE INFORMACIÓN",xs[4],y-section,xs[5]-xs[4],section,8,1,True,white);y-=section
    for minutes,name,main,aux,source in items:
        for a,b in zip(xs,xs[1:]):box(a,y,b-a,row_h,HexColor("#F8F8F8"))
        cell_text(pdf,f"({minutes} min.)",xs[0],y-row_h,xs[1]-xs[0],row_h,6,2)
        cell_text(pdf,name,xs[1],y-row_h,xs[2]-xs[1],row_h,6.3,2)
        cell_text(pdf,main,xs[2],y-row_h,xs[3]-xs[2],row_h,6.1,2,True)
        cell_text(pdf,aux,xs[3],y-row_h,xs[4]-xs[3],row_h,6.1,2,True)
        cell_text(pdf,source,xs[4],y-row_h,xs[5]-xs[4],row_h,5.5,3)
        y-=row_h
pdf.showPage();pdf.save();print(OUT)
