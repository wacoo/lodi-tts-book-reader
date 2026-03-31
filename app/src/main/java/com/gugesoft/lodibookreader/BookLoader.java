package com.gugesoft.lodibookreader;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import nl.siegmann.epublib.domain.Book;
import nl.siegmann.epublib.domain.SpineReference;
import nl.siegmann.epublib.epub.EpubReader;


public class BookLoader {

    private Context context;

    public BookLoader(Context context) {
        this.context = context;
    }

    // ---------- EPUB ----------
    public List<String> loadEpub(InputStream inputStream) {
        List<String> sentences = new ArrayList<>();
        try {
            EpubReader reader = new EpubReader();
            Book book = reader.readEpub(inputStream);
            for (SpineReference ref : book.getSpine().getSpineReferences()) {
                InputStream is = ref.getResource().getInputStream();
                BufferedReader br = new BufferedReader(new InputStreamReader(is));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line).append(" ");
                }
                br.close();
                String text = sb.toString();
                splitIntoSentences(text, sentences);
            }
        } catch (Exception e) {
            Log.e("BookLoader", "Error loading EPUB", e);
        }
        return sentences;
    }

    // ---------- TXT ----------
    public List<String> loadTxt(InputStream inputStream) {
        List<String> sentences = new ArrayList<>();
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(inputStream));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append(" ");
            }
            br.close();
            splitIntoSentences(sb.toString(), sentences);
        } catch (Exception e) {
            Log.e("BookLoader", "Error loading TXT", e);
        }
        return sentences;
    }

    // ---------- Helper ----------
    private void splitIntoSentences(String text, List<String> sentences) {
        if (text == null || text.isEmpty()) return;
        String[] parts = text.split("(?<=[.!?])\\s+");
        for (String s : parts) {
            if (!s.trim().isEmpty()) sentences.add(s.trim());
        }
    }
}