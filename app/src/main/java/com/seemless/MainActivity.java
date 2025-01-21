package com.seemless;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.seemless.asr.RecordBuffer;
import com.seemless.asr.Recorder;
import org.pytorch.IValue;
import org.pytorch.LiteModuleLoader;
import org.pytorch.Module;
import org.pytorch.Tensor;
import java.io.File;
import java.nio.FloatBuffer;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    private static final String DEFAULT_MODEL_TO_USE = "unity_on_device_s2t.ptl";

    private EditText tvResult;
    private FloatingActionButton fabCopy;
    private ImageButton btnRecord;
    private Button btnTransEng;
    private Button btnTransSpa;
    private Button btnTransPor;
    private Button btnTransHin;
    private Button btnTransRus;
    private ImageButton btnInfo;
    private ProgressBar processingBar;

    private Recorder mRecorder = null;

    private File sdcardDataFolder = null;
    private File selectedTfliteFile = null;
    private Module module;

    private final Handler handler = new Handler(Looper.getMainLooper());

    @SuppressLint("ClickableViewAccessibility")

    @Override
    protected void onDestroy(){
        if (module != null) module.destroy();
        super.onDestroy();
    }
    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        // Initialize default model to use
        sdcardDataFolder = this.getExternalFilesDir(null);
        selectedTfliteFile = new File(sdcardDataFolder, DEFAULT_MODEL_TO_USE);
        initModel();

        btnInfo = findViewById(R.id.btnInfo);
        btnInfo.setOnClickListener(view -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/woheller69/seemless#Donate"))));

        btnRecord = findViewById(R.id.btnRecord);

        btnRecord.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                // Pressed
                Log.d(TAG, "Start recording...");
                resetLanguageButtons();
                startRecording();
            } else if (event.getAction() == MotionEvent.ACTION_UP) {
                // Released
                btnRecord.setBackgroundResource(R.drawable.rounded_button_background);
                if (mRecorder != null && mRecorder.isInProgress()) {
                    Log.d(TAG, "Recording is in progress... stopping...");
                    stopRecording();
                }
            }
            return true;
        });

        btnTransEng = findViewById(R.id.btnTransEng);
        btnTransEng.setOnClickListener(v -> {
            if (mRecorder != null && mRecorder.isInProgress()) {
                Log.d(TAG, "Recording is in progress... stopping...");
                stopRecording();
            }
            resetLanguageButtons();
            btnTransEng.setBackgroundResource(R.drawable.rounded_button_background_pressed);
            startTranslation("eng");

        });

        btnTransSpa = findViewById(R.id.btnTransSpa);
        btnTransSpa.setOnClickListener(v -> {
            if (mRecorder != null && mRecorder.isInProgress()) {
                Log.d(TAG, "Recording is in progress... stopping...");
                stopRecording();
            }
            resetLanguageButtons();
            btnTransSpa.setBackgroundResource(R.drawable.rounded_button_background_pressed);
            startTranslation("spa");

        });

        btnTransPor = findViewById(R.id.btnTransPor);
        btnTransPor.setOnClickListener(v -> {
            if (mRecorder != null && mRecorder.isInProgress()) {
                Log.d(TAG, "Recording is in progress... stopping...");
                stopRecording();
            }
            resetLanguageButtons();
            btnTransPor.setBackgroundResource(R.drawable.rounded_button_background_pressed);
            startTranslation("por");

        });

        btnTransHin = findViewById(R.id.btnTransHin);
        btnTransHin.setOnClickListener(v -> {
            if (mRecorder != null && mRecorder.isInProgress()) {
                Log.d(TAG, "Recording is in progress... stopping...");
                stopRecording();
            }
            resetLanguageButtons();
            btnTransHin.setBackgroundResource(R.drawable.rounded_button_background_pressed);
            startTranslation("hin");

        });

        btnTransRus = findViewById(R.id.btnTransRus);
        btnTransRus.setOnClickListener(v -> {
            if (mRecorder != null && mRecorder.isInProgress()) {
                Log.d(TAG, "Recording is in progress... stopping...");
                stopRecording();
            }
            resetLanguageButtons();
            btnTransRus.setBackgroundResource(R.drawable.rounded_button_background_pressed);
            startTranslation("rus");

        });

        tvResult = findViewById(R.id.tvResult);
        tvResult.setOnClickListener(view -> tvResult.setCursorVisible(true));
        fabCopy = findViewById(R.id.fabCopy);
        fabCopy.setOnClickListener(v -> {
            String textToCopy = tvResult.getText().toString();
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText(getString(R.string.translated_output), textToCopy);
            clipboard.setPrimaryClip(clip);
        });

        processingBar = findViewById(R.id.processing_bar);

        mRecorder = new Recorder(this, 60, 0);  //no realtime processing needed
        mRecorder.setListener(new Recorder.RecorderListener() {
            @Override
            public void onUpdateReceived(String message) {
                Log.d(TAG, "Update is received, Message: " + message);
                if (message.equals(Recorder.MSG_RECORDING)) {
                    handler.post(() -> tvResult.setText(""));
                    handler.post(() -> btnRecord.setBackgroundResource(R.drawable.rounded_button_background_pressed));
                } else if (message.equals(Recorder.MSG_RECORDING_DONE)) {
                    handler.post(() -> btnRecord.setBackgroundResource(R.drawable.rounded_button_background));
                }
            }

            @Override
            public void onRealtimeDataReceived(byte[] samples) {}

            @Override
            public void onFinalDataReceived(byte[] samples) {
                // Save recorded audio data to RecordBuffer
                RecordBuffer.setOutputBuffer(samples);
            }
        });
        getOnBackPressedDispatcher().addCallback(new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (tvResult.isCursorVisible()) tvResult.setCursorVisible(false);
                else finish();
            }
        });
        if (GithubStar.shouldShowStarDialog(this)) GithubStar.starDialog(this, "https://github.com/woheller69/seemless");
        checkRecordPermission();

    }

    private void startTranslation(String lang) {
        handler.post(() -> tvResult.setText(""));
        processingBar.setIndeterminate(true);
        float[] samples = RecordBuffer.getSamples();
        if (samples.length == 0) {
            resetLanguageButtons();
            processingBar.setIndeterminate(false);
            return;
        }

        FloatBuffer inTensorBuffer = Tensor.allocateFloatBuffer(samples.length);
        for (float val : samples)
            inTensorBuffer.put(val);

        Tensor inTensor = Tensor.fromBlob(inTensorBuffer, new long[]{1, samples.length});  //channels 1, time steps = samples.length

         // Run the model
        Thread thread = new Thread(() -> {
            Log.d("Inference","Inference started, language: " + lang);
            if (module != null){
                IValue outputs = module.forward(IValue.from(inTensor), IValue.from(lang));

                Log.d("Inference","Inference finished");
                String text = outputs.toStr();
                runOnUiThread(() -> {
                    tvResult.setText(text);
                    processingBar.setIndeterminate(false);
                });
                Log.d("Output","Inference output: " + text);
            } else {
                runOnUiThread(() -> {
                    processingBar.setIndeterminate(false);
                });
                Log.d("Output","Inference output: " + "Model not ready");
            }

        });
        thread.start();

    }


    private void initModel(){
        Thread thread = new Thread(() -> {
            if (module == null) module = LiteModuleLoader.load(selectedTfliteFile.getAbsolutePath());
        });
        thread.start();
    }

    private void checkRecordPermission() {
        int permission = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO);
        if (permission == PackageManager.PERMISSION_GRANTED) {
        } else {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 0);
            Toast.makeText(this, getString(R.string.need_record_audio_permission), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Record permission is granted");
        } else {
            Log.d(TAG, "Record permission is not granted");
        }
    }

    // Recording calls
    private void startRecording() {
        checkRecordPermission();
        mRecorder.start();
    }

    private void stopRecording() {
        mRecorder.stop();
    }

    public void resetLanguageButtons(){
        btnTransEng.setBackgroundResource(R.drawable.rounded_button_background);
        btnTransSpa.setBackgroundResource(R.drawable.rounded_button_background);
        btnTransPor.setBackgroundResource(R.drawable.rounded_button_background);
        btnTransHin.setBackgroundResource(R.drawable.rounded_button_background);
        btnTransRus.setBackgroundResource(R.drawable.rounded_button_background);
    }
}