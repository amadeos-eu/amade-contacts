package eu.amadeos.apps.contacts.service;

import eu.amadeos.apps.contacts.domain.Contact;
import eu.amadeos.apps.contacts.domain.Entry;
import eu.amadeos.apps.contacts.domain.Kind;
import eu.amadeos.apps.contacts.domain.Name;
import eu.amadeos.apps.contacts.io.Imported;
import eu.amadeos.apps.contacts.io.Samples;
import eu.amadeos.apps.contacts.repository.AddressBooks;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContactImportTest {
    @TempDir
    Path root;

    private AddressBooks books() {
        AddressBooks books = new AddressBooks(root, Clock.systemUTC(), "Personal");
        books.reload();
        return books;
    }

    private static Imported person(String name, String mail) {
        return new Imported(new Contact(Contact.newUid(), Name.parse(name), "", "", "", "", null,
                List.of(new Entry(Kind.HOME, mail)), null, null, null, null, "", null), null);
    }

    @Test
    void addsMergesAndGroups() {
        AddressBooks books = books();
        books.save("personal", person("Anna Berger", "anna@example.org").contact());
        List<Imported> imported = List.of(person("Anna B.", "ANNA@example.org"), person("Clara Novak", "clara@example.net"));
        assertEquals(1, ContactImport.duplicates(books, imported));
        ContactImport.Result result = ContactImport.apply(books, imported,
                new ContactImport.Options("personal", true, Optional.of("Imported")));
        assertEquals(1, result.added());
        assertEquals(1, result.merged());
        assertEquals(2, books.contacts().size());
        Contact anna = books.contacts().stream().filter(c -> c.contact().hasEmail("anna@example.org")).findFirst()
                .orElseThrow().contact();
        assertEquals("Anna Berger", anna.label(), "the known name stays");
        assertEquals(Set.of("Imported"), anna.groups());
    }

    @Test
    void withoutMergingEveryContactIsNew() {
        AddressBooks books = books();
        books.save("personal", person("Anna Berger", "anna@example.org").contact());
        ContactImport.apply(books, List.of(person("Anna B.", "anna@example.org")),
                new ContactImport.Options("personal", false, Optional.empty()));
        assertEquals(2, books.contacts().size());
    }

    @Test
    void importingTheSameVCardTwiceGetsANewUid() {
        AddressBooks books = books();
        List<Imported> david = Samples.file("apple-3.0.vcf").contacts(null, Locale.ROOT);
        ContactImport.apply(books, david, new ContactImport.Options("personal", false, Optional.empty()));
        ContactImport.apply(books, david, new ContactImport.Options("personal", false, Optional.empty()));
        assertEquals(2, books.contacts().size());
    }

    @Test
    void importedVCardsKeepTheirOtherProperties() throws Exception {
        AddressBooks books = books();
        List<Imported> david = Samples.file("apple-3.0.vcf").contacts(null, Locale.ROOT);
        ContactImport.apply(books, david, new ContactImport.Options("personal", true, Optional.of("Library")));
        String text = Files.readString(books.file(books.contacts().getFirst().uid()).orElseThrow());
        assertTrue(text.contains("matrix:u/david:example.org"), text);
        assertTrue(text.contains("VERSION:4.0"));
    }
}
