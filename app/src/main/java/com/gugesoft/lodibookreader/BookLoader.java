package com.gugesoft.lodibookreader;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.text.Html;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import nl.siegmann.epublib.domain.Book;
import nl.siegmann.epublib.domain.Metadata;
import nl.siegmann.epublib.domain.Resource;
import nl.siegmann.epublib.domain.SpineReference;
import nl.siegmann.epublib.epub.EpubReader;

public class BookLoader {

    public static class BookMetadata {
        public final String title;
        public final String author;
        public final String coverUri;   // file:// URI to cached cover
        public final List<Sentence> sentences;

        public BookMetadata(String title, String author, String coverUri, List<Sentence> sentences) {
            this.title = title != null && !title.isEmpty() ? title : "Unknown Book";
            this.author = author != null ? author : "";
            this.coverUri = coverUri;
            this.sentences = sentences != null ? sentences : new ArrayList<>();
        }
    }

    // ==================== PUBLIC METHOD ====================
    public BookMetadata loadBookWithMetadata(Context context, Uri uri) {
        String mimeType = context.getContentResolver().getType(uri);

        try (InputStream is = context.getContentResolver().openInputStream(uri)) {
            if (is == null) {
                return new BookMetadata("Error Opening File", "", null, new ArrayList<>());
            }

            if ("text/plain".equals(mimeType) ||
                    (uri.getPath() != null && uri.getPath().toLowerCase().endsWith(".txt"))) {

                List<Sentence> sentences = loadTxt(is);        // ← This line was causing error
                return new BookMetadata("TXT Book", "", null, sentences);

            } else {
                return loadEpubWithMetadata(context, is);
            }
        } catch (Exception e) {
            Log.e("BookLoader", "General load error", e);
            return new BookMetadata("Error Loading Book", "", null, new ArrayList<>());
        }
    }

// ==================== PRIVATE METHODS ====================

    // This method was probably missing or had wrong signature
    private List<Sentence> loadTxt(InputStream inputStream) {
        List<Sentence> sentences = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(inputStream))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append(" ");
            }
            sentences.addAll(splitIntoSentences(sb.toString()));
        } catch (Exception e) {
            Log.e("BookLoader", "TXT load error", e);
        }
        return sentences;
    }

    // Your improved EPUB loader (with better cover detection)
    private BookMetadata loadEpubWithMetadata(Context context, InputStream inputStream) {
        List<Sentence> sentences = new ArrayList<>();
        String title = "Unknown Book";
        String author = "";
        String coverUri = null;

        try {
            EpubReader reader = new EpubReader();
            Book book = reader.readEpub(inputStream);

            // Title
            if (!book.getMetadata().getTitles().isEmpty()) {
                title = book.getMetadata().getTitles().get(0);
            }

            // Author
            if (!book.getMetadata().getAuthors().isEmpty()) {
                author = book.getMetadata().getAuthors().get(0).toString();
            }

            // Cover detection
            Resource coverResource = book.getCoverImage();

            if (coverResource == null) {
                for (Resource res : book.getResources().getAll()) {
                    if (res.getHref() == null) continue;
                    String href = res.getHref().toLowerCase();

                    if (href.contains("cover") || href.contains("front")) {
                        if (href.endsWith(".jpg") || href.endsWith(".png") || href.endsWith(".jpeg")) {
                            if (res.getSize() > 5000) {   // avoid tiny images
                                coverResource = res;
                                break;
                            }
                        }
                    }
                }
            }

            // Save cover
            if (coverResource != null) {
                try (InputStream coverIs = coverResource.getInputStream()) {
                    Bitmap bitmap = BitmapFactory.decodeStream(coverIs);
                    if (bitmap != null) {
                        coverUri = saveCoverToCache(context, bitmap, title);
                    }
                } catch (Exception ignored) {}
            }

            // Extract text
            StringBuilder fullText = new StringBuilder();
            for (SpineReference spine : book.getSpine().getSpineReferences()) {
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(spine.getResource().getInputStream()))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        fullText.append(line).append(" ");
                    }
                }
            }

            String html = fullText.toString()
                    .replaceAll("(?s)<style.*?>.*?</style>", "")
                    .replaceAll("(?s)<script.*?>.*?</script>", "");

            CharSequence spanned = Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY);
            sentences.addAll(splitIntoSentences(spanned.toString()));

        } catch (Exception e) {
            Log.e("BookLoader", "EPUB error", e);
        }

        return new BookMetadata(title, author, coverUri, sentences);
    }

    private List<Sentence> splitIntoSentences(String text) {
        List<Sentence> sentences = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) return sentences;

        BreakIterator iterator = BreakIterator.getSentenceInstance(Locale.US);
        iterator.setText(text);

        int id = 0;
        for (int start = iterator.first(), end = iterator.next();
             end != BreakIterator.DONE;
             start = end, end = iterator.next()) {

            String sentenceText = text.substring(start, end).trim();
            if (!sentenceText.isEmpty()) {
                sentences.add(new Sentence(id++, sentenceText, extractLink(sentenceText)));
            }
        }
        return sentences;
    }

    private String extractLink(String text) {
        if (text.contains("http")) {
            try {
                return text.substring(text.indexOf("http")).split("\\s+")[0];
            } catch (Exception ignored) {}
        }
        return null;
    }

    // Save cover image to app's internal cache and return file URI
    private String saveCoverToCache(Context context, Bitmap bitmap, String bookTitle) {
        try {
            File cacheDir = new File(context.getCacheDir(), "book_covers");
            if (!cacheDir.exists()) {
                cacheDir.mkdirs();
            }

            String safeName = bookTitle.replaceAll("[^a-zA-Z0-9]", "_") + "_" + System.currentTimeMillis() + ".jpg";
            File coverFile = new File(cacheDir, safeName);

            try (FileOutputStream out = new FileOutputStream(coverFile)) {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out);
            }

            return Uri.fromFile(coverFile).toString();
        } catch (Exception e) {
            Log.e("BookLoader", "Failed to save cover", e);
            return null;
        }
    }
}