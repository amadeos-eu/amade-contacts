package eu.amadeos.apps.contacts.io;

import eu.amadeos.apps.contacts.domain.Birthday;
import eu.amadeos.apps.contacts.domain.Contact;
import eu.amadeos.apps.contacts.domain.Entry;
import eu.amadeos.apps.contacts.domain.Kind;
import eu.amadeos.apps.contacts.domain.Name;
import eu.amadeos.apps.contacts.domain.PostalAddress;
import eu.amadeos.apps.contacts.domain.StoredContact;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CsvTest {
    private static List<Contact> read(ImportFile file, Locale locale) {
        return file.contacts(ColumnMapping.guess(file.table().headers()), locale).stream().map(Imported::contact).toList();
    }

    @Test
    void tableWithQuotesLineBreaksAndSeparators() {
        CsvTable t = CsvTable.parse("﻿a;b;c\r\n\"x;1\";\"say \"\"hi\"\"\";\"two\nlines\"\r\n\r\n1;2\n");
        assertEquals(List.of("a", "b", "c"), t.headers());
        assertEquals(List.of(List.of("x;1", "say \"hi\"", "two\nlines"), List.of("1", "2", "")), t.rows());
        assertEquals(List.of(List.of("1", "2")), CsvTable.parse("a\tb\n1\t2").rows());
        assertEquals("\"a\",\"b \"\"c\"\"\"\r\n", CsvTable.line(List.of("a", "b \"c\"")));
    }

    @Test
    void googleColumnsAreRecognized() {
        ImportFile file = Samples.file("google.csv");
        assertEquals(FileFormat.CSV_GOOGLE, file.format());
        ColumnMapping m = ColumnMapping.guess(file.table().headers());
        List<String> h = file.table().headers();
        assertEquals(Field.GIVEN, m.target(h.indexOf("First Name")).field());
        assertEquals(Field.IGNORE, m.target(h.indexOf("Phonetic First Name")).field());
        assertEquals(Field.ORGANIZATION, m.target(h.indexOf("Organization Name")).field());
        assertEquals(Field.JOB_TITLE, m.target(h.indexOf("Organization Title")).field());
        assertEquals(Field.IGNORE, m.target(h.indexOf("E-mail 1 - Label")).field());
        assertEquals(Field.EMAIL, m.target(h.indexOf("E-mail 1 - Value")).field());
        assertEquals(h.indexOf("E-mail 1 - Label"), m.target(h.indexOf("E-mail 1 - Value")).labelColumn());
        assertEquals(Field.IGNORE, m.target(h.indexOf("Address 1 - Formatted")).field());
        assertEquals(Field.POSTAL_CODE, m.target(h.indexOf("Address 1 - Postal Code")).field());
        assertEquals(Field.STREET, m.target(h.indexOf("Address 1 - PO Box")).field());
        assertEquals(Field.GROUPS, m.target(h.indexOf("Labels")).field());
    }

    @Test
    void readsGoogleContacts() {
        List<Contact> list = read(Samples.file("google.csv"), Locale.ENGLISH);
        assertEquals(2, list.size());
        Contact h = list.getFirst();
        assertEquals(Name.of("Hubert", "Kranz"), h.name());
        assertEquals("Hubsi", h.nickname());
        assertEquals("Stadtchor", h.organization());
        assertEquals("Treasurer", h.jobTitle());
        assertEquals(new Birthday(1961, 3, 2), h.birthday());
        assertEquals("Bass, sings since 1990", h.note());
        assertEquals(Set.of("Choir"), h.groups());
        assertEquals(List.of(new Entry(Kind.HOME, "h.kranz@example.de"), new Entry(Kind.WORK, "hubert.kranz@stadtchor.example")),
                h.emails());
        assertEquals(List.of(new Entry(Kind.MOBILE, "+49 89 5550199"), new Entry(Kind.FAX, "+49 89 5550198")), h.phones());
        assertEquals(List.of(new PostalAddress(Kind.HOME, "Am Bach 3", "85354", "Freising", "Bayern", "Germany")), h.addresses());
        assertEquals(List.of("https://stadtchor.example"), h.websites());
        Contact c = list.get(1);
        assertEquals(new Birthday(0, 10, 29), c.birthday());
        assertEquals(Set.of("Choir", "Friends"), c.groups());
        assertEquals(2, c.emails().size(), "Google's ::: puts two addresses in one cell");
    }

    @Test
    void readsOutlook() {
        ImportFile file = Samples.file("outlook.csv");
        assertEquals(FileFormat.CSV_OUTLOOK, file.format());
        Contact g = read(file, Locale.US).getFirst();
        assertEquals(new Name("Mr.", "Gilbert", "", "Martin", ""), g.name());
        assertEquals("Martin & Fils", g.organization());
        assertEquals("Sales Director", g.jobTitle());
        assertEquals(List.of(new Entry(Kind.OTHER, "g.martin@example.fr")), g.emails(), "the display name column is no address");
        assertEquals(List.of(new Entry(Kind.WORK, "+33 1 55 50 12 34"), new Entry(Kind.MOBILE, "+33 6 55 50 12 34"),
                new Entry(Kind.FAX, "+33 1 55 50 12 35")), g.phones());
        assertEquals(List.of(new PostalAddress(Kind.WORK, "12 rue de la Paix", "75002", "Paris", "", "France")), g.addresses());
        assertEquals(new Birthday(1972, 11, 5), g.birthday());
        assertEquals(Set.of("Work", "Suppliers"), g.groups());
        assertEquals("Wine supplier", g.note());
    }

    // --8<-- [start:outlook-de]
    @Test
    void readsGermanOutlookInWindows1252() {
        ImportFile file = Samples.file("outlook-de.csv");
        assertEquals(TextFiles.WINDOWS_1252, file.charset());
        Contact j = read(file, Locale.GERMAN).getFirst();
        assertEquals(Name.of("Jürgen", "Müller"), j.name());
        assertEquals("Grünbau AG", j.organization());
        assertEquals("Geschäftsführer", j.jobTitle());
        assertEquals(List.of(new Entry(Kind.WORK, "+49 911 555 100"), new Entry(Kind.MOBILE, "+49 160 555 100")), j.phones());
        assertEquals(List.of(new Entry(Kind.OTHER, "j.mueller@gruenbau.example")), j.emails());
        assertEquals(List.of(new PostalAddress(Kind.WORK, "Hauptstraße 5", "90402", "Nürnberg", "", "Deutschland")), j.addresses());
        assertEquals(new Birthday(1965, 12, 24), j.birthday());
        assertEquals(Set.of("Kunden"), j.groups());
    }
    // --8<-- [end:outlook-de]

    @Test
    void handMadeTables() {
        String text = "Name,Email,Telefon privat,Geburtsdatum,Gruppe\nAnna Berger,anna@example.org,089 1234,14.11.1986,Chor\n,,,,\n";
        ImportFile file = ImportFile.of("list.csv", text.getBytes(StandardCharsets.UTF_8));
        assertEquals(FileFormat.CSV, file.format());
        List<Contact> list = read(file, Locale.GERMAN);
        assertEquals(1, list.size(), "empty rows are skipped");
        Contact a = list.getFirst();
        assertEquals(Name.of("Anna", "Berger"), a.name(), "a full name is split");
        assertEquals("Anna Berger", a.label());
        assertEquals(List.of(new Entry(Kind.HOME, "089 1234")), a.phones());
        assertEquals(new Birthday(1986, 11, 14), a.birthday());
        assertEquals(Set.of("Chor"), a.groups());
    }

    @Test
    void theUserCanChangeAColumn() {
        String text = "Who,What\nAnna,anna@example.org\n";
        ImportFile file = ImportFile.of("x.csv", text.getBytes(StandardCharsets.UTF_8));
        ColumnMapping m = ColumnMapping.guess(file.table().headers());
        assertEquals(Field.IGNORE, m.target(0).field());
        m = m.with(0, Field.GIVEN, Kind.OTHER).with(1, Field.EMAIL, Kind.WORK);
        Contact a = file.contacts(m, Locale.ROOT).getFirst().contact();
        assertEquals("Anna", a.label());
        assertEquals(List.of(new Entry(Kind.WORK, "anna@example.org")), a.emails());
    }

    @Test
    void googleAndOutlookExportsReadBack() {
        Contact c = new Contact("u1", new Name("Dr.", "Anna", "", "Berger", ""), "", "", "Lindner", "PM",
                List.of(new Entry(Kind.MOBILE, "+49 170 1"), new Entry(Kind.WORK, "+49 89 2"), new Entry(Kind.FAX, "+49 89 3")),
                List.of(new Entry(Kind.WORK, "anna@example.org"), new Entry(Kind.HOME, "anna@home.example")),
                List.of(new PostalAddress(Kind.HOME, "Lindenstraße 12", "80331", "München", "", "Germany")),
                List.of("https://example.org"), new Birthday(1986, 11, 14), new TreeSet<>(Set.of("Choir", "Work")),
                "Note, with \"quotes\"\nand lines", null);
        List<StoredContact> list = List.of(new StoredContact("personal", c));
        for (FileFormat format : List.of(FileFormat.CSV_GOOGLE, FileFormat.CSV_OUTLOOK)) {
            String text = format == FileFormat.CSV_GOOGLE ? CsvContacts.google(list) : CsvContacts.outlook(list);
            ImportFile file = ImportFile.of("x.csv", text.getBytes(StandardCharsets.UTF_8));
            assertEquals(format, file.format());
            Contact back = read(file, Locale.US).getFirst();
            assertEquals(c.name(), back.name(), format.name());
            assertEquals(c.organization(), back.organization());
            assertEquals(c.jobTitle(), back.jobTitle());
            assertEquals(c.birthday(), back.birthday(), format.name());
            assertEquals(c.groups(), back.groups(), format.name());
            assertEquals(c.note(), back.note());
            assertEquals(c.addresses(), back.addresses(), format.name());
            assertEquals(c.websites(), back.websites());
            assertEquals(Set.copyOf(c.phones()), Set.copyOf(back.phones()), format.name());
            assertTrue(back.hasEmail("anna@example.org") && back.hasEmail("anna@home.example"));
        }
    }
}
