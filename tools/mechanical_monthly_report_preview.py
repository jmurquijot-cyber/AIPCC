from pathlib import Path

from reportlab.lib.colors import Color, HexColor, black, white
from reportlab.lib.pagesizes import landscape, letter
from reportlab.pdfbase.pdfmetrics import stringWidth
from reportlab.pdfgen.canvas import Canvas


OUT = Path(__file__).resolve().parents[1] / "output" / "pdf" / "muestra_asignaciones_mecanicas_octubre_2026.pdf"


def box(c, x, y, w, h, fill):
    c.setFillColor(fill)
    c.setStrokeColor(black)
    c.setLineWidth(0.8)
    c.rect(x, y, w, h, fill=1, stroke=1)


def fitted(c, text, x, y, w, h, size=8, color=black, bold=True, max_lines=2):
    text = (text or "").upper().strip()
    font = "Helvetica-Bold" if bold else "Helvetica"
    words = text.replace("\n", " \n ").split()
    lines, current = [], ""
    for word in words:
        if word == "\\n":
            lines.append(current)
            current = ""
            continue
        candidate = word if not current else current + " " + word
        if current and stringWidth(candidate, font, size) > w - 6:
            lines.append(current)
            current = word
        else:
            current = candidate
    if current:
        lines.append(current)
    if not lines:
        lines = [""]
    if len(lines) > max_lines:
        lines = lines[:max_lines]
        lines[-1] += "..."
    c.setFont(font, size)
    c.setFillColor(color)
    line_h = size * 1.18
    baseline = y + (h + len(lines) * line_h) / 2 - size
    for i, line in enumerate(lines):
        while len(line) > 2 and stringWidth(line, font, size) > w - 5:
            line = line[:-1]
        c.drawCentredString(x + w / 2, baseline - i * line_h, line)


