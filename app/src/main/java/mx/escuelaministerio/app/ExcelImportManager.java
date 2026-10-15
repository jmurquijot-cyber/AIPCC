package mx.escuelaministerio.app;

import android.database.sqlite.SQLiteDatabase;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

final class ExcelImportManager {
    private static final long MAX_UNCOMPRESSED_BYTES = 24L * 1024L * 1024L;

    private ExcelImportManager() {}

    static final class Result {
        int studentsAdded, studentsUpdated, studentsDuplicatesAvoided, studentsReview;
        int presidentsAdded, presidentsUpdated, presidentsDuplicatesAvoided, presidentsReview;
        int presentationsAdded, presentationsUpdated;
        String lastPresentationMonth;
        final List<String> errors = new ArrayList<>();
        final List<String> warnings = new ArrayList<>();

        int imported() {
            return studentsAdded + studentsUpdated + presidentsAdded + presidentsUpdated
                    + presentationsAdded + presentationsUpdated;
        }

        String summary() {
            StringBuilder text = new StringBuilder("Importación terminada.\n\n")
                    .append("Alumnos: ").append(studentsAdded).append(" nuevos, ").append(studentsUpdated).append(" actualizados, ").append(studentsDuplicatesAvoided).append(" duplicados evitados, ").append(studentsReview).append(" por revisar.\n")
                    .append("Presidentes: ").append(presidentsAdded).append(" nuevos, ").append(presidentsUpdated).append(" actualizados, ").append(presidentsDuplicatesAvoided).append(" duplicados evitados, ").append(presidentsReview).append(" por revisar.\n")
                    .append("Presentaciones: ").append(presentationsAdded).append(" nuevas, ").append(presentationsUpdated).append(" actualizadas.");
            if (!errors.isEmpty()) {
                text.append("\n\nFilas omitidas: ").append(errors.size());
                int limit = Math.min(8, errors.size());
                for (int i = 0; i < limit; i++) text.append("\n• ").append(errors.get(i));
                if (errors.size() > limit) text.append("\n• … y ").append(errors.size() - limit).append(" más.");
            }
            if (!warnings.isEmpty()) {
                text.append("\n\nObservaciones:");
                int limit = Math.min(8,warnings.size());
                for(int i=0;i<limit;i++)text.append("\n• ").append(warnings.get(i));
                if(warnings.size()>limit)text.append("\n• … y ").append(warnings.size()-limit).append(" más.");
            }
            return text.toString();
        }
    }

    static Result importWorkbook(InputStream source, MinisterioDb db) throws Exception {
        Map<String, byte[]> entries = readWorkbookEntries(source);
        List<String> sharedStrings = parseSharedStrings(entries.get("xl/sharedStrings.xml"));
        List<SheetData> sheets = new ArrayList<>();
        for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
            if (entry.getKey().startsWith("xl/worksheets/") && entry.getKey().endsWith(".xml"))
                sheets.add(parseWorksheet(entry.getKey(), entry.getValue(), sharedStrings));
        }
        if (sheets.isEmpty()) throw new IllegalArgumentException("El archivo no contiene hojas de cálculo válidas.");

