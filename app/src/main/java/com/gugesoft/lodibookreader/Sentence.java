package com.gugesoft.lodibookreader;

public class Sentence {
    public int id;
    public String text;
    public String link;

    public Sentence(int id, String text, String link) {
        this.id = id;
        this.text = text;
        this.link = link;
    }

    public int getId() { return id; }
    public String getText() { return text; }
    public String getLink() { return link; }
}