def main():
    OUT.parent.mkdir(parents=True, exist_ok=True)
    width, height = landscape(letter)
    c = Canvas(str(OUT), pagesize=(width, height))
    left, right = 10, width - 10
    y = height - 10

    box(c, left, y - 36, right - left, 36, HexColor("#082B5E"))
    fitted(c, "ROL DE ASIGNACIONES VARONES OCTUBRE 2026", left, y - 36, right-left, 36, 16, white, True, 1)
    y -= 36
    box(c, left, y - 34, right-left, 34, HexColor("#595959"))
    fitted(c, "CONGREGACIÓN VILLA RICA, BOCA DEL RÍO. CIRCUITO 25", left, y - 34, right-left, 34, 15, white, True, 1)
    y -= 40

    columns = [
        ("DOMINGO\n4", "WEEKEND", True),
        ("MIÉRCOLES\n7", "MIDWEEK", False),
        ("DOMINGO\n11", "WEEKEND", False),
        ("MIÉRCOLES\n14", "MIDWEEK", False),
        ("DOMINGO\n18", "WEEKEND", False),
        ("MIÉRCOLES\n21", "MIDWEEK", False),
        ("DOMINGO\n25", "WEEKEND", False),
        ("MIÉRCOLES\n28", "MIDWEEK", False),
    ]
    labels = ["ORACIÓN INICIAL", "PRESIDENTE", "LECTOR", "ORACIÓN FINAL", "ACOMODADOR DE ENTRADA", "ACOMODADORES DE AUDITORIO", "MICRÓFONOS", "LIMPIEZA"]
    heights = [48, 48, 48, 48, 48, 70, 70, 48]
    sample = [
        ["", "ADÁN MUÑIZ", "ANTONIO AGUILERA", "EDILBERTO TABAL", "EZEQUIEL VENTURA", "FRANCISCO GÓMEZ", "MANUEL ROMERO", "MOISÉS OCAMPO"],
        ["", "MANUEL URQUIJO", "MOISÉS OCAMPO MUÑIZ", "SECUNDINO DAMIÁN", "RODOLFO MARTÍNEZ", "JOSÍAS SAID URQUIJO", "SILVIO ORTIZ", "PABLO RAMOS"],
        ["", "CUAUHTÉMOC MISARTE", "MANUEL URQUIJO", "ESDRAS VALENTÍN", "MISAEL VITAL", "MANUEL URQUIJO", "JOSÍAS SAID URQUIJO", "SECUNDINO DAMIÁN"],
        ["", "ANTONIO AGUILERA", "ADÁN MUÑIZ", "EZEQUIEL VENTURA", "EDILBERTO TABAL", "MANUEL ROMERO", "FRANCISCO GÓMEZ", "PABLO RAMOS"],
        ["", "RODOLFO MARTÍNEZ", "SECUNDINO DAMIÁN", "SILVIO ORTIZ", "ESDRAS VALENTÍN", "EZEQUIEL VENTURA", "JEHOSAFAT VITAL", "MANUEL ROMERO"],
        ["", "CUAUHTÉMOC MISARTE\nMANUEL ROMERO", "MANUEL URQUIJO\nRODOLFO MARTÍNEZ", "EZEQUIEL VENTURA\nJEHOSAFAT VITAL", "SILVIO ORTIZ\nSECUNDINO DAMIÁN", "ADÁN MUÑIZ\nRODOLFO MARTÍNEZ", "EZEQUIEL VENTURA\nEDILBERTO TABAL", "MOISÉS OCAMPO\nMISAEL VITAL"],
        ["", "MOISÉS OCAMPO\nSECUNDINO DAMIÁN", "EDILBERTO TABAL\nMISAEL VITAL", "ESDRAS VALENTÍN\nJEHOSAFAT VITAL", "EZEQUIEL VENTURA\nJOSÍAS URQUIJO", "MOISÉS OCAMPO\nMISAEL VITAL", "EDILBERTO TABAL\nSAMUEL TEMICH", ""],
        ["", "GRUPO 2", "GRUPO 3", "GRUPO 4", "GRUPO 5", "GRUPO 1", "GRUPO 2", "GRUPO 3"],
    ]

    label_w = 130
    col_w = (right-left-label_w) / len(columns)
    date_h = 49
    box(c, left, y-date_h, label_w, date_h, HexColor("#F2F2F2"))
    fitted(c, "OCTUBRE\n2026", left, y-date_h, label_w, date_h, 10.5, black, True, 2)
    for i, (date, kind, _) in enumerate(columns):
        x = left + label_w + i * col_w
        fill = HexColor("#0070C0") if kind == "MIDWEEK" else HexColor("#F2F2F2")
        color = white if kind == "MIDWEEK" else black
        box(c, x, y-date_h, col_w, date_h, fill)
        fitted(c, date, x, y-date_h, col_w, date_h, 8.5, color, True, 2)
    y -= date_h
    body_h = sum(heights)
    suspended_drawn = False
    for row, row_h in enumerate(heights):
        box(c, left, y-row_h, label_w, row_h, HexColor("#2F3946"))
        fitted(c, labels[row], left, y-row_h, label_w, row_h, 8, white, True, 2)
        for i, (_, kind, suspended) in enumerate(columns):
            x = left + label_w + i * col_w
            fill = HexColor("#BDD7EE") if kind == "MIDWEEK" else HexColor("#F2F2F2")
            if suspended:
                if not suspended_drawn:
                    box(c, x, y-body_h, col_w, body_h, fill)
                    fitted(c, "ASAMBLEA / REUNIÓN SUSPENDIDA", x, y-body_h, col_w, body_h, 7.2, black, True, 4)
                    suspended_drawn = True
                continue
            box(c, x, y-row_h, col_w, row_h, fill)
            fitted(c, sample[row][i], x, y-row_h, col_w, row_h, 6.8, black, True, 3 if row_h > 50 else 2)
        y -= row_h
    c.showPage()
    c.save()
    print(OUT)


if __name__ == "__main__":
    main()
