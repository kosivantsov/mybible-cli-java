package org.truetranslation.mybible.core;

import org.truetranslation.mybible.core.model.Book;
import org.truetranslation.mybible.core.model.Verse;
import org.truetranslation.mybible.core.model.Reference;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.Arrays;

public class OutputFormatter {
    private final String formatString;
    private final BookMapper defaultBookMapper;
    private final BookMapper moduleBookMapper;
    private final String moduleName;

    // --- ANSI Escape Codes ---
    private static final String RESET_TO_NORMAL = "\u001B[0m";
    private static final String START_RED = "\u001B[31m";
    private static final String START_BOLD = "\u001B[1m";
    private static final String START_ITALICS = "\u001B[3m";
    private static final String START_LIGHTBLUE_ITALICS = "\u001B[3;34m";
    private static final String START_LIGHTGREY_ITALICS = "\u001B[3;90m";

    public OutputFormatter(String formatString, BookMapper defaultBookMapper, BookMapper moduleBookMapper, String moduleName) {
        this.formatString = formatString;
        this.defaultBookMapper = defaultBookMapper;
        this.moduleBookMapper = moduleBookMapper;
        this.moduleName = moduleName;
    }

    private String createSimplePlainText(String text) {
        String temp = text;
        temp = temp.replaceAll("\\[\\d+\\]", ""); 
        temp = temp.replaceAll("(?i)<[SGH]>\\s*[0-9]+\\s*</[SGH]>", " ");
        temp = temp.replaceAll("<[^>]+>", " ");
        return temp.trim().replaceAll("\\s+", " ");
    }

    private String createMultiLinePlainText(String text) {
        String temp = text;
        temp = temp.replaceAll("\\[\\d+\\]", " ");
        temp = temp.replaceAll("(?i)<[SGH]>\\s*[0-9]+\\s*</[SGH]>", " ");
        if (temp.trim().startsWith("<pb/>")) {
            temp = temp.replaceFirst("<pb/>", "").trim();
        }
        temp = temp.replace("<pb/>", "\n");
        temp = temp.replaceAll("<[^>]+>", " ");
        return Arrays.stream(temp.split("\n"))
                .map(line -> line.trim().replaceAll("\\s+", " "))
                .collect(Collectors.joining("\n"));
    }
    
    private String createAnsiText(String text, boolean includeStrongs, boolean multiline) {
        String tempText = text;
        tempText = tempText.replaceAll("(?i)<f>.*?</f>|</?t>|~|@|@[^ ]+? ", " ");
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
        if (!includeStrongs) {
            tempText = tempText.replaceAll("(?i)<S>.*?</S>", "");
        }

        final Map<String, String> tagToAnsi = new HashMap<>();
        tagToAnsi.put("J", START_RED);
        tagToAnsi.put("E", START_BOLD);
        tagToAnsi.put("I", START_ITALICS);
        tagToAnsi.put("N", START_LIGHTGREY_ITALICS);
        tagToAnsi.put("S", START_LIGHTBLUE_ITALICS);

        StringBuilder result = new StringBuilder();
        Stack<String> stateStack = new Stack<>();
        stateStack.push(RESET_TO_NORMAL);

        Pattern pattern = Pattern.compile("(?i)(</?(?:J|E|I|N|S)>)|([^<]+)");
        Matcher matcher = pattern.matcher(tempText);

        while (matcher.find()) {
            String tagToken = matcher.group(1);
            String textToken = matcher.group(2);
            if (tagToken != null) {
                boolean isClosing = tagToken.startsWith("</");
                String tagName = tagToken.replaceAll("[<>/]", "").toUpperCase();
                if (isClosing) {
                    if (tagName.equals("S")) result.append("}");
                    if (tagName.equals("J")) {
                        while (stateStack.size() > 1) stateStack.pop();
                    } else if (stateStack.size() > 1) {
                        stateStack.pop();
                    }
                    result.append(stateStack.peek());
                } else {
                    String ansiCode = tagToAnsi.get(tagName);
                    if (ansiCode != null) {
                        stateStack.push(ansiCode);
                        result.append(ansiCode);
                        if (tagName.equals("S")) result.append("{");
                    }
                }
            } else if (textToken != null) {
                result.append(textToken);
            }
        }
        result.append(RESET_TO_NORMAL);
        return result.toString();
    }

    public String format(Verse verse, Reference reference) {
        String result = formatString.replace("\\n", "\n").replace("\\t", "\t");

        final String userProvidedShortName = (reference != null) ? reference.getBookName() : null;

        Optional<Book> defaultBookOpt = defaultBookMapper.getBook(verse.getBookNumber());
        Optional<Book> moduleBookOpt = moduleBookMapper.getBook(verse.getBookNumber());

        String defaultFullName = defaultBookOpt.map(Book::getFullName).orElse("");
        
        String defaultAbbrName = defaultBookOpt.map(book -> 
            (userProvidedShortName != null && book.getShortNames().contains(userProvidedShortName))
                ? userProvidedShortName 
                : book.getShortNames().stream().findFirst().orElse("")
        ).orElse("");

        String moduleFullName = moduleBookOpt.map(Book::getFullName).orElse(defaultFullName);
        
        String moduleShortName = moduleBookOpt.map(book ->
            (userProvidedShortName != null && book.getShortNames().contains(userProvidedShortName))
                ? userProvidedShortName
                : book.getShortNames().stream().findFirst().orElse(defaultAbbrName)
        ).orElse(defaultAbbrName);

        String tempResult = result.replace("%T", "%%TEMP_T%%");
        
        tempResult = tempResult.replace("%a", defaultAbbrName);
        tempResult = tempResult.replace("%f", defaultFullName);
        tempResult = tempResult.replace("%A", moduleShortName);
        tempResult = tempResult.replace("%F", moduleFullName);
        
        tempResult = tempResult.replace("%b", String.valueOf(verse.getBookNumber()));
        tempResult = tempResult.replace("%c", String.valueOf(verse.getChapter()));
        tempResult = tempResult.replace("%v", String.valueOf(verse.getVerse()));
        tempResult = tempResult.replace("%m", moduleName);
        
        tempResult = tempResult.replace("%z", createSimplePlainText(verse.getText()));
        tempResult = tempResult.replace("%t", createMultiLinePlainText(verse.getText()));

        tempResult = tempResult.replace("%X", createAnsiText(verse.getText(), true, true));
        tempResult = tempResult.replace("%Y", createAnsiText(verse.getText(), false, true));
        tempResult = tempResult.replace("%Z", createAnsiText(verse.getText(), false, false));
        
        result = tempResult.replace("%%TEMP_T%%", verse.getText());

        return result;
    }
}
