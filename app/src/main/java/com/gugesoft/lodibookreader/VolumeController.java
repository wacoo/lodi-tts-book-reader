package com.gugesoft.lodibookreader;

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

public class VolumeController {
    private final AudioManager audioManager;
    private int originalVolume = -1;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Context context;
    private boolean isAutoChanging = false;

    public VolumeController(Context context) {
        this.context = context.getApplicationContext();
        audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    }

    public void updateLastSystemVolume() {
        // If we are currently fading or restoring, don't overwrite the original volume
        // because the current system volume is not the user's intended "base" volume.
        if (isAutoChanging && originalVolume != -1) {
            return;
        }
        originalVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
    }

    public void fadeDownStep() {
        isAutoChanging = true;
        int current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        if (current > 0) {
            isAutoChanging = true;
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, current - 1, 0);
            isAutoChanging = false;
        }
    }

    public void restoreVolumeGradually() {
        if (originalVolume < 0) return;
        isAutoChanging = true;
        handler.removeCallbacksAndMessages(null);
        handler.post(new Runnable() {
            @Override
            public void run() {
                int current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                if (current < originalVolume) {
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, current + 1, 0);
                    handler.postDelayed(this, 100);
                } else {
                    // force correction to baseline
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, originalVolume, 0);
                    isAutoChanging = false;
                }
            }
        });
    }


    public void restoreVolumeImmediately() {
        if (originalVolume >= 0) {
            handler.removeCallbacksAndMessages(null);

            isAutoChanging = true; // ✅ prevent observer overwrite

            int current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            Log.d("VolumeController", "Restoring from " + current + " to " + originalVolume);

            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, originalVolume, 0);

            isAutoChanging = false;
        }
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
    public void captureBaselineVolume() {
        if (isAutoChanging) return; // ✅ ignore app-triggered changes
        originalVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
    }
    public boolean isAutoChanging() {
        return isAutoChanging;
    }

}