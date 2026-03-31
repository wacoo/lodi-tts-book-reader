package com.gugesoft.lodibookreader;

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Handler;

public class VolumeController {

    private AudioManager audioManager;
    private int lastSystemVolume = -1; // previous system volume
    private Handler handler = new Handler();
    private Context context;

    public VolumeController(Context context) {
        this.context = context;
        audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    }

    // Capture the current system STREAM_MUSIC volume
    public void updateLastSystemVolume() {
        lastSystemVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
    }

    // Fade down by 1 step
    public void fadeDownStep() {
        int current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        if (current > 0) {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, current - 1, 0);
        }
    }

    // Gradually restore to previous system volume
    public void restoreVolumeGradually() {
        if (lastSystemVolume < 0) return;

        handler.removeCallbacksAndMessages(null); // stop previous fade

        final int current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);

        handler.post(new Runnable() {
            int vol = current;

            @Override
            public void run() {
                if (vol < lastSystemVolume) {
                    vol++;
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, vol, 0);
                    handler.postDelayed(this, 50);
                } else if (vol > lastSystemVolume) {
                    vol--;
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, vol, 0);
                    handler.postDelayed(this, 50);
                }
            }
        });
    }

    // Play bell from res/raw/bell.mp3
    public void playBell() {
        try {
            MediaPlayer mp = MediaPlayer.create(context, R.raw.bell);
            if (mp != null) {
                mp.start();
                mp.setOnCompletionListener(MediaPlayer::release);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}