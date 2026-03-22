package com.brightnessguard;

import android.content.*;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {
    public static final String PREFS_NAME = "BGPrefs";
    public static final String KEY_MIN = "min_brightness";
    public static final String KEY_THRESHOLD = "light_threshold";
    public static final int DEFAULT_MIN = 30;
    public static final int DEFAULT_THRESHOLD = 500;

    private SharedPreferences prefs;
    private Switch switchService;
    private SeekBar seekMin, seekThreshold;
    private TextView tvMin, tvThreshold, tvStatus;
    private Button btnPerm;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        switchService = findViewById(R.id.switchService);
        seekMin = findViewById(R.id.seekBarMin);
        seekThreshold = findViewById(R.id.seekBarThreshold);
        tvMin = findViewById(R.id.tvMin);
        tvThreshold = findViewById(R.id.tvThreshold);
        tvStatus = findViewById(R.id.tvStatus);
        btnPerm = findViewById(R.id.btnPermission);

        int savedMin = prefs.getInt(KEY_MIN, DEFAULT_MIN);
        int savedThr = prefs.getInt(KEY_THRESHOLD, DEFAULT_THRESHOLD);

        seekMin.setMax(255);
        seekMin.setProgress(savedMin);
        tvMin.setText("Luminozitate minima: " + toPercent(savedMin) + "%");
        seekMin.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar sb, int p, boolean f) {
                int v = Math.max(p, 5);
                tvMin.setText("Luminozitate minima: " + toPercent(v) + "%");
                prefs.edit().putInt(KEY_MIN, v).apply();
                notifyService();
            }
            public void onStartTrackingTouch(SeekBar sb) {}
            public void onStopTrackingTouch(SeekBar sb) {}
        });

        seekThreshold.setMax(199);
        seekThreshold.setProgress(Math.max(0, (savedThr - 10) / 10));
        tvThreshold.setText("Prag lumina: " + savedThr + " lux");
        seekThreshold.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar sb, int p, boolean f) {
                int lux = p * 10 + 10;
                tvThreshold.setText("Prag lumina: " + lux + " lux");
                prefs.edit().putInt(KEY_THRESHOLD, lux).apply();
                notifyService();
            }
            public void onStartTrackingTouch(SeekBar sb) {}
            public void onStopTrackingTouch(SeekBar sb) {}
        });

        boolean enabled = prefs.getBoolean("service_enabled", false);
        switchService.setChecked(enabled);
        updateStatus(enabled);
        switchService.setOnCheckedChangeListener((btn, checked) -> {
            if (checked && !Settings.System.canWrite(this)) {
                requestWriteSettings();
                switchService.setChecked(false);
                return;
            }
            prefs.edit().putBoolean("service_enabled", checked).apply();
            if (checked) startService();
            else stopService(new Intent(this, BrightnessService.class));
            updateStatus(checked);
        });

        btnPerm.setOnClickListener(v -> requestWriteSettings());
    }

    @Override
    protected void onResume() {
        super.onResume();
        updatePermBtn();
    }

    private void requestWriteSettings() {
        startActivity(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,
            Uri.parse("package:" + getPackageName())));
    }

    private void startService() {
        ContextCompat.startForegroundService(this, new Intent(this, BrightnessService.class));
    }

    private void notifyService() {
        if (BrightnessService.isRunning) {
            Intent i = new Intent(this, BrightnessService.class);
            i.setAction(BrightnessService.ACTION_UPDATE);
            startService(i);
        }
    }

    private void updateStatus(boolean on) {
        tvStatus.setText(on ? "● Serviciu activ" : "○ Serviciu oprit");
        tvStatus.setTextColor(on ? 0xFF2E7D32 : 0xFF888888);
    }

    private void updatePermBtn() {
        if (Settings.System.canWrite(this)) {
            btnPerm.setText("✓ Permisiune acordata");
            btnPerm.setEnabled(false);
        } else {
            btnPerm.setText("Acorda permisiune WRITE_SETTINGS");
            btnPerm.setEnabled(true);
        }
    }

    private int toPercent(int v) { return (int)((v / 255f) * 100); }
}
EOFcat > ~/BrightnessGuard/app/src/main/java/com/brightnessguard/BrightnessService.java << 'EOF'
package com.brightnessguard;

