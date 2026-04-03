package com.gugesoft.lodibookreader;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.core.app.NotificationCompat;
import android.support.v4.media.session.MediaSessionCompat;

public class ReadingService extends Service {

    private static final String CHANNEL_ID = "LodiReadingChannel";
    private static final int NOTIF_ID = 101;
    private static final long IDLE_TIMEOUT_MS = 10 * 60 * 1000;

    public static final String ACTION_PLAY = "LODI_ACTION_PLAY";
    public static final String ACTION_PAUSE = "LODI_ACTION_PAUSE";
    public static final String ACTION_REWIND = "LODI_ACTION_REWIND";
    public static final String ACTION_CLOSE = "LODI_ACTION_CLOSE";

    private MediaSessionCompat mediaSession;
    private Handler idleHandler = new Handler(Looper.getMainLooper());
    private boolean isPlaying = false;

    private final Runnable stopServiceTask = () -> {
        if (!isPlaying) {
            stopForeground(true);
            stopSelf();
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();

        mediaSession = new MediaSessionCompat(this, "LodiReaderSession");
        mediaSession.setActive(true);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (intent != null) {
            String action = intent.getAction();

            if (ACTION_CLOSE.equals(action)) {
                sendBroadcast(new Intent(ACTION_CLOSE));
                stopForeground(true);
                stopSelf();
                return START_NOT_STICKY;
            }

            if (action != null && !action.equals("UPDATE_ONLY")) {
                sendBroadcast(new Intent(action));
            }

            isPlaying = intent.getBooleanExtra("IS_PLAYING", false);
            updateNotification();
        }

        return START_STICKY;
    }

    private void updateNotification() {
        idleHandler.removeCallbacks(stopServiceTask);

        if (!isPlaying) {
            idleHandler.postDelayed(stopServiceTask, IDLE_TIMEOUT_MS);
        }

        startForeground(NOTIF_ID, buildNotification());
    }

    private Notification buildNotification() {

        Intent mainIntent = new Intent(this, MainActivity.class);
        PendingIntent pMain = PendingIntent.getActivity(
                this, 0, mainIntent,
                PendingIntent.FLAG_IMMUTABLE
        );

        PendingIntent pRewind = PendingIntent.getService(
                this, 1,
                new Intent(this, ReadingService.class).setAction(ACTION_REWIND),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        PendingIntent pPlayPause = PendingIntent.getService(
                this, 2,
                new Intent(this, ReadingService.class)
                        .setAction(isPlaying ? ACTION_PAUSE : ACTION_PLAY),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        PendingIntent pClose = PendingIntent.getService(
                this, 3,
                new Intent(this, ReadingService.class).setAction(ACTION_CLOSE),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(this, CHANNEL_ID)
                        .setSmallIcon(android.R.drawable.ic_media_play)
                        .setContentTitle("Lodi Book Reader")
                        .setContentText(isPlaying ? "Reading..." : "Paused")
                        .setContentIntent(pMain)
                        .setOngoing(isPlaying)
                        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setOnlyAlertOnce(true)
                        .addAction(android.R.drawable.ic_media_rew, "Rewind", pRewind)
                        .addAction(
                                isPlaying ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
                                isPlaying ? "Pause" : "Play",
                                pPlayPause
                        )
                        .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Close", pClose)
                        .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                                .setMediaSession(mediaSession.getSessionToken())
                                .setShowActionsInCompactView(0, 1, 2)
                        );

        return builder.build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel =
                    new NotificationChannel(
                            CHANNEL_ID,
                            "Lodi Playback",
                            NotificationManager.IMPORTANCE_LOW
                    );

            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    @Override
    public void onDestroy() {
        if (mediaSession != null) mediaSession.release();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}