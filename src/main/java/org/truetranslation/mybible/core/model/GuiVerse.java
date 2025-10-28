package org.truetranslation.mybible.core.model;

// A simple POJO representing the data structure for a single verse
public class GuiVerse {
    private final int bookNumber;
    private final String defaultFullBookName;
    private final String defaultShortBookName;
    private final String moduleFullBookName;
    private final String moduleShortBookName;
    private final int chapter;
    private final int verse;
    private final String rawVerseText;
    private final String moduleName;

    public GuiVerse(int bookNumber, String defaultFullBookName, String defaultShortBookName,
                    String moduleFullBookName, String moduleShortBookName, int chapter,
                    int verse, String rawVerseText, String moduleName) {
        this.bookNumber = bookNumber;
        this.defaultFullBookName = defaultFullBookName;
        this.defaultShortBookName = defaultShortBookName;
        this.moduleFullBookName = moduleFullBookName;
        this.moduleShortBookName = moduleShortBookName;
        this.chapter = chapter;
        this.verse = verse;
        this.rawVerseText = rawVerseText;
        this.moduleName = moduleName;
    }
}