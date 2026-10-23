package eu.amadeos.apps.contacts.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

/** The sample files in {@code src/test/resources/samples}: made-up people as other programs export them. */
public final class Samples {
    private Samples() {
    }

    public static byte[] bytes(String name) {
        try (InputStream in = Samples.class.getResourceAsStream("/samples/" + name)) {
            if (in == null) throw new IllegalArgumentException("No sample " + name);
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static ImportFile file(String name) {
        return ImportFile.of(name, bytes(name));
    }
}
