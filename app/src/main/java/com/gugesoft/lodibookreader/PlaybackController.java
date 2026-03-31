package com.gugesoft.lodibookreader;

import android.app.Activity;

public class PlaybackController {

    private SentenceManager manager;
    private SystemTtsEngine tts;
    private Activity activity;

    private int retryCount = 0;
    private static final int MAX_RETRY = 3;

    public PlaybackController(Activity activity, SentenceManager manager) {
        this.activity = activity;
        this.manager = manager;
    }

    public void setTts(SystemTtsEngine tts) {
        this.tts = tts;
    }

    public void start() {
        Sentence sentence = manager.getCurrent();
        if (sentence != null) {
            tts.speak(sentence);
        }
    }

    public void onSentenceDone() {
        activity.runOnUiThread(() -> {
            retryCount = 0;

            if (manager.hasNext()) {
                Sentence next = manager.next();
                tts.speak(next);
            }
        });
    }

    public void onSentenceError() {
        activity.runOnUiThread(() -> {
            retryCount++;

            if (retryCount < MAX_RETRY) {
                Sentence retry = manager.retry();
                tts.speak(retry);
            } else {
                retryCount = 0;

                if (manager.hasNext()) {
                    Sentence next = manager.next();
                    tts.speak(next);
                }
            }
        });
    }
}