package com.aariyan.backgroundcamerarecorderbylibrary;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.Service;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Camera;
import android.net.Uri;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.provider.MediaStore;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.aariyan.backgroundcamerarecorderbylibrary.Service.CameraRecorderService;
import com.aariyan.backgroundcamerarecorderbylibrary.Widget.SampleGLView;
import com.daasuu.camerarecorder.CameraRecordListener;
import com.daasuu.camerarecorder.CameraRecorder;
import com.daasuu.camerarecorder.CameraRecorderBuilder;
import com.daasuu.camerarecorder.LensFacing;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

public class MainActivity extends AppCompatActivity {

    private static final int CAMERA_PERMISSION_REQUEST_CODE = 88888;

    private Button recordBtn, stopRecording;

    protected int cameraWidth = 1280;
    protected int cameraHeight = 720;
    protected int videoWidth = 720;
    protected int videoHeight = 720;

    protected LensFacing lensFacing = LensFacing.FRONT;
    public static String filepath;
    public static CameraRecorder cameraRecorder;
    public static GLSurfaceView sampleGLView;
    public static FrameLayout wrapView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        sampleGLView = new GLSurfaceView(getApplicationContext());
        cameraRecorder = new CameraRecorderBuilder(MainActivity.this, sampleGLView)
                .lensFacing(LensFacing.FRONT)
                .build();

        initUI();
    }

    //Here stopping the service if it's running already:
    private void stopMainService() {
        //Checking whether the service is already running or not:
        if (CameraRecorderService.isServiceRunning) {
            stopService(new Intent(MainActivity.this, CameraRecorderService.class));
        }
    }

    private void initUI() {

        wrapView = findViewById(R.id.wrap_view);
        stopRecording = findViewById(R.id.stopRecording);
        stopRecording.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                stopMainService();
            }
        });

        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager wm = (WindowManager) getApplicationContext().getSystemService(WINDOW_SERVICE);
        wm.getDefaultDisplay().getRealMetrics(metrics);
        videoWidth = 720;
        videoHeight = 1280;
        cameraWidth = 1280;
        cameraHeight = 720;

        recordBtn = findViewById(R.id.recordBtn);
        recordBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                filepath = getVideoFilePath();
                startCameraRecording();
            }
        });
    }

    private void startCameraRecording() {
        startBackgroundCameraRecorderService();
        //set the file path:

        //cameraRecorder.start(filepath);
    }

    public static String getVideoFilePath() {
        return getAndroidMoviesFolder().getAbsolutePath() + "/" + new SimpleDateFormat("yyyyMM_dd-HHmmss").format(new Date()) + "cameraRecorder.mp4";
    }

    public static File getAndroidMoviesFolder() {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES);
    }

    @Override
    protected void onResume() {
        checkPermission();
        super.onResume();
    }

    @Override
    protected void onStop() {
        super.onStop();
        //releaseCamera();
    }

    public static void releaseCamera() {
        if (sampleGLView != null) {
            sampleGLView.onPause();
        }

        if (cameraRecorder != null) {
            cameraRecorder.stop();
            cameraRecorder.release();
            cameraRecorder = null;
        }

        if (sampleGLView != null) {
            //wrapView.removeView(sampleGLView);
            sampleGLView = null;
        }
    }

    private boolean checkPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }

        // request camera permission if it has not been grunted.
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {

            requestPermissions(new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_EXTERNAL_STORAGE}, CAMERA_PERMISSION_REQUEST_CODE);
            return false;
        }
        setUpCamera();

        return true;
    }

    private void startBackgroundCameraRecorderService() {
        Intent intent = new Intent(this, CameraRecorderService.class);
        ContextCompat.startForegroundService(this, intent);
    }

    public void setUpCamera() {
        setUpCameraView();

        cameraRecorder = new CameraRecorderBuilder(this, sampleGLView)
                //.recordNoFilter(true)
                .cameraRecordListener(new CameraRecordListener() {
                    @Override
                    public void onGetFlashSupport(boolean flashSupport) {
                    }

                    @Override
                    public void onRecordComplete() {
                        exportMp4ToGallery(getApplicationContext(), filepath);
                    }

                    @Override
                    public void onRecordStart() {

                    }

                    @Override
                    public void onError(Exception exception) {
                        Log.e("CameraRecorder", exception.toString());
                    }

                    @Override
                    public void onCameraThreadFinish() {
//                        if (toggleClick) {
//                            runOnUiThread(() -> {
//                                setUpCamera();
//                            });
//                        }
//                        toggleClick = false;
                    }
                })
                .videoSize(videoWidth, videoHeight)
                .cameraSize(cameraWidth, cameraHeight)
                .lensFacing(lensFacing)
                .build();


    }

    public static void exportMp4ToGallery(Context context, String filePath) {
        final ContentValues values = new ContentValues(2);
        values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
        values.put(MediaStore.Video.Media.DATA, filePath);
        context.getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                values);
        context.sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,
                Uri.parse("file://" + filePath)));
    }

    public void setUpCameraView() {
//        runOnUiThread(() -> {
//            FrameLayout frameLayout = findViewById(R.id.wrap_view);
//            frameLayout.removeAllViews();
//            sampleGLView = null;
//            sampleGLView = new SampleGLView(getApplicationContext());
//            frameLayout.addView(sampleGLView);
//        });

        wrapView.removeAllViews();
        sampleGLView = null;
        sampleGLView = new SampleGLView(getApplicationContext());
        //wrapView.addView(sampleGLView);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case CAMERA_PERMISSION_REQUEST_CODE:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(MainActivity.this, "camera permission has been grunted.", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(MainActivity.this, "[WARN] camera permission is not grunted.", Toast.LENGTH_SHORT).show();
                }
                break;
        }
    }

    static class TestService extends Service {

        @Nullable
        @Override
        public IBinder onBind(Intent intent) {
            return null;
        }
    }
}