import android.app.*;
import android.content.*;
import android.hardware.*;
import android.os.*;
import android.provider.Settings;
import androidx.core.app.NotificationCompat;

public class BrightnessService extends Service implements SensorEventListener {
    public static boolean isRunning = false;
    public static final String ACTION_UPDATE = "com.brightnessguard.UPDATE";
    private static final String CHANNEL_ID = "BGChannel";
    private static final int NOTIF_ID = 1001;
    private static final int SMOOTH = 5;

    private SensorManager sensorManager;
    private Sensor lightSensor;
    private SharedPreferences prefs;
    private int minBrightness, lightThreshold, lastSet = -1;
    private java.util.ArrayDeque<Float> readings = new java.util.ArrayDeque<>();

    @Override
    public void onCreate() {
        super.onCreate();
        isRunning = true;
        prefs = getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE);
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        loadPrefs();
        createChannel();
        startForeground(NOTIF_ID, buildNotif("Monitorizare activa..."));
        if (lightSensor != null)
            sensorManager.registerListener(this, lightSensor, SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_UPDATE.equals(intent.getAction())) {
            loadPrefs();
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isRunning = false;
        sensorManager.unregisterListener(this);
        restoreAuto();
    }

    @Override public IBinder onBind(Intent i) { return null; }

    @Override
    public void onSensorChanged(SensorEvent e) {
        if (e.sensor.getType() != Sensor.TYPE_LIGHT) return;
        if (readings.size() >= SMOOTH) readings.pollFirst();
        readings.addLast(e.values[0]);
        float avg = 0;
        for (float f : readings) avg += f;
        avg /= readings.size();
        int target = computeBrightness(avg);
        if (target != lastSet) {
            applyBrightness(target);
            lastSet = target;
            String info = avg >= lightThreshold ? "Luminos (" + (int)avg + " lux) -> max"
                                                : "Intunecat (" + (int)avg + " lux) -> min";
            updateNotif(info);
        }
    }

    @Override public void onAccuracyChanged(Sensor s, int a) {}

    private int computeBrightness(float lux) {
        if (lux >= lightThreshold) return 255;
        if (lux >= lightThreshold / 2f) {
            float ratio = (lux - lightThreshold / 2f) / (lightThreshold / 2f);
            return Math.min(255, Math.max(minBrightness, (int)(minBrightness + ratio * (255 - minBrightness))));
        }
        return minBrightness;
    }

    private void applyBrightness(int value) {
        if (!Settings.System.canWrite(this)) return;
        try {
            Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
            Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS,
                Math.max(1, Math.min(255, value)));
        } catch (Exception ex) { ex.printStackTrace(); }
    }

    private void restoreAuto() {
        try {
            if (Settings.System.canWrite(this))
                Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC);
        } catch (Exception ex) {}
    }

    private void loadPrefs() {
        minBrightness = Math.max(1, prefs.getInt(MainActivity.KEY_MIN, MainActivity.DEFAULT_MIN));
        lightThreshold = prefs.getInt(MainActivity.KEY_THRESHOLD, MainActivity.DEFAULT_THRESHOLD);
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "BrightnessGuard",
                NotificationManager.IMPORTANCE_LOW);
            ch.setShowBadge(false);
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
    }

    private Notification buildNotif(String text) {
        PendingIntent pi = PendingIntent.getActivity(this, 0,
            new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("BrightnessGuard")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentIntent(pi).setOngoing(true).setSilent(true).build();
    }

    private void updateNotif(String text) {
        getSystemService(NotificationManager.class).notify(NOTIF_ID, buildNotif(text));
    }
}
