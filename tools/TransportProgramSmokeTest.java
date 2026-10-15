package mx.escuelaministerio.app;

/** Host-only validation; does not open an Android database. */
public final class TransportProgramSmokeTest {
    public static void main(String[] args){
        TransportProgram.Trip trip=new TransportProgram.Trip();trip.date="2026-10-04";trip.time="06:30";trip.reason="Asamblea";trip.departurePlace="Salón del Reino";TransportProgram.validateTrip(trip);
        trip.departurePlace="";reject(trip,"lugar");trip.departurePlace="Salón del Reino";trip.time="25:00";reject(trip,"hora");trip.time="06:30";
        if(!"04/10/2026".equals(TransportProgram.humanDate(trip.date)))throw new AssertionError("Fecha del reporte incorrecta.");
        System.out.println("Datos obligatorios y fecha del Programa de Transporte: correctos.");
    }
    private static void reject(TransportProgram.Trip trip,String field){try{TransportProgram.validateTrip(trip);throw new AssertionError("Se aceptó "+field+" inválido.");}catch(IllegalArgumentException expected){/* correct */}}
}
