package eu.amadeos.apps.contacts.domain;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContactTest {
    static Contact anna() {
        return new Contact("urn:uuid:1", Name.of("Anna", "Müller"), "", "Annie", "Lindner & Partner", "Project Manager",
                List.of(new Entry(Kind.MOBILE, "+49 (170) 555-0142")), List.of(new Entry(Kind.WORK, "Anna@Example.org")),
                List.of(new PostalAddress(Kind.HOME, "Lindenstraße 12", "80331", "München", "", "Germany")),
                List.of("https://example.org"), new Birthday(1986, 11, 14), new TreeSet<>(Set.of("Choir")), "Alto", null);
    }

    @Test
    void labelFallsBackFromDisplayNameToAddresses() {
        assertEquals("Anna Müller", anna().label());
        Contact company = new Contact("u", null, "", "", "Liberty Print", "", null, null, null, null, null, null, "", null);
        assertEquals("Liberty Print", company.label());
        assertTrue(company.isOrganization() || company.displayName().isEmpty());
        Contact mailOnly = new Contact("u", null, "", "", "", "", null, List.of(new Entry(Kind.OTHER, "x@y.org")), null,
                null, null, null, "", null);
        assertEquals("x@y.org", mailOnly.label());
        assertEquals("", Contact.create().label());
    }

    @Test
    void blankEntriesAreDropped() {
        Contact c = new Contact("u", Name.of("A", "B"), "", "", "", "", List.of(new Entry(Kind.HOME, " ")),
                List.of(new Entry(Kind.HOME, "")), List.of(new PostalAddress(Kind.HOME, "", "", "", "", "")),
                List.of(" "), null, new TreeSet<>(Set.of(" ", "Work ")), " note ", null);
        assertTrue(c.phones().isEmpty());
        assertTrue(c.emails().isEmpty());
        assertTrue(c.addresses().isEmpty());
        assertTrue(c.websites().isEmpty());
        assertEquals(Set.of("Work"), c.groups());
        assertEquals("note", c.note());
    }

    @Test
    void searchIgnoresCaseAccentsAndPhoneFormatting() {
        Contact c = anna();
        assertTrue(c.matches("muller"));
        assertTrue(c.matches("MÜLLER anna"));
        assertTrue(c.matches("lindner"));
        assertTrue(c.matches("example.org"));
        assertTrue(c.matches("münchen"));
        assertTrue(c.matches("choir"));
        assertTrue(c.matches("annie"));
        assertTrue(c.matches("5550142"));
        assertTrue(c.matches("170 555"));
        assertTrue(c.matches(""));
        assertFalse(c.matches("berger"));
        assertFalse(c.matches("anna berger"));
        assertFalse(c.matches("999"));
    }

    @Test
    void initials() {
        assertEquals("AM", anna().initials());
        Contact company = new Contact("u", null, "Liberty Print Shop", "", "Liberty Print Shop", "", null, null, null,
                null, null, null, "", null);
        assertEquals("L", company.initials());
        assertEquals("?", Contact.create().initials());
        Contact two = new Contact("u", null, "Hubert Kranz", "", "", "", null, null, null, null, null, null, "", null);
        assertEquals("HK", two.initials());
    }

    @Test
    void sortKeys() {
        assertEquals("Anna Müller", anna().sortKey(false));
        assertEquals("Müller, Anna", anna().sortKey(true));
    }

    @Test
    void mergingAddsWhatIsMissing() {
        Contact known = anna();
        Contact imported = new Contact("urn:uuid:2", Name.of("Anna", "Müller"), "", "", "", "",
                List.of(new Entry(Kind.MOBILE, "+491705550142"), new Entry(Kind.HOME, "089 555 1")),
                List.of(new Entry(Kind.HOME, "anna@example.org"), new Entry(Kind.HOME, "anna@home.example")),
                List.of(), List.of(), null, new TreeSet<>(Set.of("Imported")), "Met at the fair", null);
        Contact merged = known.mergedWith(imported);
        assertEquals("urn:uuid:1", merged.uid());
        assertEquals(2, merged.phones().size(), "the same number with other spaces is not added twice");
        assertEquals(List.of("Anna@Example.org", "anna@home.example"), merged.emails().stream().map(Entry::value).toList());
        assertEquals(Set.of("Choir", "Imported"), merged.groups());
        assertEquals("Alto\nMet at the fair", merged.note());
        assertEquals(known.birthday(), merged.birthday());
        assertEquals(known.mergedWith(imported), merged.mergedWith(imported), "merging twice changes nothing more");
    }

    @Test
    void sources() {
        java.time.LocalDate today = java.time.LocalDate.of(2026, 10, 23);
        StoredContact s = new StoredContact("personal", anna());
        assertTrue(new Source.All().includes(s, today));
        assertTrue(new Source.Book("personal", "Personal").includes(s, today));
        assertFalse(new Source.Book("work", "Work").includes(s, today));
        assertTrue(new Source.Group("choir").includes(s, today));
        assertFalse(new Source.Group("Family").includes(s, today));
        assertTrue(new Source.Birthdays().includes(s, today));
        assertFalse(new Source.Birthdays().includes(s, java.time.LocalDate.of(2026, 6, 1)));
    }

    @Test
    void addressLines() {
        PostalAddress a = new PostalAddress(Kind.WORK, "Main St 1\nSuite 4", "94016", "San Francisco", "CA", "USA");
        assertEquals(List.of("Main St 1", "Suite 4", "94016 San Francisco, CA", "USA"), a.lines());
        assertEquals("Main St 1, Suite 4, 94016 San Francisco, CA, USA", a.oneLine());
    }
}
