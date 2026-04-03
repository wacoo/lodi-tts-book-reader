package com.gugesoft.lodibookreader;

import android.os.Handler;
import android.os.Looper;

public class LodiStepTimer {

    private final Handler handler = new Handler(Looper.getMainLooper());

    private long totalTimeMs, remainingTimeMs;
    private boolean isRunning = false;
    private long resetTimeMs = 20 * 1000; // 20 seconds
    private final VolumeController volumeController;
    private final Runnable onFinish;

    // Fade starts at last 10 seconds
    private static final long FADE_START_MS = 10 * 1000;

    // 🔹 Configurable shake intensity (default value)
    private float shakeIntensity = 12.0f;

    public LodiStepTimer(VolumeController vc, Runnable finish) {
        this.volumeController = vc;
        this.onFinish = finish;
    }

    private final Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isRunning) return;

            remainingTimeMs -= 1000;

            // Fade when last 10 seconds remain
            if (remainingTimeMs <= FADE_START_MS) {
                volumeController.fadeDownStep();
            }

            if (remainingTimeMs <= 0) {
                isRunning = false;
                onFinish.run();
                return;
            }

            handler.postDelayed(this, 1000);
        }
    };

    public void start(long durationMs) {
        stop();

        this.totalTimeMs = durationMs;
        this.remainingTimeMs = durationMs;
        this.isRunning = true;

        volumeController.updateLastSystemVolume();

        handler.post(timerRunnable);
    }

    public void handleShake() {
        if (!isRunning) return;

        // 🔹 Always reset timer
        remainingTimeMs = resetTimeMs;

        // 🔹 Only restore volume and play bell if in fade phase
        //if (remainingTimeMs <= FADE_START_MS) {
            volumeController.restoreVolumeGradually();
            volumeController.playBell();
        //}
    }

    public void stop() {
        isRunning = false;
        handler.removeCallbacksAndMessages(null);
        volumeController.restoreVolumeGradually();
    }

    public boolean isRunning() {
        return isRunning;
    }

    // 🔹 Getter and Setter for shake intensity
    public float getShakeIntensity() {
        return shakeIntensity;
    }

    public void setShakeIntensity(float shakeIntensity) {
        this.shakeIntensity = shakeIntensity;
    }

    public void setResetTimeMs(long resetTimeMs) {
        this.resetTimeMs = resetTimeMs;
    }
}