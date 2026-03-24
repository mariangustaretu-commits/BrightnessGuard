package com.brightnessguard;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import androidx.core.app.NotificationCompat;
public class BrightnessService extends Service {
    public static final String ACTION_UPDATE = "com.brightnessguard.UPDATE";
    public static boolean isRunning = false;
    private static final String CHANNEL_ID = "BrightnessGuardChannel";
@Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }
@Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        isRunning = true;
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("BrightnessGuard")
            .setContentText("Running")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .build();
        startForeground(1, notification);
        return START_STICKY;
    }
@Override
    public void onDestroy() {
        isRunning = false;
        super.onDestroy();
    }
@Override
    public IBinder onBind(Intent intent) { return null; }
private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
            CHANNEL_ID, "BrightnessGuard", NotificationManager.IMPORTANCE_LOW);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }
}
