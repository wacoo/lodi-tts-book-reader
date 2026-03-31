package com.gugesoft.lodibookreader;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.text.Html;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class TTSPlayer {

    private Context context;
    private TextToSpeech tts;
    private List<String> sentences;
    private int currentSentenceIndex = 0;
    private boolean isPaused = false;

    private TextView tvBookText;

    public TTSPlayer(Context context, List<String> sentences, TextView tvBookText) {
        this.context = context;
        this.sentences = sentences != null ? sentences : new ArrayList<>();
        this.tvBookText = tvBookText;

        tts = new TextToSpeech(context, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.getDefault());
                tts.setSpeechRate(1.0f);
            }
        });
        updateTextView();
    }

    public void loadSentences(List<String> newSentences) {
        if (newSentences != null) {
            sentences.clear();
            sentences.addAll(newSentences);
        }
        currentSentenceIndex = 0;
        isPaused = false;
        updateTextView();
    }

    public void play() {
        if (currentSentenceIndex >= sentences.size()) {
            currentSentenceIndex = 0;
        }
        isPaused = false;
        speakNextSentence();
    }

    public void pause() {
        isPaused = true;
        tts.stop();
    }

    public void rewind() {
        if (currentSentenceIndex > 0) {
            currentSentenceIndex = Math.max(0, currentSentenceIndex - 1);
            updateTextView();
        }
        play();
    }

    private void speakNextSentence() {
        if (isPaused || currentSentenceIndex >= sentences.size()) return;

        String sentence = sentences.get(currentSentenceIndex);
        tts.speak(sentence, TextToSpeech.QUEUE_FLUSH, null, "SENTENCE_" + currentSentenceIndex);

        tts.setOnUtteranceProgressListener(new android.speech.tts.UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) {
                updateTextView();
            }

            @Override
            public void onDone(String utteranceId) {
                if (!isPaused) {
                    currentSentenceIndex++;
                    speakNextSentence();
                }
            }

            @Override
            public void onError(String utteranceId) {
                // Repeat the sentence on error
                speakNextSentence();
            }
        });
    }

    private void updateTextView() {
        if (tvBookText == null || sentences.isEmpty()) return;

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < sentences.size(); i++) {
            if (i == currentSentenceIndex) {
                sb.append("<b>").append(sentences.get(i)).append("</b> ");
            } else {
                sb.append(sentences.get(i)).append(" ");
            }
        }
        tvBookText.post(() -> tvBookText.setText(Html.fromHtml(sb.toString())));
    }

    public void stop() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
    }
}