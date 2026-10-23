package eu.amadeos.apps.contacts.io;

import java.util.Locale;

/** The formats the address book reads and writes. */
public enum FileFormat {
    VCARD_4("vcf"), VCARD_3("vcf"), CSV_GOOGLE("csv"), CSV_OUTLOOK("csv"), CSV("csv"), LDIF("ldif");

    private final String extension;

    FileFormat(String extension) {
        this.extension = extension;
    }

    public String extension() {
        return extension;
    }

    public String key() {
        return "format." + name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    /** The formats offered for export; {@link #CSV} is for import only. */
    public static final java.util.List<FileFormat> EXPORT = java.util.List.of(VCARD_4, VCARD_3, CSV_GOOGLE, CSV_OUTLOOK, LDIF);

    /**
     * The format of a file from its first lines: vCard ({@link #VCARD_4} stands for every version), LDIF, or CSV
     * of Google, Outlook or another program.
     */
    public static FileFormat detect(String text) {
        String t = text.startsWith("﻿") ? text.substring(1) : text;
        for (String line : t.split("\\R", 50)) {
            String l = line.strip().toLowerCase(Locale.ROOT);
            if (l.isEmpty() || l.startsWith("#")) continue;
            if (l.startsWith("begin:vcard")) return VCARD_4;
            if (l.startsWith("dn:") || l.startsWith("dn::") || l.startsWith("version: 1") || l.startsWith("version:1")) {
                return LDIF;
            }
            break;
        }
        CsvTable table = CsvTable.parse(t.lines().limit(2).reduce("", (a, b) -> a + b + "\n"));
        return switch (CsvContacts.Layout.of(table.headers())) {
            case GOOGLE -> CSV_GOOGLE;
            case OUTLOOK -> CSV_OUTLOOK;
            case OTHER -> CSV;
        };
    }
}
