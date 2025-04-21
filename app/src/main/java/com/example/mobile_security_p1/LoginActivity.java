// גרסה מלאה של LoginActivity עם ניהול הרשאות חכם כמו אצל המרצה שלך

package com.example.mobile_security_p1;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.media.Image;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import java.io.File;
import java.util.concurrent.ExecutionException;

public class LoginActivity extends AppCompatActivity {

    private EditText passwordInput;
    private Button loginButton;
    private TextView statusText, directionText, shakeStatusText, decibelText, smileStatus;
    private PreviewView previewView;
    private SensorManager sensorManager;
    private Sensor accelerometer, magnetometer;
    private float[] gravity, geomagnetic;
    private float azimuth;
    private long lastShakeTime = 0;
    private int shakeCount = 0;
    private boolean hasShakenEnough = false;
    private boolean smileDetected = false;
    private MediaRecorder recorder;
    private Handler noiseHandler = new Handler();
    private FaceDetector faceDetector;

    private final String[] REQUIRED_PERMISSIONS = new String[]{
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
    };

    private ActivityResultLauncher<String> permissionLauncher;
    private ActivityResultLauncher<Intent> settingsLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        findViews();

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (!isGranted) {
                        for (String permission : REQUIRED_PERMISSIONS) {
                            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                                if (!shouldShowRequestPermissionRationale(permission)) {
                                    showSettingsRedirectDialog(permission);
                                }
                            }
                        }
                    } else {
                        checkAllPermissions();
                    }
                });

        settingsLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> checkAllPermissions()
        );

        checkAllPermissions();

        loginButton.setOnClickListener(v -> {
            String password = passwordInput.getText().toString();

            boolean batteryOk = isBatteryLevelCorrect(password);
            boolean facingEast = isFacingEast();
            boolean noiseOk = isEnvironmentNoise();
            boolean charging = isDeviceCharging();
            boolean shaken = hasUserShakenPhone();
            boolean musicPlaying = isMusicPlaying();
            boolean smiling = smileDetected;

            if (batteryOk && facingEast && noiseOk && charging && shaken && musicPlaying && smiling) {
                startActivity(new Intent(LoginActivity.this, SuccessActivity.class));
                return;
            }

            SpannableStringBuilder resultText = new SpannableStringBuilder();
            resultText.append("סטטוס תנאים:\n");

            addConditionLine(resultText, "סיסמה תואמת לרמת סוללה", batteryOk);
            addConditionLine(resultText, "המכשיר פונה מזרחה", facingEast);
            addConditionLine(resultText, "הרעש בסביבה מספיק", noiseOk);
            addConditionLine(resultText, "המכשיר בטעינה", charging);
            addConditionLine(resultText, "בוצעו מספיק שקשוקים", shaken);
            addConditionLine(resultText, "מוזיקה מתנגנת", musicPlaying);
            addConditionLine(resultText, "חיוך זוהה", smiling);

            statusText.setText(resultText);
        });
    }

    private void addConditionLine(SpannableStringBuilder builder, String label, boolean isOk) {
        String icon = isOk ? "✔️" : "❌";
        int color = isOk ? 0xFF388E3C : 0xFFD32F2F; // ירוק / אדום

        String line = icon + " " + label + "\n";
        int start = builder.length();
        builder.append(line);
        builder.setSpan(
                new ForegroundColorSpan(color),
                start,
                builder.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        );
    }
    private void findViews() {
        passwordInput = findViewById(R.id.passwordInput);
        loginButton = findViewById(R.id.loginButton);
        statusText = findViewById(R.id.statusText);
        directionText = findViewById(R.id.directionText);
        shakeStatusText = findViewById(R.id.shakeStatusText);
        decibelText = findViewById(R.id.decibelText);
        previewView = findViewById(R.id.previewView);
        smileStatus = findViewById(R.id.smileStatus);
    }

    private void checkAllPermissions() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                askForPermission(permission);
                return;
            }
        }
        startSmileDetection();
        startRecording();
        startNoiseMonitoring();
    }

    private void askForPermission(String permission) {
        if (shouldShowRequestPermissionRationale(permission)) {
            showPermissionRationale(permission);
        } else {
            permissionLauncher.launch(permission);
        }
    }

    private void showPermissionRationale(String permission) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("נדרשת הרשאה")
                .setMessage("האפליקציה צריכה את ההרשאה הזו כדי לפעול כראוי.")
                .setPositiveButton("אישור", (dialog, which) -> permissionLauncher.launch(permission))
                .setNegativeButton("ביטול", null)
                .show();
    }

    private void showSettingsRedirectDialog(String permission) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("הרשאה חסומה")
                .setMessage("יש לאפשר את ההרשאה דרך ההגדרות כדי להמשיך.")
                .setPositiveButton("פתח הגדרות", (dialog, which) -> openAppSettings())
                .setNegativeButton("ביטול", null)
                .show();
    }

    private void openAppSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        Uri uri = Uri.fromParts("package", getPackageName(), null);
        intent.setData(uri);
        settingsLauncher.launch(intent);
    }

    private void startSmileDetection() {
        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .build();

        faceDetector = FaceDetection.getClient(options);

        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(this), imageProxy -> {
                    @SuppressLint("UnsafeOptInUsageError")
                    Image mediaImage = imageProxy.getImage();
                    if (mediaImage != null) {
                        InputImage image = InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());

                        faceDetector.process(image)
                                .addOnSuccessListener(faces -> {
                                    if (faces.isEmpty()) {
                                        smileStatus.setText("לא זוהו פנים");
                                    } else {
                                        for (Face face : faces) {
                                            Float smileProb = face.getSmilingProbability();
                                            if (smileProb != null && smileProb > 0.8) {
                                                smileStatus.setText("חיוך זוהה! 😁");
                                                smileDetected = true;
                                            } else {
                                                if(smileDetected)
                                                    smileDetected = false;
                                                smileStatus.setText("ממתין לחיוך...");
                                            }
                                        }
                                    }
                                    imageProxy.close();
                                })
                                .addOnFailureListener(e -> imageProxy.close());
                    } else {
                        imageProxy.close();
                    }
                });

                CameraSelector cameraSelector = new CameraSelector.Builder()
                        .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                        .build();

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);

            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private boolean isBatteryLevelCorrect(String input) {
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = registerReceiver(null, ifilter);
        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        return input.equals(String.valueOf(level));
    }

    private boolean isFacingEast() {
        return azimuth >= 80 && azimuth <= 100;
    }

    private boolean hasUserShakenPhone() {
        return hasShakenEnough;
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

    private void startRecording() {
        try {
            recorder = new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            recorder.setOutputFile(getCacheDir().getAbsolutePath() + "/temp.3gp");
            recorder.prepare();
            recorder.start();
        } catch (Exception e) {
            e.printStackTrace();
            decibelText.setText("הקלטה נכשלה: " + e.getMessage());
        }
    }

    private boolean isEnvironmentNoise() {
        if (recorder != null) {
            int amplitude = recorder.getMaxAmplitude();
            double db = 20 * Math.log10((double) amplitude);
            return db > 70;
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
                        decibelText.setText("דציבלים: " + Math.round(db) + " dB");
                    } catch (IllegalStateException e) {
                        decibelText.setText("שגיאה בהקלטה");
                    }
                }
                noiseHandler.postDelayed(this, 500);
            }
        };
        noiseHandler.post(updateTask);
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
        if (tempFile.exists()) tempFile.delete();
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
                if (acceleration > 8) {
                    long now = System.currentTimeMillis();
                    if (lastShakeTime == 0 || (now - lastShakeTime) < 1000) {
                        shakeCount++;
                        lastShakeTime = now;
                        if (shakeCount >= 3) hasShakenEnough = true;
                    } else {
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
                    if (azimuth < 0) azimuth += 360;
                    directionText.setText("כיוון: " + Math.round(azimuth) + "°");
                }
            }
        }
        @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}
    };
}
