package mx.escuelaministerio.app;

import java.text.Normalizer;
import java.util.Locale;
import java.util.EnumMap;

final class Models {
    private Models() {}

    static final class Student {
        long id;
        String name = "";
        String phone = "";
        boolean baptized;
        String sex = "";
        boolean appointed;
        boolean pioneer;
        int rating;
        boolean vidaMinisterio;
        boolean discursoPublico;
        boolean atalaya;
        boolean lectorAtalaya;
        boolean acomodadorEntrada;
        boolean acomodadorAuditorio1;
        boolean acomodadorAuditorio2;
        boolean microfono1;
        boolean microfono2;
        boolean plataforma1;
        boolean plataforma2;
        boolean oracionInicial;
        boolean oracionFinal;
        boolean estudioLibro;
        boolean lectorEstudioLibro;
        int groupNumber;
        // True when imported workbook explicitly contains extended classification columns.
        boolean extendedClassificationsProvided;

        boolean isSuspended() { return rating == 0; }
        boolean isMale() {
            String normalized = sex == null ? "" : sex.trim().toLowerCase(Locale.ROOT);
            return normalized.equals("h") || normalized.startsWith("hombre")
                    || normalized.startsWith("mascul") || normalized.startsWith("varon") || normalized.startsWith("varón");
        }
        boolean isFemale() {
            String normalized = sex == null ? "" : sex.trim().toLowerCase(Locale.ROOT);
            return normalized.equals("f") || normalized.startsWith("fem") || normalized.contains("mujer");
        }
        @Override public String toString() {
            return name + (rating == 0 ? " · Suspendido" : " · Nivel " + rating);
        }
    }

    static final class President {
        long id;
        String name = "";
        String phone = "";
        @Override public String toString() { return name; }
    }

    static final class Presentation {
        long id;
        String date = "";
        int number;
        String material = "";
        int minutes;
        String assignmentName = "";
        String framework = "";
        String lesson = "";
        String source = "";
        String topic = "";

        boolean isBibleReading() {
            String name = normalize(assignmentName);
            String text = normalize(assignmentName + " " + framework);
            return text.contains("lectura") &&
                    (text.contains("biblia") || text.contains("biblic") || name.equals("lectura") || name.startsWith("lectura "));
        }

        boolean isWhatWouldYouSay() {
            String name = normalize(assignmentName);
            return name.equals("que diria") || name.startsWith("que diria ");
        }

        boolean isDiscourse() { return normalize(framework).contains("discurso"); }
        boolean isIndividualMaleAssignment() { return isBibleReading() || isWhatWouldYouSay() || isDiscourse(); }

        private String normalize(String value) {
            return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
                    .replaceAll("\\p{M}+", "")
                    .toLowerCase(Locale.ROOT)
                    .replaceAll("[^a-z0-9]+", " ")
                    .trim();
        }

        @Override public String toString() {
            return date + " · " + number + ". " + assignmentName + " (" + minutes + " min.)";
        }
    }

    static final class Meeting {
        String date = "";
        long presidentId;
        boolean suspended;
        boolean visit;
        String circuitOverseer = "";
        String serviceTalkTopic = "";
        String serviceSong = "";
    }

    enum MechanicalRole {
        ENTRADA("acomodador_entrada", "Acomodador de la entrada"),
        AUDITORIO_1("acomodador_auditorio_1", "Acomodador de auditorio 1"),
        AUDITORIO_2("acomodador_auditorio_2", "Acomodador de auditorio 2"),
        MICROFONO_1("microfono_1", "Micrófono 1"),
        MICROFONO_2("microfono_2", "Micrófono 2"),
        ORACION_INICIAL("oracion_inicial", "Oración inicial"),
        ORACION_FINAL("oracion_final", "Oración final"),
        PLATAFORMA_1("plataforma_1", "Plataforma 1"),
        PLATAFORMA_2("plataforma_2", "Plataforma 2");

