package com.gugesoft.lodibookreader;

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;

public class VolumeController {
    private final AudioManager audioManager;
    private int targetVolume = -1;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Context context;

    public VolumeController(Context context) {
        this.context = context.getApplicationContext();
        audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    }

    public void updateLastSystemVolume() {
        targetVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
    }

    public void fadeDownStep() {
        int current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        if (current > 0) {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, current - 1, 0);
        }
    }

    public void restoreVolumeGradually() {
        if (targetVolume < 0) return;
        handler.removeCallbacksAndMessages(null);
        handler.post(new Runnable() {
            @Override
            public void run() {
                int current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                if (current < targetVolume) {
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, current + 1, 0);
                    handler.postDelayed(this, 150);
                }
            }
        });
    }

    public void playBell() {
        try {
            MediaPlayer mp = MediaPlayer.create(context, R.raw.bell);
            if (mp != null) {
                mp.start();
                mp.setOnCompletionListener(MediaPlayer::release);
            }
        } catch (Exception e) { e.printStackTrace(); }
    }
}