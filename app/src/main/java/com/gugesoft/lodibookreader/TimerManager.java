package com.gugesoft.lodibookreader;

import android.os.Handler;

public class TimerManager {

    private Handler handler = new Handler();

    private long totalTimeMs;
    private long remainingTimeMs;

    private boolean isRunning = false;
    private boolean isPausedForReset = false;

    private Runnable timerRunnable;
    private Runnable onFinishCallback;

    private VolumeController volumeController;

    public TimerManager(VolumeController volumeController, Runnable onFinishCallback) {
        this.volumeController = volumeController;
        this.onFinishCallback = onFinishCallback;
    }

    public void start(long durationMs) {
        stop();

        totalTimeMs = durationMs;
        remainingTimeMs = durationMs;

        // capture system volume
        volumeController.updateLastSystemVolume();

        isRunning = true;
        isPausedForReset = false;

        timerRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isRunning) return;

                if (!isPausedForReset) { // pause fade when reset
                    remainingTimeMs -= 1000;

                    if (remainingTimeMs < totalTimeMs * 0.2) {
                        volumeController.fadeDownStep();
                    }

                    if (remainingTimeMs <= 0) {
                        finish();
                        return;
                    }
                }

                handler.postDelayed(this, 1000);
            }
        };

        handler.post(timerRunnable);
    }

    private void finish() {
        isRunning = false;
        isPausedForReset = true;
        volumeController.restoreVolumeGradually();
        if (onFinishCallback != null) {
            onFinishCallback.run();
        }
    }

    // Called on shake
    public void reset() {
        if (!isRunning) return;

        isPausedForReset = true;

        // restore system volume gradually
        volumeController.restoreVolumeGradually();

        // play bell
        volumeController.playBell();

        // reset timer
        remainingTimeMs = totalTimeMs;

        // resume fade after 1 second
        handler.postDelayed(() -> isPausedForReset = false, 1000);
    }

    public void stop() {
        isRunning = false;
        handler.removeCallbacksAndMessages(null);
        isPausedForReset = true;
        volumeController.restoreVolumeGradually();
    }
}