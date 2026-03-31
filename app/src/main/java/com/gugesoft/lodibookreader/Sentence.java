package com.gugesoft.lodibookreader;

public class Sentence {

    private int id;
    private String text;

    public Sentence(int id, String text) {
        this.id = id;
        this.text = text;
    }

    public int getId() {
        return id;
    }

    public String getText() {
        return text;
    }
}