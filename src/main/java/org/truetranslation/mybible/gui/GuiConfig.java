package org.truetranslation.mybible.gui;

import com.formdev.flatlaf.intellijthemes.*;
import java.awt.Color;
import java.awt.Font;
import java.util.HashMap;
import java.util.Map;

public class GuiConfig {
    public String lookAndFeelClassName;
    public String formatString = "%A %c:%v %Y";
    public boolean useModuleAbbreviations = false;
    public Map<String, TextStyle> styles = new HashMap<>();
    public boolean showRawJson = false;
    public Color textAreaBackground = null;

    public GuiConfig() {
        this.lookAndFeelClassName = FlatArcIJTheme.class.getName();
        styles.put("infoText", new TextStyle("Monospaced", Font.PLAIN, 12, null, null, false, false));
        styles.put("defaultText", new TextStyle("Serif", Font.PLAIN, 10, null, null, false, false));
        styles.put("bookName", new TextStyle("Serif", Font.PLAIN, 10, Color.GRAY, null, false, false));
        styles.put("chapter", new TextStyle("Serif", Font.PLAIN, 10, Color.GRAY, null, false, false));
        styles.put("verse", new TextStyle("Serif", Font.PLAIN, 10, Color.GRAY, null, false, false));
        styles.put("verseText", new TextStyle("Serif", Font.PLAIN, 14, Color.BLACK, null, false, false));
        styles.put("moduleName", new TextStyle("Serif", Font.ITALIC, 10, Color.DARK_GRAY, null, false, false));
        styles.put("strongsNumber", new TextStyle("Serif", Font.PLAIN, 12, Color.BLUE, null, true, false));
        styles.put("wordsOfJesus", new TextStyle("Serif", Font.PLAIN, 14, new Color(200, 0, 0), null, false, false));
        styles.put("morphologyInfo", new TextStyle("Serif", Font.ITALIC, 12, new Color(0, 100, 0), null, true, false)); // Dark Green
        styles.put("alternativeVerseText", new TextStyle("Serif", Font.PLAIN, 12, Color.GRAY, null, false, false)); // Gray
    }
}
