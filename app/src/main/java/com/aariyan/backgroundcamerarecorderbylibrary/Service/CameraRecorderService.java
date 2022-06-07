package com.aariyan.backgroundcamerarecorderbylibrary.Service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.aariyan.backgroundcamerarecorderbylibrary.R;

import java.io.File;

public class CameraRecorderService extends Service implements SurfaceHolder.Callback {

    //For checking is the service is running or not:
    public static boolean isServiceRunning;

    public static final String CHANNEL_ID_STRING = "service_01";
    private Camera camera = null;


    public static WindowManager windowManager;
    public static SurfaceView surfaceView;
    public static ImageView imageView;
    public static WindowManager.LayoutParams layoutParams;

    static MediaRecorder mediaRecorder = null;
    File outputFileFolder;

    public CameraRecorderService() {
        isServiceRunning = false;
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public void onCreate() {
        //Start foreground service to avoid unexpected kill
        Notification notification = null;
        isServiceRunning = true;

        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel mChannel = null;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            mChannel = new NotificationChannel(CHANNEL_ID_STRING, getString(R.string.app_name),
                    NotificationManager.IMPORTANCE_LOW);
            notificationManager.createNotificationChannel(mChannel);
            notification = new Notification.Builder(getApplicationContext(), CHANNEL_ID_STRING)
                    .setContentTitle("CubeGo Video Recorder")
                    .setContentText("Please do NOT stop recording during the test")
                    .setSmallIcon(R.drawable.ic_launcher_background).build();
        } else {
            notification = new Notification.Builder(this)
                    .setContentTitle("CubeGo Video Recorder")
                    .setContentText("Please do NOT stop recording during the test")
                    .setSmallIcon(R.drawable.ic_launcher_background)     //TODO: change this icon?
                    .build();
        }
        startForeground(1234, notification);

        // Create new SurfaceView, set its size to 1x1, move it to the top left corner and set this service as a callback
        windowManager = (WindowManager) this.getSystemService(Context.WINDOW_SERVICE);
        surfaceView = new SurfaceView(this);

        int smallWindowHeight = (int) convertDpToPixel(125);
        int smallWindowWidth = (int) convertDpToPixel(75);

        layoutParams = new WindowManager.LayoutParams(
                smallWindowWidth, smallWindowHeight,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        layoutParams.gravity = Gravity.END | Gravity.BOTTOM;
        layoutParams.y = (int) convertDpToPixel(30);
        layoutParams.x = (int) convertDpToPixel(30);
        layoutParams.alpha = 1f;    // transparency

        imageView = new ImageView(this);
        imageView.setImageResource(R.drawable.ic_launcher_background);
        imageView.setY(0);

        windowManager.addView(surfaceView, layoutParams);
        windowManager.addView(imageView, layoutParams);
        surfaceView.getHolder().addCallback(this);

    }

    public float convertDpToPixel(float dp) {
        return dp * ((float) getApplicationContext().getResources().getDisplayMetrics().densityDpi / DisplayMetrics.DENSITY_DEFAULT);
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void surfaceCreated(@NonNull SurfaceHolder surfaceHolder) {

        Toast.makeText(this, "Recording Started!", Toast.LENGTH_SHORT).show();
        camera = Camera.open(1);
        camera.setDisplayOrientation(90);  //270
        mediaRecorder = new MediaRecorder();
        camera.unlock();
        mediaRecorder.setOrientationHint(270);  //270
        mediaRecorder.setPreviewDisplay(surfaceHolder.getSurface());
        mediaRecorder.setCamera(camera);
//        mediaRecorder.setVideoSize();
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);  //VOICE_RECOGNITION
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);

        //mediaRecorder.setProfile(CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH));
        //mediaRecorder.setProfile(CamcorderProfile.get(CamcorderProfile.QUALITY_720P));
        //mediaRecorder.setProfile(CamcorderProfile.get(tinyDB.getInt("QUALITY")));
        if (CamcorderProfile.hasProfile(1, CamcorderProfile.QUALITY_480P)) {
            mediaRecorder.setProfile(CamcorderProfile.get(CamcorderProfile.QUALITY_480P));
        } else {
            mediaRecorder.setProfile(CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH));
        }

        //outputFileFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES) +"/CubeGO";
        outputFileFolder = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES) + "/TestVideoRecorder");
        String completeName = new File(outputFileFolder + "/" + System.currentTimeMillis() + "-VIDEO" + ".mp4").getAbsolutePath();
        if (!outputFileFolder.exists()) {
            outputFileFolder.mkdir();
        }

        mediaRecorder.setOutputFile(completeName);

        try {
            mediaRecorder.prepare();
            mediaRecorder.start();

        } catch (Exception ignored) {
            Toast.makeText(this, "IGNORE: " + ignored.getMessage(), Toast.LENGTH_SHORT).show();
            Log.d("STORAGE_LOCATION", "surfaceCreated: " + ignored.getMessage());
        }

    }

    @Override
    public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {

    }

    @Override
    public void surfaceDestroyed(@NonNull SurfaceHolder holder) {

    }

    @Override
    public void onDestroy() {
        isServiceRunning = false;
        stopForeground(true);
        stopSelf();

        if (mediaRecorder != null) {
            mediaRecorder.stop();
            mediaRecorder.reset();
            mediaRecorder.release();
        }
        camera.release();
        windowManager.removeView(surfaceView);
        windowManager.removeView(imageView);
        Toast.makeText(this, "Recording Stopped!", Toast.LENGTH_SHORT).show();
        super.onDestroy();
    }

    private class MediaProjectionCallback extends MediaProjection.Callback {

    }

}