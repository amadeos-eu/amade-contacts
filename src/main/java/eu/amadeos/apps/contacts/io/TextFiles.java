package eu.amadeos.apps.contacts.io;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Text of imported files in the right encoding: UTF-8 or UTF-16 by their byte order mark, UTF-8 if the bytes are
 * valid UTF-8, else Windows-1252, which Outlook and Excel write on Western European Windows.
 */
public final class TextFiles {
    public static final Charset WINDOWS_1252 = Charset.forName("windows-1252");
    /** The encodings the import dialog offers. */
    public static final List<Charset> CHOICES = List.of(StandardCharsets.UTF_8, WINDOWS_1252,
            StandardCharsets.ISO_8859_1, StandardCharsets.UTF_16, Charset.forName("x-MacRoman"));

    private TextFiles() {
    }

    public static Charset detect(byte[] bytes) {
        if (startsWith(bytes, 0xEF, 0xBB, 0xBF)) return StandardCharsets.UTF_8;
        if (startsWith(bytes, 0xFE, 0xFF) || startsWith(bytes, 0xFF, 0xFE)) return StandardCharsets.UTF_16;
        try {
            StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes));
            return StandardCharsets.UTF_8;
        } catch (CharacterCodingException e) {
            return WINDOWS_1252;
        }
    }

    public static String decode(byte[] bytes, Charset charset) {
        String text = new String(bytes, charset);
        return text.startsWith("﻿") ? text.substring(1) : text;
    }

    private static boolean startsWith(byte[] bytes, int... prefix) {
        if (bytes.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if ((bytes[i] & 0xFF) != prefix[i]) return false;
        }
        return true;
    }
}
