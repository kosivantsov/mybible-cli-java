package org.truetranslation.mybible.gui;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import java.awt.Color;
import java.io.IOException;

/**
 * A custom Gson TypeAdapter for java.awt.Color.
 * This avoids reflection issues with the Java Module System (JPMS) by serializing
 * the color as its single 32-bit ARGB integer value.
 */
public class ColorTypeAdapter extends TypeAdapter<Color> {

    @Override
    public void write(JsonWriter out, Color value) throws IOException {
        if (value == null) {
            out.nullValue();
            return;
        }
        // Write the color as its ARGB integer value.
        out.value(value.getRGB());
    }

    @Override
    public Color read(JsonReader in) throws IOException {
        if (in.peek() == com.google.gson.stream.JsonToken.NULL) {
            in.nextNull();
            return null;
        }
        // Read the integer and create a new Color, ensuring the alpha channel is preserved.
        int argb = in.nextInt();
        return new Color(argb, true);
    }
}
