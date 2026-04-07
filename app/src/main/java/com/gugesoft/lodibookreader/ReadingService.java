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
import android.view.KeyEvent;

public class ReadingService extends Service {

    private static final String CHANNEL_ID = "LodiReadingChannel";
    private static final int NOTIF_ID = 101;

    public static final String ACTION_PLAY = "LODI_ACTION_PLAY";
    public static final String ACTION_PAUSE = "LODI_ACTION_PAUSE";
    public static final String ACTION_REWIND = "LODI_ACTION_REWIND";
    public static final String ACTION_FORWARD = "LODI_ACTION_FORWARD";
    public static final String ACTION_CLOSE = "LODI_ACTION_CLOSE";

    private MediaSessionCompat mediaSession;
    private boolean isPlaying = false;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();

        mediaSession = new MediaSessionCompat(this, "LodiReaderSession");
        mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS |
                MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);

        updatePlaybackState(PlaybackStateCompat.STATE_PAUSED);

        mediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override
            public void onPlay() {
                sendBroadcastToActivity(ACTION_PLAY);
            }

            @Override
            public void onPause() {
                sendBroadcastToActivity(ACTION_PAUSE);
            }

            @Override
            public void onSkipToNext() {
                sendBroadcastToActivity(ACTION_FORWARD);
            }

            @Override
            public void onSkipToPrevious() {
                sendBroadcastToActivity(ACTION_REWIND);
            }

            @Override
            public boolean onMediaButtonEvent(Intent mediaButtonEvent) {
                KeyEvent keyEvent = mediaButtonEvent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
                if (keyEvent != null && keyEvent.getAction() == KeyEvent.ACTION_DOWN) {
                    if (keyEvent.getKeyCode() == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || 
                        keyEvent.getKeyCode() == KeyEvent.KEYCODE_HEADSETHOOK) {
                        if (isPlaying) onPause();
                        else onPlay();
                        return true;
                    }
                }
                return super.onMediaButtonEvent(mediaButtonEvent);
            }
        });

        mediaSession.setActive(true);
        startForeground(NOTIF_ID, buildNotification());
    }

    private void updatePlaybackState(int state) {
        long actions = PlaybackStateCompat.ACTION_PLAY |
                PlaybackStateCompat.ACTION_PAUSE |
                PlaybackStateCompat.ACTION_PLAY_PAUSE |
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT |
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS |
                PlaybackStateCompat.ACTION_STOP;

        PlaybackStateCompat.Builder stateBuilder = new PlaybackStateCompat.Builder()
                .setActions(actions)
                .setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1.0f);
        
        mediaSession.setPlaybackState(stateBuilder.build());
    }

    private void sendBroadcastToActivity(String action) {
        Intent intent = new Intent(action);
        sendBroadcast(intent);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            boolean wasPlaying = isPlaying;

            if (ACTION_CLOSE.equals(action)) {
                stopForeground(true);
                stopSelf();
                return START_NOT_STICKY;
            }

            // This is called when activity updates the service state
            if (intent.hasExtra("IS_PLAYING")) {
                isPlaying = intent.getBooleanExtra("IS_PLAYING", false);
            }

            if (ACTION_PLAY.equals(action)) isPlaying = true;
            if (ACTION_PAUSE.equals(action)) isPlaying = false;

            updatePlaybackState(isPlaying ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED);
            
            // If the action came from notification buttons, we need to notify activity
            if (ACTION_PLAY.equals(action) || ACTION_PAUSE.equals(action) || 
                ACTION_REWIND.equals(action) || ACTION_FORWARD.equals(action)) {
                sendBroadcastToActivity(action);
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

        PendingIntent pRewind = PendingIntent.getService(this, 1, 
                new Intent(this, ReadingService.class).setAction(ACTION_REWIND),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        PendingIntent pPlayPause = PendingIntent.getService(this, 2, 
                new Intent(this, ReadingService.class).setAction(isPlaying ? ACTION_PAUSE : ACTION_PLAY),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        PendingIntent pForward = PendingIntent.getService(this, 4, 
                new Intent(this, ReadingService.class).setAction(ACTION_FORWARD),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        PendingIntent pClose = PendingIntent.getService(this, 3, 
                new Intent(this, ReadingService.class).setAction(ACTION_CLOSE),
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
                        isPlaying ? "Play/Pause" : "Play/Pause", pPlayPause)
                .addAction(android.R.drawable.ic_media_ff, "Forward", pForward)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Close", pClose)
                .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                        .setMediaSession(mediaSession.getSessionToken())
                        .setShowActionsInCompactView(0, 1, 2))
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Lodi Playback",
                            NotificationManager.IMPORTANCE_LOW);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    @Override
    public void onDestroy() {
        if (mediaSession != null) {
            mediaSession.setActive(false);
            mediaSession.release();
        }
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
