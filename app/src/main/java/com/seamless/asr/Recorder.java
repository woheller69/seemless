package com.seamless.asr;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import com.seamless.R;

import java.io.ByteArrayOutputStream;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class Recorder {

    public interface RecorderListener {
        void onUpdateReceived(String message);
        void onRealtimeDataReceived(byte[] samples);
        void onFinalDataReceived(byte[] samples);
    }

    private static final String TAG = "Recorder";
    public static final String MSG_RECORDING = "Recording...";
    public static final String MSG_RECORDING_DONE = "Recording done...!";

    private final Context mContext;
    private final AtomicBoolean mInProgress = new AtomicBoolean(false);

    private RecorderListener mListener;
    private final Lock lock = new ReentrantLock();
    private final Condition hasTask = lock.newCondition();
    private final Object fileSavedLock = new Object(); // Lock object for wait/notify
    private final int maxSeconds;
    private final int realtimeSeconds;

    private volatile boolean shouldStartRecording = false;

    private final Thread workerThread;

    public Recorder(Context context, int maxSeconds, int realtimeSeconds) {
        this.mContext = context;
        this.maxSeconds = maxSeconds;
        this.realtimeSeconds = realtimeSeconds;
        // Initialize and start the worker thread
        workerThread = new Thread(this::recordLoop);
        workerThread.start();
    }

    public void setListener(RecorderListener listener) {
        this.mListener = listener;
    }

    public void start() {
        if (!mInProgress.compareAndSet(false, true)) {
            Log.d(TAG, "Recording is already in progress...");
            return;
        }
        lock.lock();
        try {
            shouldStartRecording = true;
            hasTask.signal();
        } finally {
            lock.unlock();
        }
    }

    public void stop() {
        mInProgress.set(false);

        // Wait for the recording thread to finish
        synchronized (fileSavedLock) {
            try {
                fileSavedLock.wait(); // Wait until notified by the recording thread
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // Restore interrupted status
            }
        }
    }

    public boolean isInProgress() {
        return mInProgress.get();
    }

    private void sendUpdate(String message) {
        if (mListener != null)
            mListener.onUpdateReceived(message);
    }

    private void sendRealtimeData(byte[] samples) {
        if (mListener != null)
            mListener.onRealtimeDataReceived(samples);
    }

    private void sendFinalData(byte[] samples) {
        if (mListener != null)
            mListener.onFinalDataReceived(samples);
    }

    private void recordLoop() {
        while (true) {
            lock.lock();
            try {
                while (!shouldStartRecording) {
                    hasTask.await();
                }
                shouldStartRecording = false;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } finally {
                lock.unlock();
            }

            // Start recording process
            try {
                recordAudio();
            } catch (Exception e) {
                Log.e(TAG, "Recording error...", e);
                sendUpdate(e.getMessage());
            } finally {
                mInProgress.set(false);
            }
        }
    }

    private void recordAudio() {
        if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "AudioRecord permission is not granted");
            sendUpdate(mContext.getString(R.string.need_record_audio_permission));
            return;
        }

        sendUpdate(MSG_RECORDING);

        int channels = 1;
        int bytesPerSample = 2;
        int sampleRateInHz = 16000;
        int channelConfig = AudioFormat.CHANNEL_IN_MONO;
        int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
        int audioSource = MediaRecorder.AudioSource.VOICE_RECOGNITION;

        int bufferSize = AudioRecord.getMinBufferSize(sampleRateInHz, channelConfig, audioFormat);
        AudioRecord audioRecord = new AudioRecord(audioSource, sampleRateInHz, channelConfig, audioFormat, bufferSize);
        audioRecord.startRecording();

        // Calculate maximum byte counts for maxSeconds
        int bytesForMaxSeconds = sampleRateInHz * bytesPerSample * channels * maxSeconds;
        int bytesForRealtimeSeconds = sampleRateInHz * bytesPerSample * channels * realtimeSeconds;

        ByteArrayOutputStream outputBuffer = new ByteArrayOutputStream(); // Buffer for saving data
        ByteArrayOutputStream realtimeBuffer = new ByteArrayOutputStream(); // Buffer for real-time processing

        byte[] audioData = new byte[bufferSize];
        int totalBytesRead = 0;

        while (mInProgress.get() && totalBytesRead < bytesForMaxSeconds) {
            int bytesRead = audioRecord.read(audioData, 0, bufferSize);
            if (bytesRead > 0) {
                outputBuffer.write(audioData, 0, bytesRead);  // Save all bytes read up to maxSeconds
                realtimeBuffer.write(audioData, 0, bytesRead); // Accumulate real-time audio data up to realtimeSeconds
                totalBytesRead += bytesRead;

                // Check if realtimeBuffer has more than realtimeSeconds of data
                if (bytesForRealtimeSeconds != 0 && realtimeBuffer.size() >= bytesForRealtimeSeconds) {
                    sendRealtimeData(realtimeBuffer.toByteArray()); // Send real-time data for processing
                    realtimeBuffer.reset(); // Clear the buffer for the next accumulation
                }
            } else {
                Log.d(TAG, "AudioRecord error, bytes read: " + bytesRead);
                break;
            }
        }

        audioRecord.stop();
        audioRecord.release();

        sendFinalData(outputBuffer.toByteArray());
        sendUpdate(MSG_RECORDING_DONE);

        // Notify the waiting thread that recording is complete
        synchronized (fileSavedLock) {
            fileSavedLock.notify(); // Notify that recording is finished
        }

    }

}
