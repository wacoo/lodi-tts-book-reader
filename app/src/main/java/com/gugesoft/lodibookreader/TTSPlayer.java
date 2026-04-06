package com.gugesoft.lodibookreader;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import java.util.List;
import java.util.Locale;

public class TTSPlayer {
    private TextToSpeech tts;
    private List<Sentence> sentences;
    private int currentIndex = 0;
    private boolean isReady = false;
    private boolean isPlaying = false;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private OnTTSListener listener;
    private PowerManager.WakeLock wakeLock;

    public interface OnTTSListener {
        void onSentenceChanged(int index);
        void onFinished();
    }

    public TTSPlayer(Context context, OnTTSListener listener) {
        this.listener = listener;
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "LodiReader:WakeLock");

        tts = new TextToSpeech(context.getApplicationContext(), status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.US);
                isReady = true;
            }
        });

        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) {
                mainHandler.post(() -> { if (listener != null) listener.onSentenceChanged(currentIndex); });
            }
            @Override
            public void onDone(String utteranceId) {
                if (!isPlaying) return;
                currentIndex++;
                mainHandler.post(() -> playNext());
            }
            @Override
            public void onError(String utteranceId) { isPlaying = false; }
        });
    }

    public boolean isPlaying() {
        return isPlaying;
    }

    public void loadSentences(List<Sentence> sentences) {
        this.sentences = sentences;
        this.currentIndex = 0;
    }

    public void setCurrentIndex(int index) {
        this.currentIndex = index;
    }
    public int getCurrentIndex() { return currentIndex; }

    public void play() {
        if (!isReady || sentences == null || sentences.isEmpty()) return;
        isPlaying = true;
        if (!wakeLock.isHeld()) wakeLock.acquire(60 * 60 * 1000L);
        speak(sentences.get(currentIndex));
    }

    public void playFrom(int index) {
        this.currentIndex = Math.max(0, Math.min(index, sentences.size() - 1));
        play();
    }

    private void playNext() {
        if (currentIndex < sentences.size()) {
            speak(sentences.get(currentIndex));
        } else {
            stop();
            if (listener != null) listener.onFinished();
        }
    }

    private void speak(Sentence sentence) {
        if (!isReady || sentence == null) return;
        Bundle params = new Bundle();
        params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, String.valueOf(sentence.id));
        tts.speak(sentence.text, TextToSpeech.QUEUE_FLUSH, params, String.valueOf(sentence.id));
    }

    public void pause() {
        isPlaying = false;
        tts.stop();
        if (wakeLock.isHeld()) wakeLock.release();
    }

    public void stop() {
        pause();
        currentIndex = 0;
    }

    public void release() {
        stop();
        if (tts != null) tts.shutdown();
    }
}