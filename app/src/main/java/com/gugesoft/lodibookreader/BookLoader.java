package com.gugesoft.lodibookreader;

import android.content.Context;
import android.net.Uri;
import android.text.Html;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import nl.siegmann.epublib.domain.Book;
import nl.siegmann.epublib.domain.SpineReference;
import nl.siegmann.epublib.epub.EpubReader;

public class BookLoader {

    public List<Sentence> load(Context context, Uri uri) {
        String mimeType = context.getContentResolver().getType(uri);

        try (InputStream is = context.getContentResolver().openInputStream(uri)) {
            if ("text/plain".equals(mimeType) || (uri.getPath() != null && uri.getPath().endsWith(".txt"))) {
                return loadTxt(is);
            } else {
                return loadEpub(is);
            }
        } catch (Exception e) {
            Log.e("BookLoader", "General load error", e);
            return new ArrayList<>();
        }
    }

    private List<Sentence> loadEpub(InputStream inputStream) {
        List<Sentence> sentences = new ArrayList<>();
        try {
            EpubReader reader = new EpubReader();
            Book book = reader.readEpub(inputStream);

            StringBuilder fullText = new StringBuilder();
            for (SpineReference spine : book.getSpine().getSpineReferences()) {
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(spine.getResource().getInputStream()))) {
                    String line;
                    while ((line = br.readLine()) != null) fullText.append(line);
                }
            }

            String cleanText = Html.fromHtml(fullText.toString(), Html.FROM_HTML_MODE_LEGACY).toString();
            sentences.addAll(splitIntoSentences(cleanText));

        } catch (Exception e) {
            Log.e("BookLoader", "EPUB error", e);
        }
        return sentences;
    }

    private List<Sentence> loadTxt(InputStream inputStream) {
        List<Sentence> sentences = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(inputStream))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append(" ");
            sentences.addAll(splitIntoSentences(sb.toString()));
        } catch (Exception e) {
            Log.e("BookLoader", "TXT error", e);
        }
        return sentences;
    }

    private List<Sentence> splitIntoSentences(String text) {
        List<Sentence> sentences = new ArrayList<>();
        if (text == null || text.isEmpty()) return sentences;

        BreakIterator iterator = BreakIterator.getSentenceInstance(Locale.US);
        iterator.setText(text);

        int start = iterator.first();
        int id = 0;

        for (int end = iterator.next(); end != BreakIterator.DONE; start = end, end = iterator.next()) {
            String sentenceText = text.substring(start, end).trim();
            if (!sentenceText.isEmpty()) {

                String link = null;
                if (sentenceText.contains("http")) {
                    try {
                        link = sentenceText.substring(sentenceText.indexOf("http")).split(" ")[0];
                    } catch (Exception ignored) {}
                }

                sentences.add(new Sentence(id++, sentenceText, link));
            }
        }
        return sentences;
    }
}