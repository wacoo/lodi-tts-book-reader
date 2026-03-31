package com.gugesoft.lodibookreader;

import java.util.List;

public class SentenceManager {

    private List<Sentence> sentences;
    private int currentIndex = 0;

    public SentenceManager(List<Sentence> sentences) {
        this.sentences = sentences;
    }

    public Sentence getCurrent() {
        if (currentIndex < sentences.size()) {
            return sentences.get(currentIndex);
        }
        return null;
    }

    public Sentence next() {
        currentIndex++;
        return getCurrent();
    }

    public Sentence retry() {
        return getCurrent();
    }

    public boolean hasNext() {
        return currentIndex < sentences.size() - 1;
    }

    public void reset() {
        currentIndex = 0;
    }
}