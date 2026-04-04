package com.gugesoft.lodibookreader;

public class BookItem {
    public String uri;
    public String title;
    public String author;
    public String coverUri;
    public int lastSentenceIndex;

    // Full constructor - this is the one we must use now
    public BookItem(String uri, String title, String author, String coverUri, int lastSentenceIndex) {
        this.uri = uri;
        this.title = (title != null && !title.isEmpty()) ? title : "Unknown Book";
        this.author = (author != null) ? author : "";
        this.coverUri = coverUri;
        this.lastSentenceIndex = lastSentenceIndex;
    }

    // Keep this old constructor only for safety (but we won't use it much)
    public BookItem(String uri, String title, int lastIndex) {
        this(uri, title, "", null, lastIndex);
    }
}