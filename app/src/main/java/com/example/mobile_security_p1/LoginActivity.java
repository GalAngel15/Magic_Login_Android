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
import androidx.core.app.ActivityCompat;
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
    private TextView statusText;
    private SensorManager sensorManager;
    private Sensor accelerometer, magnetometer;
    private float[] gravity, geomagnetic;
    private float azimuth = 0f;
    private TextView directionText, shakeStatusText;
    private ActivityResultLauncher<String> requestPermissionLauncher;
    private ActivityResultLauncher<Intent> manuallyPermissionResultLauncher;
    private MediaRecorder recorder;
    private TextView decibelText;
    private Handler noiseHandler = new Handler();
    private long lastShakeTime = 0;
    private int shakeCount = 0;
    private boolean hasShakenEnough = false;
    private static final int SHAKE_THRESHOLD = 3;
    private PreviewView previewView;
    private TextView smileStatus;
    private boolean smileDetected = false;
    private FaceDetector faceDetector;
    private final String[] REQUIRED_PERMISSIONS = new String[] {
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
    };

    private ActivityResultLauncher<String> permissionLauncher;
    private ActivityResultLauncher<Intent> settingsLauncher;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> checkAllPermissions()
        );

        settingsLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> checkAllPermissions()
        );

        checkAllPermissions(); // להתחיל את הבדיקה

        findViews();
        startSmileDetection();

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

        loginButton.setOnClickListener(v -> {
            String password = passwordInput.getText().toString();
            boolean allConditionsMet =
                    isBatteryLevelCorrect(password)
                            && isFacingEast()
                            && isEnvironmentQuiet()
                            && isDeviceCharging()
                            && hasUserShakenPhone()
                            && isMusicPlaying()
                            && smileDetected;
            if (allConditionsMet) {
                Intent intent = new Intent(LoginActivity.this, SuccessActivity.class);
                startActivity(intent);
            } else {
                statusText.setText("התנאים לא התקיימו: סוללה / כיוון / רעש / טעינה / שקשוק / מוזיקה / חיוך");
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
        previewView = findViewById(R.id.previewView);
        smileStatus = findViewById(R.id.smileStatus);
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

                if (acceleration > 5) {
                    long now = System.currentTimeMillis();
                    if (lastShakeTime == 0 || (now - lastShakeTime) < 1000) {
                        shakeCount++;
                        lastShakeTime = now;
                        if (shakeCount >= SHAKE_THRESHOLD) {
                            hasShakenEnough = true;
                        }
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

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {}
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
            recorder.setOutputFile(getCacheDir().getAbsolutePath() + "/temp.3gp");
            recorder.prepare();
            recorder.start();
        } catch (Exception e) {
            e.printStackTrace();
            decibelText.setText("הקלטה נכשלה: " + e.getMessage());
        }
    }

    private boolean isEnvironmentQuiet() {
        if (recorder != null) {
            int amplitude = recorder.getMaxAmplitude();
            double db = 20 * Math.log10((double) amplitude);
            return db < 60;
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
        return status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL;
    }

    private boolean isMusicPlaying() {
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        return audioManager.isMusicActive();
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
                                                smileStatus.setText("ממתין לחיוך...");
                                            }
                                        }
                                    }
                                    imageProxy.close();
                                })
                                .addOnFailureListener(e -> {
                                    smileStatus.setText("שגיאה בזיהוי פנים");
                                    imageProxy.close();
                                });
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

    private void checkAllPermissions() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                if (ActivityCompat.shouldShowRequestPermissionRationale(this, permission)) {
                    showPermissionRationale(permission);
                } else {
                    permissionLauncher.launch(permission);
                }
                return; // יוצא אחרי טיפול בהרשאה הראשונה שלא ניתנה
            }
        }

        // אם הגענו לכאן – כל ההרשאות תקינות
        startSmileDetection();
        startRecording();
        startNoiseMonitoring();
    }

    private void showPermissionRationale(String permission) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("נדרש אישור")
                .setMessage("האפליקציה צריכה את ההרשאה הזו כדי לעבוד כראוי.")
                .setPositiveButton("אפשר", (dialog, which) -> permissionLauncher.launch(permission))
                .setNegativeButton("לא עכשיו", null)
                .show();
    }
}
