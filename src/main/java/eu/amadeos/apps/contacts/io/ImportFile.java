package eu.amadeos.apps.contacts.io;

import ezvcard.VCard;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** A file chosen for import: its bytes, the encoding and the format, which the import dialog can change. */
public record ImportFile(String name, byte[] bytes, Charset charset, FileFormat format) {
    public ImportFile {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(bytes, "bytes");
    }

    public static ImportFile read(Path file) throws IOException {
        return of(file.getFileName().toString(), Files.readAllBytes(file));
    }

    /** With the encoding and format found in the bytes. */
    public static ImportFile of(String name, byte[] bytes) {
        Charset charset = TextFiles.detect(bytes);
        return new ImportFile(name, bytes, charset, FileFormat.detect(TextFiles.decode(bytes, charset)));
    }

    public ImportFile withCharset(Charset newCharset) {
        return new ImportFile(name, bytes, newCharset, format);
    }

    public ImportFile withFormat(FileFormat newFormat) {
        return new ImportFile(name, bytes, charset, newFormat);
    }

    public String text() {
        return TextFiles.decode(bytes, charset);
    }

    public boolean isCsv() {
        return format == FileFormat.CSV || format == FileFormat.CSV_GOOGLE || format == FileFormat.CSV_OUTLOOK;
    }

    public CsvTable table() {
        return CsvTable.parse(text());
    }

    /** The contacts in the file; for CSV with the given column mapping. */
    public List<Imported> contacts(ColumnMapping mapping, Locale locale) {
        List<Imported> result = new ArrayList<>();
        switch (format) {
            case VCARD_3, VCARD_4 -> {
                // ez-vcard reads the bytes itself: vCard 2.1 may name its own charset per property
                for (VCard card : VCards.read(text())) {
                    var contact = VCards.toContact(card);
                    if (!contact.label().isEmpty()) result.add(new Imported(contact, card));
                }
            }
            case LDIF -> Ldif.read(text()).forEach(c -> result.add(new Imported(c, null)));
            case CSV, CSV_GOOGLE, CSV_OUTLOOK -> CsvContacts.read(table(), mapping, locale)
                    .forEach(c -> result.add(new Imported(c, null)));
        }
        return result;
    }
}
