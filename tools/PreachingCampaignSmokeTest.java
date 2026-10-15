package mx.escuelaministerio.app;

/** Host-only checks; does not touch Android or a user database. */
public final class PreachingCampaignSmokeTest {
    private static void reject(PreachingCampaign.Campaign c){try{PreachingCampaign.validate(c);throw new AssertionError("Se aceptó una campaña inválida.");}catch(IllegalArgumentException expected){/* correct */}}
    public static void main(String[] args){
        PreachingCampaign.Campaign c=new PreachingCampaign.Campaign();c.startDate="2026-09-15";c.endDate="2026-09-20";c.startMinute=480;c.endMinute=1080;c.exhibitors=1;
        PreachingCampaign.validate(c);if(c.slots()!=5||!"08:00 – 10:00".equals(c.slotLabel(0))||!"16:00 – 18:00".equals(c.slotLabel(4)))throw new AssertionError("Turnos de dos horas incorrectos.");
        c.exhibitors=6;PreachingCampaign.validate(c);c.exhibitors=7;reject(c);c.exhibitors=1;
        c.endMinute=1020;reject(c);c.endMinute=1080;
        c.endDate="2026-09-14";reject(c);c.endDate="2026-09-20";
        PreachingCampaign.validateDay(c,"2026-09-20");
        try{PreachingCampaign.validateDay(c,"2026-09-21");throw new AssertionError("Se aceptó un día posterior al final.");}catch(IllegalArgumentException expected){/* correct */}
        System.out.println("Campaña y turnos de dos horas: correctos.");
    }
}
