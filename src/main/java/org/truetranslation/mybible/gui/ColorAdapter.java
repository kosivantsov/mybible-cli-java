package org.truetranslation.mybible.gui;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.awt.Color;
import java.io.IOException;

public class ColorAdapter extends TypeAdapter<Color> {

    @Override
    public void write(JsonWriter out, Color value) throws IOException {
        if (value == null) {
            out.nullValue();
            return;
        }
        out.value(value.getRGB());
    }

    @Override
    public Color read(JsonReader in) throws IOException {
        if (in.peek() == com.google.gson.stream.JsonToken.NULL) {
            in.nextNull();
            return null;
        }
        int rgb = in.nextInt();
        return new Color(rgb, true);
    }
}