        final String column;
        final String label;
        MechanicalRole(String column, String label) { this.column=column; this.label=label; }
        boolean authorized(Student s) {
            switch (this) {
                case ENTRADA: return s.acomodadorEntrada;
                case AUDITORIO_1: return s.acomodadorAuditorio1;
                case AUDITORIO_2: return s.acomodadorAuditorio2;
                case MICROFONO_1: return s.microfono1;
                case MICROFONO_2: return s.microfono2;
                case ORACION_INICIAL: return s.oracionInicial;
                case ORACION_FINAL: return s.oracionFinal;
                case PLATAFORMA_1: return s.plataforma1;
                case PLATAFORMA_2: return s.plataforma2;
                default: return false;
            }
        }
    }

    static final class MechanicalProgram {
        String date = "";
        String meetingType = "MIDWEEK";
        final EnumMap<MechanicalRole,Long> assigned = new EnumMap<>(MechanicalRole.class);
        String openingSong = "";
        String middleSong = "";
        String closingSong = "";

        long studentId(MechanicalRole role) { return assigned.getOrDefault(role,0L); }
    }

    static final class Song {
        final int number;
        final String title;
        Song(int number,String title){this.number=number;this.title=title;}
        @Override public String toString(){return number>0?number+". "+title:number<0?title+" (registro anterior)":title;}
    }

    static final class VisitingSpeaker {
        long id;
        String name = "";
        String congregation = "";
        String locality = "";
        String appointment = "";
        @Override public String toString(){return name+(congregation.isEmpty()?"":" · "+congregation);}
    }

    static final class PublicMeeting {
        String date = "";
        long presidentId;
        boolean suspended;
        boolean visit;
        String speakerKind = "LOCAL";
        long localSpeakerId;
        long visitorSpeakerId;
        String outlineNumber = "";
        String outlineTitle = "";
        long conductorId;
        String watchtowerTopic = "";
        long readerId;
        String finalTalkTitle = "";
        boolean evaluated;
        boolean speakerPresented = true;
        String evaluationComments = "";
    }

    static final class Movement {
        long id;
        long studentId;
        long presentationId;
        String room = "PRINCIPAL";
        String role = "MAESTRO";
        long helperId;
        boolean emergency;
        int actualMinutes;
        String lesson = "";
        boolean visit;
        boolean evaluated;
        int actualSeconds;
        boolean studentPresented = true;
        String studentAbsenceReason = "";
        long replacementStudentId;
        boolean helperPresented = true;
        String helperAbsenceReason = "";
        boolean materialAdhered;
        boolean lessonFulfilled;
        String lessonComments = "";
        String evaluationComments = "";
    }

    static final class ProgramRow {
        Movement movement;
        Presentation presentation;
        Student student;
        Student helper;
        Meeting meeting;
        President president;
    }

    static final class LifePart {
        long id;
        String date = "";
        int number;
        String assignmentName = "";
        int minutes;
        String topic = "";

        boolean isBookStudy() {
            String value = Normalizer.normalize(assignmentName == null ? "" : assignmentName, Normalizer.Form.NFD)
                    .replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT)
                    .replaceAll("[^a-z0-9]+", " ").trim();
            return value.contains("estudio") && value.contains("libro");
        }

        @Override public String toString() {
            return date + " · " + number + ". " + assignmentName + " (" + minutes + " min.)";
        }
    }

    static final class LifeAssignment {
        long id;
        long partId;
        long studentId;
        long readerId;
        boolean evaluated;
        int actualSeconds;
        boolean studentPresented = true;
        String studentAbsenceReason = "";
        long replacementStudentId;
        boolean readerPresented = true;
        String readerAbsenceReason = "";
        boolean topicAdhered;
        String evaluationComments = "";
    }

    static final class LifeProgramRow {
        LifePart part;
        LifeAssignment assignment;
        Student student;
        Student reader;
        Meeting meeting;
        President president;
    }
}
