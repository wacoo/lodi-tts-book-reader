package com.gugesoft.lodibookreader;
import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import java.util.HashMap;
import java.util.Locale;

public class SystemTtsEngine {

    private TextToSpeech tts;
    private PlaybackController controller;
    private boolean isReady = false;

    public SystemTtsEngine(Context context, PlaybackController controller) {

        this.controller = controller;

        tts = new TextToSpeech(context, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.US);
                isReady = true;

                controller.start();
            }
        });

        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {

            @Override
            public void onStart(String utteranceId) {}

            @Override
            public void onDone(String utteranceId) {
                controller.onSentenceDone();
            }

            @Override
            public void onError(String utteranceId) {
                controller.onSentenceError();
            }
        });
    }

    public void speak(Sentence sentence) {
        if (!isReady) return;

        HashMap<String, String> params = new HashMap<>();
        params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID,
                String.valueOf(sentence.getId()));

        tts.speak(sentence.getText(),
                TextToSpeech.QUEUE_FLUSH,
                params);
    }

    public void stop() {
        tts.stop();
    }

    public void shutdown() {
        tts.shutdown();
    }
}