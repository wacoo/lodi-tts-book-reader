package com.gugesoft.lodibookreader;

import android.os.Handler;
import android.os.Looper;

public class LodiStepTimer {

    public interface TimerListener {
        void onTick(long remainingMs);
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private TimerListener listener;

    private long totalTimeMs, remainingTimeMs;
    private boolean isRunning = false;
    private long resetTimeMs = 60 * 1000; // Default 1 minute
    private final VolumeController volumeController;
    private final Runnable onFinish;

    // Fade duration
    private long fadeDurationMs = 10 * 1000;

    public LodiStepTimer(VolumeController vc, Runnable finish) {
        this.volumeController = vc;
        this.onFinish = finish;
    }

    public void setTimerListener(TimerListener listener) {
        this.listener = listener;
    }

    private final Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isRunning) return;

            remainingTimeMs -= 1000;

            if (listener != null) {
                listener.onTick(remainingTimeMs);
            }

            // Fade when remaining time is less than or equal to fade duration
            if (remainingTimeMs <= fadeDurationMs) {
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

        boolean wasInFade = remainingTimeMs <= fadeDurationMs;

        // Reset timer to the configured reset time (the full duration set in settings)
        remainingTimeMs = resetTimeMs;

        if (wasInFade) {
            volumeController.restoreVolumeGradually();
            volumeController.playBell();
        }
    }

    public void stop() {
        isRunning = false;
        handler.removeCallbacksAndMessages(null);
        volumeController.restoreVolumeGradually();
    }

    public boolean isRunning() {
        return isRunning;
    }

    public long getRemainingTimeMs() {
        return remainingTimeMs;
    }

    public void setResetTimeMs(long resetTimeMs) {
        this.resetTimeMs = resetTimeMs;
    }

    public void setFadeStartMs(long fadeDurationMs) {
        this.fadeDurationMs = fadeDurationMs;
    }
}