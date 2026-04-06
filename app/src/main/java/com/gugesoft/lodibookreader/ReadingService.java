package com.gugesoft.lodibookreader;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;

public class ReadingService extends Service {

    private static final String CHANNEL_ID = "LodiReadingChannel";
    private static final int NOTIF_ID = 101;

    public static final String ACTION_PLAY = "LODI_ACTION_PLAY";
    public static final String ACTION_PAUSE = "LODI_ACTION_PAUSE";
    public static final String ACTION_REWIND = "LODI_ACTION_REWIND";
    public static final String ACTION_CLOSE = "LODI_ACTION_CLOSE";

    private MediaSessionCompat mediaSession;
    private TTSPlayer ttsPlayer;
    private boolean isPlaying = false;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();

        // Initialize TTSPlayer here so playback is owned by the service
        ttsPlayer = new TTSPlayer(this, null);

        mediaSession = new MediaSessionCompat(this, "LodiReaderSession");

        // Declare supported actions
        PlaybackStateCompat state = new PlaybackStateCompat.Builder()
                .setActions(PlaybackStateCompat.ACTION_PLAY |
                        PlaybackStateCompat.ACTION_PAUSE |
                        PlaybackStateCompat.ACTION_PLAY_PAUSE |
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)
                .setState(PlaybackStateCompat.STATE_PAUSED, 0, 1.0f)
                .build();
        mediaSession.setPlaybackState(state);

        // Hook headset buttons directly to TTSPlayer
        mediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override
            public void onPlay() {
                ttsPlayer.play();
                isPlaying = true;
                updateNotification();
            }

            @Override
            public void onPause() {
                ttsPlayer.pause();
                isPlaying = false;
                updateNotification();
            }

            @Override
            public void onSkipToPrevious() {
                ttsPlayer.playFrom(Math.max(0, ttsPlayer.getCurrentIndex() - 1));
            }
        });

        mediaSession.setActive(true);

        // Start foreground immediately with initial notification
        startForeground(NOTIF_ID, buildNotification());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();

            if (ACTION_CLOSE.equals(action)) {
                stopForeground(true);
                stopSelf();
                return START_NOT_STICKY;
            } else if (ACTION_PLAY.equals(action)) {
                ttsPlayer.play();
                isPlaying = true;
            } else if (ACTION_PAUSE.equals(action)) {
                ttsPlayer.pause();
                isPlaying = false;
            } else if (ACTION_REWIND.equals(action)) {
                ttsPlayer.playFrom(Math.max(0, ttsPlayer.getCurrentIndex() - 1));
            }

            updateNotification();
        }
        return START_STICKY;
    }

    private void updateNotification() {
        startForeground(NOTIF_ID, buildNotification());
    }

    private Notification buildNotification() {
        Intent mainIntent = new Intent(this, MainActivity.class);
        PendingIntent pMain = PendingIntent.getActivity(
                this, 0, mainIntent, PendingIntent.FLAG_IMMUTABLE);

        PendingIntent pRewind = PendingIntent.getService(
                this, 1, new Intent(this, ReadingService.class).setAction(ACTION_REWIND),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        PendingIntent pPlayPause = PendingIntent.getService(
                this, 2, new Intent(this, ReadingService.class)
                        .setAction(isPlaying ? ACTION_PAUSE : ACTION_PLAY),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        PendingIntent pClose = PendingIntent.getService(
                this, 3, new Intent(this, ReadingService.class).setAction(ACTION_CLOSE),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle("Lodi Book Reader")
                .setContentText(isPlaying ? "Reading..." : "Paused")
                .setContentIntent(pMain)
                .setOngoing(isPlaying)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setOnlyAlertOnce(true)
                .addAction(android.R.drawable.ic_media_rew, "Rewind", pRewind)
                .addAction(isPlaying ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
                        isPlaying ? "Pause" : "Play", pPlayPause)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Close", pClose)
                .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                        .setMediaSession(mediaSession.getSessionToken())
                        .setShowActionsInCompactView(0, 1, 2))
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel =
                    new NotificationChannel(CHANNEL_ID, "Lodi Playback",
                            NotificationManager.IMPORTANCE_LOW);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    @Override
    public void onDestroy() {
        if (mediaSession != null) mediaSession.release();
        if (ttsPlayer != null) ttsPlayer.release();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
