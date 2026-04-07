package com.gugesoft.lodibookreader;

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.util.Log;

public class VolumeController {
    private static final String TAG = "VolumeController";
    private final AudioManager audioManager;
    private final Context context;
    
    private int originalVolume = -1;
    private long lastAutoSetTime = 0;
    private int lastSetAutoVolume = -1;
    
    // 3 seconds cooldown to ignore system volume broadcasts after we've set the volume.
    private static final long AUTO_CHANGE_COOLDOWN_MS = 3000;

    public VolumeController(Context context) {
        this.context = context.getApplicationContext();
        audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        // Initial capture
        originalVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        Log.d(TAG, "Initialized. Baseline volume: " + originalVolume);
    }

    /**
     * Captures the current system volume as the baseline.
     */
    public synchronized void captureBaselineVolume() {
        int current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        
        // If we are currently in an automated change cooldown
        if (isAutoChanging()) {
            // If the volume matches what we last set programmatically, it's definitely an auto-change.
            if (current == lastSetAutoVolume) {
                Log.d(TAG, "Capture skipped: matches last auto-set volume (" + current + ")");
                return;
            }
            
            // If the volume is DIFFERENT from what we set but we're in cooldown,
            // it's a bit ambiguous. However, if we're fading, we really don't want to 
            // capture a lower volume as the new baseline unless it's a significant user action.
            // For now, we trust the cooldown to prevent accidental baseline corruption.
            Log.d(TAG, "Capture skipped: within automated change cooldown (" + current + ")");
            return;
        }
        
        originalVolume = current;
        Log.d(TAG, "Baseline updated to: " + originalVolume);
    }

    /**
     * Smoothly reduces volume based on the percentage of remaining fade time.
     */
    public synchronized void applyFade(long remainingMs, long totalFadeDurationMs) {
        if (originalVolume <= 0 || totalFadeDurationMs <= 0) return;
        
        // Calculate target volume: scale baseline volume by percentage of remaining fade time
        int target = (int) Math.ceil((double) originalVolume * remainingMs / totalFadeDurationMs);
        int current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);

        // Always update the cooldown and the "owned" volume level during a fade tick
        lastAutoSetTime = System.currentTimeMillis();
        lastSetAutoVolume = target;

        if (target < current) {
            Log.d(TAG, "Fading: setting volume to " + target + " (baseline: " + originalVolume + ")");
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0);
        }
    }

    /**
     * Instantly restores volume to the captured baseline.
     */
    public synchronized void restoreVolume() {
        if (originalVolume >= 0) {
            Log.d(TAG, "Restoring volume to baseline: " + originalVolume);
            lastAutoSetTime = System.currentTimeMillis();
            lastSetAutoVolume = originalVolume;
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, originalVolume, 0);
        } else {
            Log.w(TAG, "Restore failed: no baseline volume available");
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
            Log.e(TAG, "Error playing bell", e);
        }
    }

    public boolean isAutoChanging() {
        return (System.currentTimeMillis() - lastAutoSetTime < AUTO_CHANGE_COOLDOWN_MS);
    }
}