        Result result = new Result(); boolean recognized = false;
        SQLiteDatabase sql = db.getWritableDatabase(); sql.beginTransaction();
        try {
            for (SheetData sheet : sheets) {
                Map<String, Integer> headers = sheet.headers();
                if (isStudentSheet(headers)) { recognized = true; importStudents(sheet, headers, db, result); }
                else if (isPresidentSheet(headers)) { recognized = true; importPresidents(sheet, headers, db, result); }
                else if (isPresentationSheet(headers)) { recognized = true; importPresentations(sheet, headers, db, result); }
            }
            if (!recognized) throw new IllegalArgumentException("No se reconocieron las hojas Alumnos, Presidentes o Presentaciones. Use la plantilla de la aplicación.");
            sql.setTransactionSuccessful();
        } finally { sql.endTransaction(); }
        return result;
    }

    private static void importStudents(SheetData sheet, Map<String, Integer> h, MinisterioDb db, Result out) {
        for (int i = 1; i < sheet.rows.size(); i++) {
            RowData row = sheet.rows.get(i); if (row.blank()) continue;
            try {
                Models.Student s = new Models.Student();
                s.id = optionalId(cell(row, h, "id alumno", "id"));
                s.name = required(cell(row, h, "nombre"), "Nombre");
                s.phone = cell(row, h, "celular", "telefono", "teléfono");
                s.baptized = bool(cell(row, h, "bautizado"));
                s.sex = sex(required(cell(row, h, "sexo"), "Sexo"));
                s.appointed = bool(cell(row, h, "nombrado"));
                s.pioneer = bool(cell(row, h, "precursor"));
                s.rating = integer(required(cell(row, h, "calificador"), "Calificador"), "Calificador");
                if (s.rating < 0 || s.rating > 10) throw new IllegalArgumentException("Calificador debe estar entre 0 y 10");
                int status=db.upsertImportedStudent(s);if(status==MinisterioDb.IMPORT_ADDED)out.studentsAdded++;else if(status==MinisterioDb.IMPORT_UPDATED)out.studentsUpdated++;else if(status==MinisterioDb.IMPORT_MATCHED)out.studentsDuplicatesAvoided++;else{out.studentsReview++;out.warnings.add("Alumnos, fila "+row.number+": «"+s.name+"» coincide por nombre pero sus datos son distintos; no se importó para evitar duplicarlo.");}
            } catch (Exception e) { out.errors.add("Alumnos, fila " + row.number + ": " + message(e)); }
        }
    }

    private static void importPresidents(SheetData sheet, Map<String, Integer> h, MinisterioDb db, Result out) {
        for (int i = 1; i < sheet.rows.size(); i++) {
            RowData row = sheet.rows.get(i); if (row.blank()) continue;
            try {
                Models.President p = new Models.President();
                p.id = optionalId(cell(row, h, "id presidente", "id"));
                p.name = required(cell(row, h, "nombre"), "Nombre");
                p.phone = cell(row, h, "celular", "telefono", "teléfono");
                int status=db.upsertImportedPresident(p);if(status==MinisterioDb.IMPORT_ADDED)out.presidentsAdded++;else if(status==MinisterioDb.IMPORT_UPDATED)out.presidentsUpdated++;else if(status==MinisterioDb.IMPORT_MATCHED)out.presidentsDuplicatesAvoided++;else{out.presidentsReview++;out.warnings.add("Presidentes, fila "+row.number+": «"+p.name+"» coincide por nombre pero su celular es distinto; no se importó para evitar duplicarlo.");}
            } catch (Exception e) { out.errors.add("Presidentes, fila " + row.number + ": " + message(e)); }
        }
    }

    private static void importPresentations(SheetData sheet, Map<String, Integer> h, MinisterioDb db, Result out) {
        Map<String, Set<Integer>> numbersByDate = new LinkedHashMap<>();
        String previousDate = null; int inheritedDates = 0;
        for (int i = 1; i < sheet.rows.size(); i++) {
            RowData row = sheet.rows.get(i); if (row.blank()) continue;
            try {
                Models.Presentation p = new Models.Presentation();
                p.id = optionalId(cell(row, h, "id asignacion", "id presentación", "id presentacion", "id"));
                String dateValue = cell(row, h, "fecha");
                if (dateValue.isEmpty()) {
                    if (previousDate == null) throw new IllegalArgumentException("Fecha es obligatoria en la primera presentación");
                    p.date = previousDate; inheritedDates++;
                } else {
                    p.date = date(dateValue); previousDate = p.date;
                }
                p.number = integer(required(cell(row, h, "numero de asignacion", "numero", "intervencion"), "Número de asignación"), "Número de asignación");
                p.minutes = integer(required(cell(row, h, "tiempo minutos", "tiempo", "minutos"), "Tiempo"), "Tiempo");
                p.assignmentName = required(cell(row, h, "nombre de asignacion", "asignacion", "presentacion"), "Nombre de asignación");
                p.framework = cell(row, h, "marco"); p.lesson = cell(row, h, "leccion");
                p.source = cell(row, h, "fuente de informacion", "fuente");
                if (p.source.isEmpty()) p.source = cell(row, h, "material"); // Compatibilidad con la plantilla anterior.
                p.material = ""; p.topic = cell(row, h, "tema");
                if (p.number < 1) throw new IllegalArgumentException("Número de asignación debe ser mayor que cero");
                if (p.minutes < 1) throw new IllegalArgumentException("Tiempo debe ser mayor que cero");
                if (db.upsertImportedPresentation(p) == 1) out.presentationsAdded++; else out.presentationsUpdated++;
                numbersByDate.computeIfAbsent(p.date, key -> new HashSet<>()).add(p.number);
                out.lastPresentationMonth = p.date.substring(0, 7);
            } catch (Exception e) { out.errors.add("Presentaciones, fila " + row.number + ": " + message(e)); }
        }
        if (inheritedDates > 0) out.warnings.add(inheritedDates + " filas de Presentaciones usaron la fecha de la fila anterior porque la celda Fecha estaba vacía.");
        for (Map.Entry<String, Set<Integer>> entry : numbersByDate.entrySet()) {
            int count = entry.getValue().size();
            if (count < 4) out.warnings.add(entry.getKey() + ": solo se importaron " + count + " presentaciones; revise que esa semana tenga 4 o 5.");
        }
    }

    private static boolean isStudentSheet(Map<String, Integer> h) {
        return h.containsKey("nombre") && h.containsKey("sexo") && h.containsKey("calificador");
    }
    private static boolean isPresidentSheet(Map<String, Integer> h) {
        return h.containsKey("id presidente") && h.containsKey("nombre") && !h.containsKey("sexo");
    }
    private static boolean isPresentationSheet(Map<String, Integer> h) {
        return h.containsKey("fecha") && h.containsKey("numero de asignacion") && h.containsKey("nombre de asignacion");
    }

    private static Map<String, byte[]> readWorkbookEntries(InputStream source) throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>(); long total = 0;
        try (ZipInputStream zip = new ZipInputStream(source)) {
            ZipEntry entry; byte[] buffer = new byte[8192];
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                boolean wanted = "xl/sharedStrings.xml".equals(name) || (name.startsWith("xl/worksheets/") && name.endsWith(".xml"));
                if (!wanted || entry.isDirectory()) continue;
                ByteArrayOutputStream data = new ByteArrayOutputStream(); int read;
                while ((read = zip.read(buffer)) != -1) {
                    total += read; if (total > MAX_UNCOMPRESSED_BYTES) throw new IllegalArgumentException("El archivo Excel es demasiado grande.");
                    data.write(buffer, 0, read);
                }
                entries.put(name, data.toByteArray());
            }
        }
        if (entries.isEmpty()) throw new IllegalArgumentException("El archivo seleccionado no es un libro XLSX válido.");
        return entries;
    }

    private static List<String> parseSharedStrings(byte[] xml) throws Exception {
        List<String> values = new ArrayList<>(); if (xml == null) return values;
        XmlPullParser parser = Xml.newPullParser(); parser.setInput(new ByteArrayInputStream(xml), "UTF-8");
        StringBuilder current = null; int event;
        while ((event = parser.next()) != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && "si".equals(parser.getName())) current = new StringBuilder();
            else if (event == XmlPullParser.START_TAG && "t".equals(parser.getName()) && current != null) current.append(parser.nextText());
            else if (event == XmlPullParser.END_TAG && "si".equals(parser.getName()) && current != null) { values.add(current.toString()); current = null; }
        }
        return values;
    }

    private static SheetData parseWorksheet(String name, byte[] xml, List<String> shared) throws Exception {
        SheetData sheet = new SheetData(name); XmlPullParser parser = Xml.newPullParser();
        parser.setInput(new ByteArrayInputStream(xml), "UTF-8"); RowData row = null;
        String cellType = null, cellRef = null, raw = ""; boolean inCell = false; int nextColumn = 0, event;
        while ((event = parser.next()) != XmlPullParser.END_DOCUMENT) {
            String tag = parser.getName();
            if (event == XmlPullParser.START_TAG && "row".equals(tag)) {
                row = new RowData(parsePositive(parser.getAttributeValue(null, "r"), sheet.rows.size() + 1)); nextColumn = 0;
            } else if (event == XmlPullParser.START_TAG && "c".equals(tag) && row != null) {
                inCell = true; cellType = parser.getAttributeValue(null, "t"); cellRef = parser.getAttributeValue(null, "r"); raw = "";
            } else if (event == XmlPullParser.START_TAG && "v".equals(tag) && inCell) raw = parser.nextText();
            else if (event == XmlPullParser.START_TAG && "t".equals(tag) && inCell && "inlineStr".equals(cellType)) raw += parser.nextText();
            else if (event == XmlPullParser.END_TAG && "c".equals(tag) && row != null) {
                int column = cellRef == null ? nextColumn : columnIndex(cellRef); nextColumn = column + 1;
                row.cells.put(column, decode(raw, cellType, shared)); inCell = false;
            } else if (event == XmlPullParser.END_TAG && "row".equals(tag) && row != null) { sheet.rows.add(row); row = null; }
        }
        return sheet;
    }

    private static String decode(String raw, String type, List<String> shared) {
        if (raw == null) return "";
        if ("s".equals(type)) {
            try { int index = Integer.parseInt(raw.trim()); return index >= 0 && index < shared.size() ? shared.get(index) : ""; }
            catch (Exception ignored) { return ""; }
        }
        if ("b".equals(type)) return "1".equals(raw.trim()) ? "Sí" : "No";
        return raw.trim();
    }

    private static int columnIndex(String reference) {
        int result = 0; for (int i = 0; i < reference.length(); i++) {
            char c = Character.toUpperCase(reference.charAt(i)); if (c < 'A' || c > 'Z') break;
            result = result * 26 + (c - 'A' + 1);
        } return Math.max(0, result - 1);
    }

    private static String cell(RowData row, Map<String, Integer> headers, String... aliases) {
        for (String alias : aliases) {
            Integer column = headers.get(normalize(alias)); if (column != null) return row.cells.getOrDefault(column, "").trim();
        } return "";
    }

    private static String required(String value, String field) {
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException(field + " es obligatorio"); return value.trim();
    }
    private static long optionalId(String value) {
        if (value == null || value.trim().isEmpty()) return 0; long id = Math.round(Double.parseDouble(value.trim()));
        if (id < 1) throw new IllegalArgumentException("ID debe ser mayor que cero"); return id;
    }
    private static int integer(String value, String field) {
        try { return (int)Math.round(Double.parseDouble(value.trim())); }
        catch (Exception e) { throw new IllegalArgumentException(field + " debe ser numérico"); }
    }
    private static boolean bool(String value) {
        String v = normalize(value); return v.equals("si") || v.equals("yes") || v.equals("true") || v.equals("1") || v.equals("x");
    }
    private static String sex(String value) {
        String v = normalize(value);
        if (v.equals("h") || v.startsWith("hombre") || v.startsWith("mascul")) return "Hombre";
        if (v.equals("m") || v.equals("f") || v.startsWith("mujer") || v.startsWith("femen") || v.startsWith("female")) return "Mujer";
        throw new IllegalArgumentException("Sexo debe ser Hombre o Mujer");
    }
    private static String date(String value) {
        String text = value.trim();
        try {
            double serial = Double.parseDouble(text);
            if (serial > 0) return LocalDate.of(1899, 12, 30).plusDays((long)Math.floor(serial)).toString();
        } catch (NumberFormatException ignored) {}
        if (text.length() >= 10 && text.charAt(4) == '-' && text.charAt(7) == '-') text = text.substring(0, 10);
        try { return LocalDate.parse(text).toString(); } catch (DateTimeParseException ignored) {}
        for (DateTimeFormatter format : new DateTimeFormatter[]{DateTimeFormatter.ofPattern("d/M/uuuu"), DateTimeFormatter.ofPattern("d-M-uuuu")}) {
            try { return LocalDate.parse(text, format).toString(); } catch (DateTimeParseException ignored) {}
        }
        throw new IllegalArgumentException("Fecha no válida; use AAAA-MM-DD");
    }
    private static int parsePositive(String value, int fallback) {
        try { int parsed = Integer.parseInt(value); return parsed > 0 ? parsed : fallback; } catch (Exception ignored) { return fallback; }
    }
    private static String message(Exception error) {
        String value = error.getMessage(); return value == null || value.trim().isEmpty() ? "dato no válido" : value;
    }
    private static String normalize(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ").trim().replaceAll("\\s+", " ");
    }

    private static final class RowData {
        final int number; final Map<Integer, String> cells = new HashMap<>();
        RowData(int number) { this.number = number; }
        boolean blank() { for (String value : cells.values()) if (value != null && !value.trim().isEmpty()) return false; return true; }
    }
    private static final class SheetData {
        final String name; final List<RowData> rows = new ArrayList<>();
        SheetData(String name) { this.name = name; }
        Map<String, Integer> headers() {
            Map<String, Integer> result = new HashMap<>(); if (rows.isEmpty()) return result;
            for (Map.Entry<Integer, String> cell : rows.get(0).cells.entrySet()) result.put(normalize(cell.getValue()), cell.getKey());
            return result;
        }
    }
}
