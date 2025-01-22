package com.seamless.utils;

import android.app.Activity;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.seamless.R;
import com.seamless.databinding.ActivityDownloadBinding;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;


@SuppressWarnings("ResultOfMethodCallIgnored")
public class Downloader {
    static final String modelMultiLingualBase = "unity_on_device_s2t.ptl";
    static final String modelMultiLingualBaseURL = "https://huggingface.co/facebook/seamless-m4t-unity-small-s2t/resolve/main/unity_on_device_s2t.ptl";
    static final String modelMultiLingualBaseMD5 = "bf044d516f14d1ec8e603e8e666fee16";
    static final long modelMultiLingualBaseSize = 504153032;
    static long downloadModelMultiLingualBaseSize = 0L;
    static boolean modelMultiLingualBaseFinished = false;

    public static boolean checkModels(final Activity activity) {
        File modelMultiLingualBaseFile = new File(activity.getExternalFilesDir(null) + "/" + modelMultiLingualBase);
        String calcModelMultiLingualBaseMD5 = "";
        if (modelMultiLingualBaseFile.exists()) {
            try {
                calcModelMultiLingualBaseMD5 = calculateMD5(String.valueOf(Paths.get(modelMultiLingualBaseFile.getPath())));
            } catch (IOException | NoSuchAlgorithmException e) {
                throw new RuntimeException(e);
            }
        }

        if (modelMultiLingualBaseFile.exists() && !(calcModelMultiLingualBaseMD5.equals(modelMultiLingualBaseMD5))) { modelMultiLingualBaseFile.delete(); modelMultiLingualBaseFinished = false;}

        return calcModelMultiLingualBaseMD5.equals(modelMultiLingualBaseMD5);
    }

    public static void downloadModels(final Activity activity, ActivityDownloadBinding binding) {
        binding.downloadProgress.setProgress(0);

        File modelMultiLingualBaseFile = new File(activity.getExternalFilesDir(null)+ "/" + modelMultiLingualBase);
        if (!modelMultiLingualBaseFile.exists()) {
            modelMultiLingualBaseFinished = false;
            Log.d("WhisperASR", "multi-lingual base model file does not exist");
            Thread thread = new Thread(() -> {
                try {
                    URL url;

                    url = new URL(modelMultiLingualBaseURL);

                    Log.d("WhisperASR", "Download model");

                    URLConnection ucon = url.openConnection();
                    ucon.setReadTimeout(5000);
                    ucon.setConnectTimeout(10000);

                    InputStream is = ucon.getInputStream();
                    BufferedInputStream inStream = new BufferedInputStream(is, 1024 * 5);

                    modelMultiLingualBaseFile.createNewFile();

                    FileOutputStream outStream = new FileOutputStream(modelMultiLingualBaseFile);
                    byte[] buff = new byte[5 * 1024];

                    int len;
                    while ((len = inStream.read(buff)) != -1) {
                        outStream.write(buff, 0, len);
                        if (modelMultiLingualBaseFile.exists()) downloadModelMultiLingualBaseSize = modelMultiLingualBaseFile.length();
                        activity.runOnUiThread(() -> {
                            binding.downloadSize.setText((downloadModelMultiLingualBaseSize)/1024/1024 + " MB");
                            binding.downloadProgress.setProgress((int) (((double)(downloadModelMultiLingualBaseSize) / (modelMultiLingualBaseSize)) * 100));
                        });
                    }
                    outStream.flush();
                    outStream.close();
                    inStream.close();
                    String calcModelMultiLingualBaseMD5="";
                    if (modelMultiLingualBaseFile.exists()) {
                        calcModelMultiLingualBaseMD5 = calculateMD5(String.valueOf(Paths.get(modelMultiLingualBaseFile.getPath())));
                    } else {
                        throw new IOException();  //throw exception if there is no modelMultiLingualSmallFile at this point
                    }

                    if (!(calcModelMultiLingualBaseMD5.equals(modelMultiLingualBaseMD5))){
                        modelMultiLingualBaseFile.delete();
                        modelMultiLingualBaseFinished = false;
                        activity.runOnUiThread(() -> {
                            Toast.makeText(activity, activity.getResources().getString(R.string.error_download), Toast.LENGTH_SHORT).show();
                        });
                    } else {
                        modelMultiLingualBaseFinished = true;
                        activity.runOnUiThread(() -> {
                            if (modelMultiLingualBaseFinished) binding.buttonStart.setVisibility(View.VISIBLE);
                        });
                    }
                } catch (NoSuchAlgorithmException | IOException i) {
                    activity.runOnUiThread(() -> Toast.makeText(activity, activity.getResources().getString(R.string.error_download), Toast.LENGTH_SHORT).show());
                    modelMultiLingualBaseFile.delete();
                    Log.w("WhisperASR", activity.getResources().getString(R.string.error_download), i);
                }
            });
            thread.start();
        } else {
            downloadModelMultiLingualBaseSize = modelMultiLingualBaseSize;
            modelMultiLingualBaseFinished = true;
            activity.runOnUiThread(() -> {
                if (modelMultiLingualBaseFinished) binding.buttonStart.setVisibility(View.VISIBLE);
            });
        }

    }

    public static String calculateMD5(String filePath) throws IOException, NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("MD5");
        try (InputStream is = new BufferedInputStream(new FileInputStream(filePath))) {
            byte[] buffer = new byte[8192]; // 8KB buffer
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                md.update(buffer, 0, bytesRead);
            }
        }
        byte[] hash = md.digest();
        return new BigInteger(1, hash).toString(16);
    }
}