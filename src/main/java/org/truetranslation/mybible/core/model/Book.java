package org.truetranslation.mybible.core.model;

import java.util.List;

public class Book {
    private final int bookNumber;
    private final String fullName;
    private final List<String> shortNames;

    public Book(int bookNumber, String fullName, List<String> shortNames) {
        this.bookNumber = bookNumber;
        this.fullName = fullName;
        this.shortNames = shortNames;
    }

    public int getBookNumber() {
        return bookNumber;
    }

    public String getFullName() {
        return fullName;
    }

    /**
     * Returns the primary (first) short name for the book.
     */
    public String getShortName() {
        return (shortNames != null && !shortNames.isEmpty()) ? shortNames.get(0) : "";
    }

    /**
     * Returns the complete list of all short names and abbreviations for the book.
     * This is the method that was missing.
     */
    public List<String> getShortNames() {
        return shortNames;
    }
}
