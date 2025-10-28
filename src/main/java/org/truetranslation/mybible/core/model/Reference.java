package org.truetranslation.mybible.core.model;

public class Reference {
    private final int book;
    private final int chapter;
    private final int verse;
    private final String bookName;

    public Reference(int book, int chapter, int verse, String bookName) {
        this.book = book;
        this.chapter = chapter;
        this.verse = verse;
        this.bookName = bookName;
    }

    public int getBook() { return book; }
    public int getChapter() { return chapter; }
    public int getVerse() { return verse; }
    public String getBookName() { return bookName; }

    @Override
    public String toString() {
        return "Reference{" + "book=" + book + ", chapter=" + chapter + ", verse=" + verse + '}';
    }
}
