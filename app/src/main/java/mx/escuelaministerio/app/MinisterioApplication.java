package mx.escuelaministerio.app;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;

public final class MinisterioApplication extends Application {
    static final String CHANNEL_REMINDERS = "asignaciones";

    @Override public void onCreate() {
        super.onCreate();
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_REMINDERS,
                "Recordatorios de asignaciones",
                NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("Avisos siete días antes de cada asignación");
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }
}
