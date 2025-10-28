package org.truetranslation.mybible.gui;

// A simple data object to hold all verse information for the GUI, mirroring the CLI's JSON structure.
public class GuiVerse {
    public int bookNumber;
    public String defaultFullBookName;
    public String defaultShortBookName;
    public String moduleFullBookName;
    public String moduleShortBookName;
    public int chapter;
    public int verse;
    public String rawText;
    public String moduleName;

    public GuiVerse(int bookNumber, String defaultFullBookName, String defaultShortBookName, String moduleFullBookName, String moduleShortBookName, int chapter, int verse, String rawText, String moduleName) {
        this.bookNumber = bookNumber;
        this.defaultFullBookName = defaultFullBookName;
        this.defaultShortBookName = defaultShortBookName;
        this.moduleFullBookName = moduleFullBookName;
        this.moduleShortBookName = moduleShortBookName;
        this.chapter = chapter;
        this.verse = verse;
        this.rawText = rawText;
        this.moduleName = moduleName;
    }
}
