package org.truetranslation.mybible.core.model;
import java.util.List;

// A simple POJO representing the data structure for a single verse
public class GuiVerse {
    public int bookNumber;
    public String defaultFullBookName;
    public String defaultShortBookName;
    public String moduleFullBookName;
    public String moduleShortBookName;
    public List<String> allBookNames;
    public int chapter;
    public int verse;
    public String rawVerseText;
    public String moduleName;

    public GuiVerse(int bookNumber,
                    String defaultFullBookName,
                    String defaultShortBookName,
                    String moduleFullBookName,
                    String moduleShortBookName,
                    List<String> allBookNames,
                    int chapter,
                    int verse,
                    String rawVerseText,
                    String moduleName) {
        this.bookNumber = bookNumber;
        this.defaultFullBookName = defaultFullBookName;
        this.defaultShortBookName = defaultShortBookName;
        this.moduleFullBookName = moduleFullBookName;
        this.moduleShortBookName = moduleShortBookName;
        this.allBookNames = allBookNames;
        this.chapter = chapter;
        this.verse = verse;
        this.rawVerseText = rawVerseText;
        this.moduleName = moduleName;
    }
}