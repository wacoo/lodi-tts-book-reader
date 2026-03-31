package com.gugesoft.lodibookreader;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SentenceSplitter {

    public static List<Sentence> split(String text) {
        List<Sentence> sentences = new ArrayList<>();

        BreakIterator iterator = BreakIterator.getSentenceInstance(Locale.US);
        iterator.setText(text);

        int start = iterator.first();
        int id = 0;

        for (int end = iterator.next();
             end != BreakIterator.DONE;
             start = end, end = iterator.next()) {

            String sentence = text.substring(start, end).trim();

            if (!sentence.isEmpty()) {
                sentences.add(new Sentence(id++, sentence));
            }
        }

        return sentences;
    }
}