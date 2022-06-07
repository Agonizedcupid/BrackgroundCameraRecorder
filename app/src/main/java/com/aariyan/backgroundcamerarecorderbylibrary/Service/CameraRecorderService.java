package com.aariyan.backgroundcamerarecorderbylibrary.Service;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.PixelFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.CamcorderProfile;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Size;
import android.view.Gravity;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.aariyan.backgroundcamerarecorderbylibrary.R;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class CameraRecorderService extends Service
        //implements SurfaceHolder.Callback
{

    //For checking is the service is running or not:
    public static boolean isServiceRunning;

    public static final String CHANNEL_ID_STRING = "service_01";
    private Camera camera = null;


    public static WindowManager windowManager;
    //public static SurfaceView surfaceView;
    public static ImageView imageView;
    public static WindowManager.LayoutParams layoutParams;

    private TextureView mTextureView;

    static MediaRecorder mediaRecorder = null;
    File outputFileFolder;

    private CameraDevice mCameraDevice;
    private String mCameraId;

    private Handler mBackgroundHandler;
    private HandlerThread mBackgroundHandlerThread;

    private CaptureRequest.Builder mCaptureRequestBuilder;
    private CameraCaptureSession mRecordCaptureSession;

    private Size mPreviewSize;
    private Size mVideoSize;

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

        mTextureView = new TextureView(this);
        mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);

        // Create new SurfaceView, set its size to 1x1, move it to the top left corner and set this service as a callback
        windowManager = (WindowManager) this.getSystemService(Context.WINDOW_SERVICE);
        //surfaceView = new SurfaceView(this);

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

        //windowManager.addView(mTextureView, layoutParams);
       // windowManager.addView(imageView, layoutParams);
        //surfaceView.getHolder().addCallback(this);
    }

    private void setUpTextureView() {
        if(mTextureView.isAvailable()) {
            setupCamera(mTextureView.getWidth(), mTextureView.getHeight());
            connectCamera();
            Toast.makeText(this, "1", Toast.LENGTH_SHORT).show();
        } else {
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
            if (mTextureView.isAvailable()) {
                Toast.makeText(this, "Called", Toast.LENGTH_SHORT).show();
            }
            Toast.makeText(this, "2", Toast.LENGTH_SHORT).show();
        }
    }

    private void startBackgroundThread() {
        mBackgroundHandlerThread = new HandlerThread("Camera2VideoImage");
        mBackgroundHandlerThread.start();
        mBackgroundHandler = new Handler(mBackgroundHandlerThread.getLooper());
    }

    private void stopBackgroundThread() {
        mBackgroundHandlerThread.quitSafely();
        try {
            mBackgroundHandlerThread.join();
            mBackgroundHandlerThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private CameraDevice.StateCallback mCameraDeviceStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice camera) {
            mCameraDevice = camera;
            startRecord();
            // Toast.makeText(getApplicationContext(),
            //         "Camera connection made!", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onDisconnected(CameraDevice camera) {
            camera.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(CameraDevice camera, int error) {
            camera.close();
            mCameraDevice = null;
        }
    };

    private TextureView.SurfaceTextureListener mSurfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            setupCamera(width, height);
            connectCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {

        }
    };

    private void setupCamera(int width, int height) {
        Toast.makeText(this, ""+width+" + "+height, Toast.LENGTH_SHORT).show();
        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            for (String cameraId : cameraManager.getCameraIdList()) {
                CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId);
                if (cameraCharacteristics.get(CameraCharacteristics.LENS_FACING) ==
                        CameraCharacteristics.LENS_FACING_FRONT) {
                    continue;
                }
                StreamConfigurationMap map = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                mPreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class), width, height);
                mVideoSize = chooseOptimalSize(map.getOutputSizes(MediaRecorder.class), height, width);
                mCameraId = cameraId;
                return;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private static Size chooseOptimalSize(Size[] choices, int width, int height) {
        List<Size> bigEnough = new ArrayList<Size>();
        for(Size option : choices) {
            if(option.getHeight() == option.getWidth() * height / width &&
                    option.getWidth() >= width && option.getHeight() >= height) {
                bigEnough.add(option);
            }
        }
        if(bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizeByArea());
        } else {
            return choices[0];
        }
    }

    private void connectCamera() {
        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return;
            }
            cameraManager.openCamera(mCameraId, mCameraDeviceStateCallback, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        setUpTextureView();
        startBackgroundThread();
        startRecord();

        return START_STICKY;
    }

    private void startRecord() {
        setUpMediaRecorder();

        try {
            SurfaceTexture surfaceTexture = mTextureView.getSurfaceTexture();
            surfaceTexture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            Surface previewSurface = new Surface(surfaceTexture);
            Surface recordSurface = mediaRecorder.getSurface();
            mCaptureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            mCaptureRequestBuilder.addTarget(previewSurface);
            mCaptureRequestBuilder.addTarget(recordSurface);

            mCameraDevice.createCaptureSession(Arrays.asList(previewSurface, recordSurface),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(CameraCaptureSession session) {
                            mRecordCaptureSession = session;
                            try {
                                mRecordCaptureSession.setRepeatingRequest(
                                        mCaptureRequestBuilder.build(), null, null
                                );
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onConfigureFailed(CameraCaptureSession session) {
                            Log.d("TAG", "onConfigureFailed: startRecord");
                        }
                    }, null);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void setUpMediaRecorder() {
        mediaRecorder = new MediaRecorder();
        mediaRecorder.setOrientationHint(270);  //270
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);  //VOICE_RECOGNITION
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
        //mediaRecorder.setVideoSize(mVideoSize.getWidth(), mVideoSize.getHeight());

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

    public float convertDpToPixel(float dp) {
        return dp * ((float) getApplicationContext().getResources().getDisplayMetrics().densityDpi / DisplayMetrics.DENSITY_DEFAULT);
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

//    @Override
//    public void surfaceCreated(@NonNull SurfaceHolder surfaceHolder) {
//
//        Toast.makeText(this, "Recording Started!", Toast.LENGTH_SHORT).show();
//        camera = Camera.open(1);
//        camera.setDisplayOrientation(90);  //270
//        mediaRecorder = new MediaRecorder();
//        camera.unlock();
//        mediaRecorder.setOrientationHint(270);  //270
//        mediaRecorder.setPreviewDisplay(surfaceHolder.getSurface());
//        mediaRecorder.setCamera(camera);
////        mediaRecorder.setVideoSize();
//        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);  //VOICE_RECOGNITION
//        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
//
//        //mediaRecorder.setProfile(CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH));
//        //mediaRecorder.setProfile(CamcorderProfile.get(CamcorderProfile.QUALITY_720P));
//        //mediaRecorder.setProfile(CamcorderProfile.get(tinyDB.getInt("QUALITY")));
//        if (CamcorderProfile.hasProfile(1, CamcorderProfile.QUALITY_480P)) {
//            mediaRecorder.setProfile(CamcorderProfile.get(CamcorderProfile.QUALITY_480P));
//        } else {
//            mediaRecorder.setProfile(CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH));
//        }
//
//        //outputFileFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES) +"/CubeGO";
//        outputFileFolder = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES) + "/TestVideoRecorder");
//        String completeName = new File(outputFileFolder + "/" + System.currentTimeMillis() + "-VIDEO" + ".mp4").getAbsolutePath();
//        if (!outputFileFolder.exists()) {
//            outputFileFolder.mkdir();
//        }
//
//        mediaRecorder.setOutputFile(completeName);
//
//        try {
//            mediaRecorder.prepare();
//            mediaRecorder.start();
//
//        } catch (Exception ignored) {
//            Toast.makeText(this, "IGNORE: " + ignored.getMessage(), Toast.LENGTH_SHORT).show();
//            Log.d("STORAGE_LOCATION", "surfaceCreated: " + ignored.getMessage());
//        }
//
//    }


    @Override
    public void onDestroy() {
        isServiceRunning = false;
        stopForeground(true);

        if(mCameraDevice != null) {
            mCameraDevice.close();
            mCameraDevice = null;
        }
        if (mediaRecorder != null) {
            mediaRecorder.stop();
            mediaRecorder.reset();
            mediaRecorder.release();
        }

        stopBackgroundThread();
        //camera.release();
        //windowManager.removeView(mTextureView);
        //windowManager.removeView(imageView);
        Toast.makeText(this, "Recording Stopped!", Toast.LENGTH_SHORT).show();
        super.onDestroy();
    }

    private static class CompareSizeByArea implements Comparator<Size> {

        @Override
        public int compare(Size lhs, Size rhs) {
            return Long.signum( (long)(lhs.getWidth() * lhs.getHeight()) -
                    (long)(rhs.getWidth() * rhs.getHeight()));
        }
    }

}