package com.example.mobile_security_p1;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import android.Manifest;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;

public class LoginActivity extends AppCompatActivity {

    private EditText passwordInput;
    private Button loginButton;
    private TextView statusText;

    // Sensor variables
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private Sensor magnetometer;
    private float[] gravity;
    private float[] geomagnetic;
    private float azimuth = 0f;
    private TextView directionText,shakeStatusText;

    private final String PERMISSION = Manifest.permission.RECORD_AUDIO;
    private ActivityResultLauncher<String> requestPermissionLauncher;
    private ActivityResultLauncher<Intent> manuallyPermissionResultLauncher;
    private MediaRecorder recorder;
    private TextView decibelText;
    private Handler noiseHandler = new Handler();

    private long lastShakeTime = 0;
    private int shakeCount = 0;
    private boolean hasShakenEnough = false;
    private static final int SHAKE_THRESHOLD = 3; // Adjust this value as needed

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        findViews();

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

        requestPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        startRecording();
                        startNoiseMonitoring();
                    } else {
                        checkMicrophonePermission(); // זה כבר כולל דיאלוגים והפניה להגדרות
                    }
                }
        );

        manuallyPermissionResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> checkMicrophonePermission()
        );

        checkMicrophonePermission();

        loginButton.setOnClickListener(v -> {
            String password = passwordInput.getText().toString();
            boolean allConditionsMet =
                    isBatteryLevelCorrect(password)
                    && isFacingEast()
                    && isEnvironmentQuiet()
                    && isDeviceCharging()
                    && hasUserShakenPhone()
                    && isMusicPlaying();
            if (allConditionsMet) {
                Intent intent = new Intent(LoginActivity.this, SuccessActivity.class);
                startActivity(intent);
            } else {
                statusText.setText("התנאים לא התקיימו: סוללה / כיוון / רעש / טעינה / שקשוק / מוזיקה");
            }
        });
    }

    private void findViews() {
        passwordInput = findViewById(R.id.passwordInput);
        loginButton = findViewById(R.id.loginButton);
        statusText = findViewById(R.id.statusText);
        directionText = findViewById(R.id.directionText);
        shakeStatusText = findViewById(R.id.shakeStatusText);
        decibelText = findViewById(R.id.decibelText);
    }

    private boolean isBatteryLevelCorrect(String input) {
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = registerReceiver(null, ifilter);
        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        return input.equals(String.valueOf(level));
    }

    @Override
    protected void onResume() {
        super.onResume();
        sensorManager.registerListener(sensorEventListener, accelerometer, SensorManager.SENSOR_DELAY_UI);
        sensorManager.registerListener(sensorEventListener, magnetometer, SensorManager.SENSOR_DELAY_UI);
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(sensorEventListener);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        File tempFile = new File(getCacheDir(), "temp.3gp");
        if (tempFile.exists()) {
            tempFile.delete();
        }

        noiseHandler.removeCallbacksAndMessages(null);
        if (recorder != null) {
            recorder.stop();
            recorder.release();
            recorder = null;
        }
    }


    private final SensorEventListener sensorEventListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                gravity = event.values;

                float x = event.values[0];
                float y = event.values[1];
                float z = event.values[2];

                double acceleration = Math.sqrt(x * x + y * y + z * z) - SensorManager.GRAVITY_EARTH;

                if (acceleration > 5) { // ערך סף לשקשוק
                    long now = System.currentTimeMillis();
                    if (lastShakeTime == 0 || (now - lastShakeTime) < 1000) {
                        shakeCount++;
                        lastShakeTime = now;

                        if (shakeCount >= SHAKE_THRESHOLD) { // נניח צריך 4 שקשוקים
                            hasShakenEnough = true;
                        }
                    } else {
                        // אם עבר יותר מדי זמן בין שקשוקים, מאפסים
                        shakeCount = 1;
                        lastShakeTime = now;
                    }
                }
            }
            shakeStatusText.setText("שקשוקים : " + shakeCount);
            if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD)
                geomagnetic = event.values;
            if (gravity != null && geomagnetic != null) {
                float[] R = new float[9];
                float[] I = new float[9];
                boolean success = SensorManager.getRotationMatrix(R, I, gravity, geomagnetic);
                if (success) {
                    float[] orientation = new float[3];
                    SensorManager.getOrientation(R, orientation);
                    azimuth = (float) Math.toDegrees(orientation[0]);
                    String directionString = "כיוון: " + Math.round(azimuth) + "°";
                    directionText.setText(directionString);
                    if (azimuth < 0) {
                        azimuth += 360;
                    }
                }
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) { }
    };

    private boolean isFacingEast() {
        return azimuth >= 80 && azimuth <= 100;
    }

    private boolean hasUserShakenPhone() {
        return hasShakenEnough;
    }

    private void startRecording() {
        try {
            recorder = new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            recorder.setOutputFile(getCacheDir().getAbsolutePath() + "/temp.3gp"); // קובץ אמיתי

            recorder.prepare();
            recorder.start();
        } catch (Exception e) {
            e.printStackTrace();
            decibelText.setText("הקלטה נכשלה: " + e.getMessage());
        }
    }

    private boolean isEnvironmentQuiet() {
        if (recorder != null) {
            int amplitude = recorder.getMaxAmplitude();  // 0–32767
            double db = 20 * Math.log10((double) amplitude);
            return db < 60;  // תוכל לשחק עם הסף
        }
        return false;
    }

    private void startNoiseMonitoring() {
        Runnable updateTask = new Runnable() {
            @Override
            public void run() {
                if (recorder != null) {
                    try {
                        int amplitude = recorder.getMaxAmplitude();
                        double db = 20 * Math.log10((double) amplitude);
                        if (db < 0) db = 0;
                        String dbString = "דציבלים: " + Math.round(db) + " dB";
                        decibelText.setText(dbString);
                    } catch (IllegalStateException e) {
                        decibelText.setText("שגיאה בהקלטה");
                    }
                }
                noiseHandler.postDelayed(this, 500);
            }
        };
        noiseHandler.post(updateTask);
    }

    private void checkMicrophonePermission() {
        decibelText.setText("אין גישה למיקרופון. נדרש לאישור.");
        if (ContextCompat.checkSelfPermission(this, PERMISSION) == PackageManager.PERMISSION_GRANTED) {
            startRecording();
            startNoiseMonitoring();
        } else if (ActivityCompat.shouldShowRequestPermissionRationale(this, PERMISSION)) {
            showMicrophoneRationale();
        } else {
            showMicrophoneSettingsRedirect();
        }
    }
    private void showMicrophoneRationale() {
        new MaterialAlertDialogBuilder(this)
                .setCancelable(false)
                .setTitle("גישה למיקרופון")
                .setMessage("האפליקציה צריכה את המיקרופון כדי לזהות רעש סביבתי כחלק מהתנאים לכניסה.")
                .setPositiveButton("הבנתי", (dialog, which) -> requestPermissionLauncher.launch(PERMISSION))
                .setNegativeButton("לא", null)
                .show();
    }

    private void showMicrophoneSettingsRedirect() {
        new MaterialAlertDialogBuilder(this)
                .setCancelable(false)
                .setTitle("הרשאה חסומה")
                .setMessage("כדי להפעיל את זיהוי הרעש, נא לאפשר גישה למיקרופון דרך ההגדרות.")
                .setPositiveButton("פתח הגדרות", (dialog, which) -> openAppSettings())
                .setNegativeButton("ביטול", null)
                .show();
    }

    private void openAppSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        Uri uri = Uri.fromParts("package", getPackageName(), null);
        intent.setData(uri);
        manuallyPermissionResultLauncher.launch(intent);
    }

    private boolean isDeviceCharging() {
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = registerReceiver(null, ifilter);
        int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        return status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL;
    }

    private boolean isMusicPlaying() {
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        return audioManager.isMusicActive();
    }


}