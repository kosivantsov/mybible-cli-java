package org.truetranslation.mybible.core.model;


// A simple data class representing a single Bible verse with its text.
public class Verse {
    private final int bookNumber;
    private final int chapter;
    private final int verse;
    private final String text;

    public Verse(int bookNumber, int chapter, int verse, String text) {
        this.bookNumber = bookNumber;
        this.chapter = chapter;
        this.verse = verse;
        this.text = text;
    }

    public int getBookNumber() {
        return bookNumber;
    }

    public int getChapter() {
        return chapter;
    }

    public int getVerse() {
        return verse;
    }

    public String getText() {
        return text;
    }

    @Override
    public String toString() {
        return "Verse{" + bookNumber + ":" + chapter + ":" + verse + " - '" + text.substring(0, Math.min(text.length(), 50)) + "...'}";
    }
}
