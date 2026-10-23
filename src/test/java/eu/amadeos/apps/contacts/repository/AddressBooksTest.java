package eu.amadeos.apps.contacts.repository;

import eu.amadeos.apps.contacts.domain.Contact;
import eu.amadeos.apps.contacts.domain.Entry;
import eu.amadeos.apps.contacts.domain.Kind;
import eu.amadeos.apps.contacts.domain.Name;
import eu.amadeos.apps.contacts.domain.Settings;
import eu.amadeos.apps.contacts.domain.Source;
import eu.amadeos.apps.contacts.domain.StoredContact;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AddressBooksTest {
    @TempDir
    Path root;

    private AddressBooks books() {
        AddressBooks books = new AddressBooks(root, Clock.fixed(Instant.parse("2026-10-23T12:00:00Z"), ZoneOffset.UTC),
                "Persönlich");
        books.reload();
        return books;
    }

    private static Contact person(String given, String family, String mail) {
        return new Contact(Contact.newUid(), Name.of(given, family), "", "", "", "", null,
                List.of(new Entry(Kind.HOME, mail)), null, null, null, null, "", null);
    }

    @Test
    void makesTheFirstAddressBook() throws Exception {
        AddressBooks books = books();
        assertEquals(List.of(new Source.Book("personal", "Persönlich")), books.books());
        assertEquals("Persönlich\n", Files.readString(root.resolve("personal/displayname")));
        assertTrue(books.contacts().isEmpty());
    }

    @Test
    void savesOneVCard4FilePerContact() throws Exception {
        AddressBooks books = books();
        Contact anna = person("Anna", "Berger", "anna@example.org");
        StoredContact saved = books.save("personal", anna);
        Path file = root.resolve("personal").resolve(anna.uid().substring("urn:uuid:".length()) + ".vcf");
        assertTrue(Files.exists(file));
        String text = Files.readString(file);
        assertTrue(text.startsWith("BEGIN:VCARD\r\nVERSION:4.0"), text);
        assertTrue(text.contains("REV:20261023T120000Z"), text);
        assertEquals(anna, saved.contact());
        try (var list = Files.list(root.resolve("personal"))) {
            assertFalse(list.anyMatch(p -> p.getFileName().toString().endsWith(".tmp")), "no temporary file is left");
        }
        AddressBooks again = books();
        assertEquals(List.of(new StoredContact("personal", anna)), again.contacts());
    }

    @Test
    void anEditKeepsTheFileAndForeignProperties() throws Exception {
        Path book = Files.createDirectories(root.resolve("personal"));
        Path file = book.resolve("from-phone.vcf");
        Files.writeString(file, "BEGIN:VCARD\r\nVERSION:3.0\r\nUID:abc-1\r\nFN:Bernd Albers\r\nN:Albers;Bernd;;;\r\n"
                + "TEL;TYPE=CELL:+49 170 5550123\r\nX-PHONETIC-FIRST-NAME:Bernd\r\nEND:VCARD\r\n", StandardCharsets.UTF_8);
        AddressBooks books = books();
        Contact bernd = books.find("abc-1").orElseThrow().contact();
        Contact changed = new Contact(bernd.uid(), bernd.name(), bernd.displayName(), "", "Albers GmbH", "", bernd.phones(),
                List.of(new Entry(Kind.WORK, "bernd@albers.example")), null, null, null, null, "", null);
        books.save("personal", changed);
        String text = Files.readString(file);
        assertTrue(text.contains("VERSION:4.0"), text);
        assertTrue(text.contains("X-PHONETIC-FIRST-NAME:Bernd"), text);
        assertTrue(text.contains("ORG:Albers GmbH"), text);
        try (var list = Files.list(book)) {
            assertEquals(1, list.filter(p -> p.toString().endsWith(".vcf")).count(), "no second file");
        }
    }

    @Test
    void reloadFollowsOtherPrograms() throws Exception {
        AddressBooks books = books();
        Contact anna = person("Anna", "Berger", "anna@example.org");
        books.save("personal", anna);
        assertFalse(books.reload(), "nothing changed");
        Path other = root.resolve("personal/new.vcf");
        Files.writeString(other, "BEGIN:VCARD\nVERSION:4.0\nFN:Clara Novak\nEND:VCARD\n");
        assertTrue(books.reload());
        StoredContact clara = books.contacts().stream().filter(c -> c.contact().label().equals("Clara Novak")).findFirst()
                .orElseThrow();
        assertEquals("new", clara.uid(), "a card without UID takes the file name");
        Files.writeString(other, "BEGIN:VCARD\nVERSION:4.0\nUID:new\nFN:Clara Novak-Lund\nEND:VCARD\n");
        Files.setLastModifiedTime(other, FileTime.from(Instant.now().plusSeconds(5)));
        assertTrue(books.reload());
        assertEquals("Clara Novak-Lund", books.find("new").orElseThrow().contact().label());
        Files.delete(other);
        assertTrue(books.reload());
        assertEquals(1, books.contacts().size());
    }

    @Test
    void brokenAndDuplicateFilesAreSkipped() throws Exception {
        Path book = Files.createDirectories(root.resolve("personal"));
        Files.writeString(book.resolve("a.vcf"), "BEGIN:VCARD\nVERSION:4.0\nUID:same\nFN:First\nEND:VCARD\n");
        Files.writeString(book.resolve("b.vcf"), "BEGIN:VCARD\nVERSION:4.0\nUID:same\nFN:Second\nEND:VCARD\n");
        Files.writeString(book.resolve("c.vcf"), "this is no vCard");
        Files.writeString(book.resolve("notes.txt"), "ignored");
        AddressBooks books = books();
        assertEquals(1, books.contacts().size());
        assertEquals("First", books.contacts().getFirst().contact().label());
    }

    @Test
    void severalAddressBooks() throws Exception {
        Files.createDirectories(root.resolve("personal"));
        Files.createDirectories(root.resolve("work"));
        Files.writeString(root.resolve("work/displayname"), "Office");
        Files.createDirectories(root.resolve(".hidden"));
        AddressBooks books = books();
        assertEquals(List.of(new Source.Book("personal", "personal"), new Source.Book("work", "Office")), books.books());
        Contact c = person("A", "B", "a@b.example");
        assertEquals("work", books.save("work", c).book());
        assertEquals("work", books.save("personal", c).book(), "a known contact stays in its book");
        assertThrows(IllegalArgumentException.class, () -> books.save("nope", person("X", "Y", "x@y.example")));
    }

    @Test
    void deleteRemovesTheFile() {
        AddressBooks books = books();
        Contact c = books.save("personal", person("A", "B", "a@b.example")).contact();
        Path file = books.file(c.uid()).orElseThrow();
        assertTrue(books.delete(c.uid()));
        assertFalse(Files.exists(file));
        assertFalse(books.delete(c.uid()));
        assertTrue(books.contacts().isEmpty());
    }

    @Test
    void fileNamesFromUids() {
        assertEquals("1234-abcd.vcf", AddressBooks.fileName("urn:uuid:1234-abcd"));
        assertEquals("a_b_c.vcf", AddressBooks.fileName("a/b c"));
        assertEquals("contact.._x.vcf", AddressBooks.fileName("../x"));
    }

    @Test
    void preferences(@TempDir Path dir) {
        ContactsPreferences prefs = new ContactsPreferences(dir.resolve("contacts.properties"));
        assertEquals(Settings.DEFAULT, prefs.settings());
        prefs.setSettings(new Settings(true));
        assertEquals(new Settings(true), new ContactsPreferences(dir.resolve("contacts.properties")).settings());
    }
}
