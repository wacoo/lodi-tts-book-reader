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

    /**
     * Captures the current system volume as the baseline.
     * Only captures if we aren't currently performing an automated fade.
     */
    public void captureBaselineVolume() {
        if (isAutoChanging) return;
        originalVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        Log.d("VolumeController", "Baseline captured: " + originalVolume);
    }

    /**
     * Smoothly reduces volume based on the percentage of remaining fade time.
     */
    public void applyFade(long remainingMs, long totalFadeDurationMs) {
        if (originalVolume <= 0 || totalFadeDurationMs <= 0) return;
        
        // Calculate target volume: scale baseline volume by percentage of remaining fade time
        int target = (int) Math.ceil((double) originalVolume * remainingMs / totalFadeDurationMs);
        
        int current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        // Only lower the volume, never increase it during fade
        if (target < current) {
            isAutoChanging = true;
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0);
            // Briefly delay resetting isAutoChanging to allow system events to settle
            handler.postDelayed(() -> isAutoChanging = false, 200);
        }
    }

    /**
     * Instantly restores volume to the captured baseline.
     */
    public void restoreVolume() {
        if (originalVolume >= 0) {
            isAutoChanging = true;
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, originalVolume, 0);
            handler.postDelayed(() -> isAutoChanging = false, 200);
            Log.d("VolumeController", "Volume restored to baseline: " + originalVolume);
        }
    }

    public void playBell() {
        try {
            MediaPlayer mp = MediaPlayer.create(context, R.raw.bell);
            if (mp != null) {
                mp.start();
                mp.setOnCompletionListener(MediaPlayer::release);
            }
        } catch (Exception e) { 
            Log.e("VolumeController", "Error playing bell", e);
        }
    }

    public boolean isAutoChanging() {
        return isAutoChanging;
    }
}