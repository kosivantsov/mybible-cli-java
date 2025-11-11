package org.truetranslation.mybible.gui;

import javax.swing.text.BadLocationException;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyleContext;
import javax.swing.text.StyledDocument;
import java.awt.Font;
import java.util.Arrays;
import java.util.List;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.truetranslation.mybible.core.model.GuiVerse;

public class GuiTextFormatter {

    private final GuiConfig config;
    private final StyleContext styleContext;

    public GuiTextFormatter(GuiConfig config) {
        this.config = config;
        this.styleContext = new StyleContext();
    }

    public void format(List<GuiVerse> verses, StyledDocument doc) {
        try {
            doc.remove(0, doc.getLength());

            for (GuiVerse verse : verses) {
                String processedFormat = config.formatString.replace("\\t", "\t");

                Pattern pattern = Pattern.compile("(?s)%(.)|(\\\\n)");
                Matcher matcher = pattern.matcher(processedFormat);
                int lastEnd = 0;

                while (matcher.find()) {
                    if (matcher.start() > lastEnd) {
                        appendSimpleText(doc, processedFormat.substring(lastEnd, matcher.start()), "defaultText");
                    }

                    if (matcher.group(1) != null) {
                        String specifier = matcher.group(1);
                        switch (specifier) {
                            case "a": appendSimpleText(doc, verse.defaultShortBookName, "bookName"); break;
                            case "f": appendSimpleText(doc, verse.defaultFullBookName, "bookName"); break;
                            case "A": appendSimpleText(doc, verse.moduleShortBookName, "bookName"); break;
                            case "F": appendSimpleText(doc, verse.moduleFullBookName, "bookName"); break;
                            case "b": appendSimpleText(doc, String.valueOf(verse.bookNumber), "bookName"); break;
                            case "c": appendSimpleText(doc, String.valueOf(verse.chapter), "chapter"); break;
                            case "v": appendSimpleText(doc, String.valueOf(verse.verse), "verse"); break;
                            case "m": appendSimpleText(doc, verse.moduleName, "moduleName"); break;
                            
                            case "T": appendSimpleText(doc, verse.rawVerseText, "infoText"); break;
                            case "t": appendPlainText(doc, verse.rawVerseText, true); break;
                            case "z": appendPlainText(doc, verse.rawVerseText, false); break;
                            case "X": appendStyledText(doc, verse.rawVerseText, true, true); break;
                            case "Y": appendStyledText(doc, verse.rawVerseText, false, true); break;
                            case "Z": appendStyledText(doc, verse.rawVerseText, false, false); break;
                                
                            default:
                                appendSimpleText(doc, "%" + specifier, "defaultText");
                                break;
                        }
                    } else if (matcher.group(2) != null) {
                        doc.insertString(doc.getLength(), "\n", createStyleFromConfig("defaultText"));
                    }
                    lastEnd = matcher.end();
                }

                if (lastEnd < processedFormat.length()) {
                    appendSimpleText(doc, processedFormat.substring(lastEnd), "defaultText");
                }
                doc.insertString(doc.getLength(), "\n", createStyleFromConfig("defaultText"));
            }
        } catch (BadLocationException e) {
            e.printStackTrace();
        }
    }
    
    private void appendSimpleText(StyledDocument doc, String text, String styleKey) throws BadLocationException {
        if (text == null || text.isEmpty()) return;
        Style style = createStyleFromConfig(styleKey);
        doc.insertString(doc.getLength(), text, style);
    }
    
    private void appendPlainText(StyledDocument doc, String rawVerseText, boolean multiline) throws BadLocationException {
        String temp = rawVerseText
            .replaceAll("\\[\\d+\\]", "")
            .replaceAll("(?i)<f>.*?</f>|</?t>|~|@|@[^ ]+? ", " ")
            .replaceAll("(?i)<[SGH]>\\s*\\d+\\s*</[SGH]>", " ")
            .replaceAll("(?i)<m>.*?</m>", " ")
            .replaceAll("(?i)<n>.*?</n>", " ")
            .replaceAll("(?i)<h>.*?</h>", " ")
            .replaceAll("<br/>", " ");
        
        if (multiline) {
            if (temp.trim().startsWith("<pb/>")) {
                temp = temp.replaceFirst("<pb/>", "").trim();
            }
            temp = temp.replace("<pb/>", "\n");
        } else {
            temp = temp.replace("<pb/>", " ");
        }

        temp = temp.replaceAll("<[^>]+>", " ");

        String cleaned = Arrays.stream(temp.split("\n"))
                .map(line -> line.trim().replaceAll("\\s+", " "))
                .collect(Collectors.joining(multiline ? "\n" : " "));

        appendSimpleText(doc, cleaned, "verseText");
    }

