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
