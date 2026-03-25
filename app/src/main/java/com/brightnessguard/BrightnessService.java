package com.brightnessguard;
import android.app.*;
import android.content.*;
import android.hardware.*;
import android.os.*;
import android.provider.Settings;
import androidx.core.app.NotificationCompat;
public class BrightnessService extends Service implements SensorEventListener {
    public static final String ACTION_UPDATE = "com.brightnessguard.UPDATE";
    public static boolean isRunning = false;
    private static final String CHANNEL_ID = "BrightnessGuardChannel";
    private static final String PREFS = "BrightnessGuardPrefs";
    private static final String KEY_MIN = "min_brightness";
    private static final String KEY_THRESHOLD = "threshold_lux";
    private static final int DEFAULT_MIN = 13;
    private static final int DEFAULT_THRESHOLD = 50;
private SensorManager sensorManager;
    private Sensor lightSensor;
@Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
    }
@Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        isRunning = true;
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("BrightnessGuard activ")
            .setContentText("Monitorizez lumina ambientala")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .build();
        startForeground(1, notification);
        if (lightSensor != null)
            sensorManager.registerListener(this, lightSensor, SensorManager.SENSOR_DELAY_NORMAL);
        return START_STICKY;
    }
@Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_LIGHT) return;
        float lux = event.values[0];
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        int minBrightness = prefs.getInt(KEY_MIN, DEFAULT_MIN);
        int threshold = prefs.getInt(KEY_THRESHOLD, DEFAULT_THRESHOLD);
if (!Settings.System.canWrite(this)) return;
if (lux < threshold) {
            Settings.System.putInt(getContentResolver(),
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
            Settings.System.putInt(getContentResolver(),
                Settings.System.SCREEN_BRIGHTNESS, minBrightness);
        } else {
            Settings.System.putInt(getContentResolver(),
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC);
        }
}
@Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}
 @Override
    public void onDestroy() {
        isRunning = false;
        sensorManager.unregisterListener(this);
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