    private void appendStyledText(StyledDocument doc, String rawVerseText, boolean withStrongs, boolean multiline) throws BadLocationException {
        String tempText = rawVerseText
                .replaceAll("\\[\\d+\\]", "")
                .replaceAll("(?i)<f>.*?</f>|(?i)<h>.*?</h>|<br/>|</?t>|~|@|@[^ ]+? ", " ");

        if (multiline) {
            if (tempText.trim().startsWith("<pb/>")) {
                tempText = tempText.replaceFirst("<pb/>", " ");
            }
            tempText = tempText.replace("<pb/>", "\n");
        } else {
            tempText = tempText.replace("<pb/>", " ");
        }
        
        tempText = Arrays.stream(tempText.split("\n"))
                .map(line -> line.trim().replaceAll("\\s+", " "))
                .collect(Collectors.joining(multiline ? "\n" : " "));

        if (!withStrongs) {
            // For %Y and %Z, strip out Strong's and morphology
            tempText = tempText.replaceAll("(?i)<[SGH]>.*?</[SGH]>", "");
            tempText = tempText.replaceAll("(?i)<m>.*?</m>", "");
        }

        Pattern pattern = Pattern.compile("(?i)(</?(?:J|E|I|N|S|M)>)|([^<]+)");
        Matcher matcher = pattern.matcher(tempText);
        
        Stack<Style> styleStack = new Stack<>();
        // Use default 'verseText' as the base style
        styleStack.push(createStyleFromConfig("verseText"));

        while (matcher.find()) {
            String tagToken = matcher.group(1);
            String textToken = matcher.group(2);

            if (tagToken != null) {
                boolean isClosing = tagToken.startsWith("</");
                String tagName = tagToken.replaceAll("[<>/]", "").toUpperCase();

                if (isClosing) {
                    if (tagName.equals("S")) {
                        doc.insertString(doc.getLength(), "}", styleStack.peek());
                    }
                    if (tagName.equals("M")) {
                        doc.insertString(doc.getLength(), "}", styleStack.peek());
                    }
                    if (styleStack.size() > 1) {
                        styleStack.pop();
                    }
                } else {
                    Style current = styleStack.peek();
                    Style newStyle = doc.addStyle(null, current);
                    
                    switch (tagName) {
                        case "J": newStyle = createStyleFromConfig("wordsOfJesus"); break;
                        case "E": StyleConstants.setBold(newStyle, true); break;
                        case "I": StyleConstants.setItalic(newStyle, true); break;
                        case "N": newStyle = createStyleFromConfig("alternativeVerseText"); break;
                        case "M":
                            newStyle = createStyleFromConfig("morphologyInfo");
                            doc.insertString(doc.getLength(), "{", newStyle);
                            break;
                        case "S":
                            newStyle = createStyleFromConfig("strongsNumber");
                            doc.insertString(doc.getLength(), "{", newStyle);
                            break;
                    }
                    styleStack.push(newStyle);
                }
            } else if (textToken != null) {
                doc.insertString(doc.getLength(), textToken, styleStack.peek());
            }
        }
    }

    private Style createStyleFromConfig(String key) {
        TextStyle ts = config.styles.get(key);
        if (ts == null) {
            ts = new GuiConfig().styles.get(key);
            if (ts == null) {
                 Style defaultStyle = styleContext.addStyle("default", null);
                 StyleConstants.setFontFamily(defaultStyle, "SansSerif");
                 StyleConstants.setFontSize(defaultStyle, 12);
                 return defaultStyle;
            }
        }

        // Use a unique name for the style to avoid conflicts
        Style style = styleContext.addStyle(key + System.nanoTime(), null);
        StyleConstants.setFontFamily(style, ts.fontName);
        StyleConstants.setFontSize(style, ts.fontSize);
        StyleConstants.setBold(style, (ts.fontStyle & Font.BOLD) != 0);
        StyleConstants.setItalic(style, (ts.fontStyle & Font.ITALIC) != 0);
        
        if (ts.color != null) {
            StyleConstants.setForeground(style, ts.color);
        }
        
        StyleConstants.setSuperscript(style, ts.superscript);
        StyleConstants.setSubscript(style, ts.subscript);
        
        return style;
    }
}
