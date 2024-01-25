package edu.cmu.pocketsphinx.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.annotation.NonNull;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.video.MediaStoreOutputOptions;
import androidx.camera.video.Quality;
import androidx.camera.video.QualitySelector;
import androidx.camera.video.Recorder;
import androidx.camera.video.Recording;
import androidx.camera.video.VideoCapture;
import androidx.camera.video.VideoRecordEvent;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import com.google.common.util.concurrent.ListenableFuture;

import edu.cmu.pocketsphinx.Assets;
import edu.cmu.pocketsphinx.Hypothesis;
import edu.cmu.pocketsphinx.RecognitionListener;
import edu.cmu.pocketsphinx.SpeechRecognizer;
import edu.cmu.pocketsphinx.SpeechRecognizerSetup;


public class MainActivity extends AppCompatActivity implements RecognitionListener {

    Recording recording = null;
    VideoCapture<Recorder> videoCapture = null;
    ImageButton capture, toggleFlash, flipCamera, question;
    ImageView rec;
    PreviewView previewView;
    int cameraFacing = CameraSelector.LENS_FACING_BACK;
    private ImageCapture imageCapture;
    private static final String MAIN = "wakeup";
    private static final String KEYVIDEO = "recording";
    private static final String KEYPHOTO = "photograph";
    private static final int PERMISSIONS_REQUEST = 1;
    private SpeechRecognizer recognizer;
    private ToneGenerator toneGenerator;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);

        setContentView(R.layout.main);
        previewView = findViewById(R.id.cameraPreview);
        capture = findViewById(R.id.capture);
        toggleFlash = findViewById(R.id.toggleFlash);
        flipCamera = findViewById(R.id.flipCamera);
        question = findViewById(R.id.question);
        rec = findViewById(R.id.record);
        rec.setVisibility(View.INVISIBLE);

        toneGenerator = new ToneGenerator(AudioManager.STREAM_ALARM, 100);
        question.setOnClickListener(v -> openHelp());

        // Request audio permission
        if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
        {
            String[] permissions = {Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO};
            ActivityCompat.requestPermissions(this, permissions, PERMISSIONS_REQUEST);
        } else {
            startCamera(cameraFacing);
            new SetupTask(this).execute();
        }

        flipCamera.setOnClickListener(view -> {
            if (cameraFacing == CameraSelector.LENS_FACING_BACK) {
                cameraFacing = CameraSelector.LENS_FACING_FRONT;
            } else {
                cameraFacing = CameraSelector.LENS_FACING_BACK;
            }
            startCamera(cameraFacing);
        });
    }

    public static class SetupTask {

        private final WeakReference<MainActivity> activityReference;

        public SetupTask(MainActivity activity) {
            this.activityReference = new WeakReference<>(activity);
        }

        public void execute() {
            new Thread(() -> {
                try {
                    Assets assets = new Assets(activityReference.get());
                    File assetDir = assets.syncAssets();
                    activityReference.get().setupRecognizer(assetDir);

                    // Use a Handler to post back to the main thread
                    new Handler(Looper.getMainLooper()).post(() -> activityReference.get().startSpotting());

                } catch (IOException e) {
                    // Handle the exception
                    Log.d("SpeechRecognition", "Failed to init recognizer " + e);
                }
            }).start();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSIONS_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED &&
                    grantResults.length > 1 && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                startCamera(cameraFacing);
                new SetupTask(this).execute();
            } else {
                finish();
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (toneGenerator != null) {
            toneGenerator.release();
            toneGenerator = null;
        }

        if (recognizer != null) {
            recognizer.cancel();
            recognizer.shutdown();
        }
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onPartialResult(Hypothesis hypothesis) {

    }

    @Override
    public void onResult(Hypothesis hypothesis) {
        if (hypothesis != null) {
            String text = hypothesis.getHypstr();
            if (text.contains(KEYVIDEO)) {
                playBeep(ToneGenerator.TONE_PROP_BEEP);
                Log.e("RECOGNITION", "Keyword Spotted: " + KEYVIDEO);
                captureVideo().thenRun(() -> recognizer.startListening(MAIN));
            } else if (text.contains(KEYPHOTO)) {
                playBeep(ToneGenerator.TONE_CDMA_ABBR_ALERT);
                Log.e("RECOGNITION", "Keyword Spotted: " + KEYPHOTO);
                takePicture().thenRun(() -> recognizer.startListening(MAIN));
            }
        }
        recognizer.startListening(MAIN);
    }

    @Override
    public void onBeginningOfSpeech() {
    }

    @Override
    public void onEndOfSpeech() {
        recognizer.stop();
    }

    private void startSpotting() {
        recognizer.startListening(MAIN); // Start listening for the keyword
    }

    private void setupRecognizer(File assetsDir) throws IOException {
        recognizer = SpeechRecognizerSetup.defaultSetup()
                .setAcousticModel(new File(assetsDir, "en-us-ptm"))
                .setDictionary(new File(assetsDir, "cmudict-en-us.dict"))
                .getRecognizer();
        recognizer.addListener(this);

        File menuGrammar = new File(assetsDir, "keywords");
        recognizer.addKeywordSearch(MAIN, menuGrammar);
    }

    @Override
    public void onError(Exception error) {
        Log.d("SpeechRecognition", Objects.requireNonNull(error.getMessage()));
    }

    @Override
    public void onTimeout() {
    }

    public void startCamera(int cameraFacing) {
        ListenableFuture<ProcessCameraProvider> listenableFuture = ProcessCameraProvider.getInstance(this);

        listenableFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = listenableFuture.get();

                Preview preview = new Preview.Builder().build();

                imageCapture = new ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .setTargetRotation((int) previewView.getRotation()).build();

                Recorder recorder = new Recorder.Builder()
                        .setQualitySelector(QualitySelector.from(Quality.HIGHEST))
                        .build();
                videoCapture = VideoCapture.withOutput(recorder);

                CameraSelector cameraSelector = new CameraSelector.Builder()
                        .requireLensFacing(cameraFacing).build();

                cameraProvider.unbindAll();

                Camera camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, videoCapture, imageCapture);

                toggleFlash.setOnClickListener(view -> setFlashIcon(camera));

                preview.setSurfaceProvider(previewView.getSurfaceProvider());
            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    public CompletableFuture<Void> captureVideo() {
        CompletableFuture<Void> future = new CompletableFuture<>();

        Recording recording1 = recording;
        if (recording1 != null) {
            recording1.stop();
            recording = null;
            return future;
        }

        String name = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.getDefault()).format(System.currentTimeMillis());
        ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4");
        contentValues.put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES); // Specify Movies directory

        MediaStoreOutputOptions options = new MediaStoreOutputOptions.Builder(
                getContentResolver(),
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        ).build();

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return future;
        }

        recording = videoCapture.getOutput().prepareRecording(MainActivity.this, options).withAudioEnabled().start(ContextCompat.getMainExecutor(MainActivity.this), videoRecordEvent -> {
            if (videoRecordEvent instanceof VideoRecordEvent.Start) {
                rec.setVisibility(View.VISIBLE);
                capture.setEnabled(true);

                // Set the duration for video capture (10 seconds in this example)
                long captureDurationMillis = 10000;

                // Schedule a task to stop the recording after the specified duration
                Timer timer = new Timer();
                timer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        if (recording != null) {
                            recording.stop();
                            recording = null;
                            timer.cancel(); // Stop the timer after stopping the recording
                        }
                    }
                }, captureDurationMillis);

            } else if (videoRecordEvent instanceof VideoRecordEvent.Finalize) {
                rec.setVisibility(View.INVISIBLE);
                if (!((VideoRecordEvent.Finalize) videoRecordEvent).hasError()) {
                    playBeep(ToneGenerator.TONE_CDMA_ABBR_ALERT);

                    String msg = "Video Captured and Saved";
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                } else {
                    recording.close();
                    recording = null;
                    String msg = "Error: " + ((VideoRecordEvent.Finalize) videoRecordEvent).getError();
                    String err = "Video Capture Failed ";
                    Toast.makeText(this, err, Toast.LENGTH_SHORT).show();
                    Log.e("VIDEO", msg);
                }
            }
            future.complete(null);
        });
        return future;
    }

    public CompletableFuture<Void> takePicture() {
        CompletableFuture<Void> future = new CompletableFuture<>();
        String nameTimeStamp = "IMG_" + System.currentTimeMillis();
        String name = nameTimeStamp + ".jpeg";
        ImageCapture.OutputFileOptions outputFileOptions = null;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            ContentValues contentValues = new ContentValues();
            contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, nameTimeStamp);
            contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
            contentValues.put(MediaStore.MediaColumns.ORIENTATION, 90);

            outputFileOptions = new ImageCapture.OutputFileOptions.Builder(
                    this.getContentResolver(),
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    contentValues
            ).build();
        } else {
            File mImageDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
            boolean isDirectoryCreated = mImageDir.exists() || mImageDir.mkdirs();

            if (isDirectoryCreated) {
                File file = new File(mImageDir, name);
                outputFileOptions = new ImageCapture.OutputFileOptions.Builder(file).build();
            }
        }

        assert outputFileOptions != null;
        imageCapture.takePicture(outputFileOptions, ContextCompat.getMainExecutor(this),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                        Log.e("IMAGE", "Image Capture Success");

                        // Get the saved image URI
                        Uri savedUri = outputFileResults.getSavedUri();
                        assert savedUri != null;
                        //Log.v("IMAGE", "Saved Image Uri " + savedUri);

                        // Update the MediaStore to make the image appear in the gallery
                        Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                        mediaScanIntent.setData(savedUri);
                        sendBroadcast(mediaScanIntent);

                        Toast.makeText(getApplicationContext(), "Image Captured and Saved", Toast.LENGTH_SHORT).show();
                        future.complete(null); // Complete the CompletableFuture
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        Log.e("IMAGE", "Image Capture Failed With Exception : " + exception);
                        Toast.makeText(MainActivity.this, "Image Capture Failed", Toast.LENGTH_SHORT).show();
                        future.completeExceptionally(exception); // Complete the CompletableFuture exceptionally on error
                    }
                });

        return future;
    }

    private void setFlashIcon(Camera camera) {
        if (camera.getCameraInfo().hasFlashUnit()) {
            if (camera.getCameraInfo().getTorchState().getValue() == 0) {
                camera.getCameraControl().enableTorch(true);
                toggleFlash.setImageResource(R.drawable.baseline_flash_off_24);
            } else {
                camera.getCameraControl().enableTorch(false);
                toggleFlash.setImageResource(R.drawable.baseline_flash_on_24);
            }
        } else {
            runOnUiThread(() -> Toast.makeText(MainActivity.this, "Flash is not available currently", Toast.LENGTH_SHORT).show());
        }
    }

    private void playBeep(int tone) {
        if (toneGenerator != null) {
            // Play a short beep with the specified volume
            toneGenerator.startTone(tone, 400);

        }
    }

    public void openHelp(){
        Intent intent = new Intent(this, HelpActivity.class);
        startActivity(intent);
        finish();
    }
}