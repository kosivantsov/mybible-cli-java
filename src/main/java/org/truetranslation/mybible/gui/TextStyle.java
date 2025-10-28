package org.truetranslation.mybible.gui;

import java.awt.Color;

public class TextStyle {
    public String fontName;
    public int fontStyle;
    public int fontSize;
    public Color color;
    public Color background;
    public boolean superscript;
    public boolean subscript;

    // Default constructor for Gson
    public TextStyle() {}

    public TextStyle(String fontName, int fontStyle, int fontSize, Color color, Color background, boolean superscript, boolean subscript) {
        this.fontName = fontName;
        this.fontStyle = fontStyle;
        this.fontSize = fontSize;
        this.color = color;
        this.background = background;
        this.superscript = superscript;
        this.subscript = subscript;
    }

    // Copy constructor to allow editing a temporary copy of a style
    public TextStyle(TextStyle other) {
        this.fontName = other.fontName;
        this.fontStyle = other.fontStyle;
        this.fontSize = other.fontSize;
        this.color = other.color;
        this.background = other.background;
        this.superscript = other.superscript;
        this.subscript = other.subscript;
    }
}
