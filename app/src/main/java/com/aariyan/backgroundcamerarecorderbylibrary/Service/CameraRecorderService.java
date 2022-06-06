package com.aariyan.backgroundcamerarecorderbylibrary.Service;

import static com.aariyan.backgroundcamerarecorderbylibrary.MainActivity.cameraRecorder;
import static com.aariyan.backgroundcamerarecorderbylibrary.MainActivity.filepath;
import static com.aariyan.backgroundcamerarecorderbylibrary.MainActivity.releaseCamera;
import static com.aariyan.backgroundcamerarecorderbylibrary.MainActivity.sampleGLView;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;

import com.aariyan.backgroundcamerarecorderbylibrary.MainActivity;
import com.aariyan.backgroundcamerarecorderbylibrary.R;

public class CameraRecorderService extends Service {

    private String CHANNEL_ID = "com.aariyan.backgroundcamerarecorderbylibrary";


    //For checking is the service is running or not:
    public static boolean isServiceRunning;

    public CameraRecorderService() {
        isServiceRunning = false;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        //notifying the service is running:
        isServiceRunning = true;
        //Creating a notification channel for latest android version
        createNotificationChannel();
        createNotification();
        MainActivity.wrapView.addView(sampleGLView);
    }

    private void createNotification() {
        //This intent will be used as pending intent; means when user will click on notification tab it will open this activity:
        Intent notificationIntent = new Intent(this, MainActivity.class);
        //Attaching the pending intent:
        //PendingIntent.FLAG_IMMUTABLE is used for >= android 11:
        PendingIntent pendingIntent = PendingIntent.getActivity(this,
                0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                //this is the notification title:
                .setContentTitle("Service is Running")
                //Notification sub-title:
                .setContentText("Activity Tracking!")
                //notification icon:
                //setting the pending intent on the notification:
                .setContentIntent(pendingIntent)
                //set the background color of intent
                .setColor(getResources().getColor(R.color.teal_700))
                //Finally build the notification to show:
                .build();
        /**
         * A started service can use the startForeground API to put the service in a foreground state,
         * where the system considers it to be something the user is actively aware of and thus not
         * a candidate for killing when low on memory.
         */
        // it will starting show the ForeGround notification:
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification);
        }
    }

    //Notification channel is only needed for above Oreo:
    private void createNotificationChannel() {
        //Checking the device OS version:
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            //App name
            String appName = getString(R.string.app_name);
            //creating the notification channel here and adding all the information:
            NotificationChannel serviceChannel = new NotificationChannel(
                    //Channel id. that could be anything but same package name is recommended:
                    CHANNEL_ID,
                    //Putting the app name to show
                    appName,
                    //This is the importance on notification showing:
                    //For now we are setting as Default:
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            //Instantiating the Notification Manager:
            NotificationManager manager = getSystemService(NotificationManager.class);
            //Finally creating the notification channel and passing as parameter of manager:
            manager.createNotificationChannel(serviceChannel);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Toast.makeText(this, "Service Started!", Toast.LENGTH_SHORT).show();
        startCameraRecording();
        return START_REDELIVER_INTENT;
    }

    private void startCameraRecording() {
        //set the file path:
        //filepath = getVideoFilePath();
        cameraRecorder.start(filepath);
    }

    @Override
    public void onDestroy() {
        isServiceRunning = false;
        stopForeground(true);
        stopScreenRecording();
        Toast.makeText(this, "Service Destroyed!", Toast.LENGTH_SHORT).show();
        super.onDestroy();
    }

    private void stopScreenRecording() {
        releaseCamera();
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }
}