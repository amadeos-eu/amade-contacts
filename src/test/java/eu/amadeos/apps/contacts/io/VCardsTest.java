package eu.amadeos.apps.contacts.io;

import eu.amadeos.apps.contacts.domain.Birthday;
import eu.amadeos.apps.contacts.domain.Contact;
import eu.amadeos.apps.contacts.domain.Entry;
import eu.amadeos.apps.contacts.domain.Kind;
import eu.amadeos.apps.contacts.domain.Name;
import eu.amadeos.apps.contacts.domain.Photo;
import eu.amadeos.apps.contacts.domain.PostalAddress;
import ezvcard.VCard;
import ezvcard.VCardVersion;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VCardsTest {
    private static final Instant NOW = Instant.parse("2026-10-23T12:00:00Z");

    @Test
    void readsAnAppleVCard30() {
        List<Imported> list = Samples.file("apple-3.0.vcf").contacts(null, Locale.ROOT);
        assertEquals(1, list.size());
        Contact c = list.getFirst().contact();
        assertEquals("F3A1C2D4-1111-2222-3333-444455556666", c.uid());
        assertEquals(Name.of("David", "Okafor"), c.name());
        assertEquals("David Okafor", c.label());
        assertEquals("City Library, Music Department", c.organization());
        assertEquals("Librarian", c.jobTitle());
        assertEquals(List.of(new Entry(Kind.OTHER, "d.okafor@example.org")), c.emails());
        assertEquals(List.of(new Entry(Kind.MOBILE, "+44 7700 900123")), c.phones());
        assertEquals(List.of(new PostalAddress(Kind.WORK, "1 Library Way\nFloor 2", "EC1A 1BB", "London", "",
                "United Kingdom")), c.addresses());
        assertEquals(new Birthday(0, 7, 21), c.birthday(), "Apple's 1604 means no year");
        assertEquals(Set.of("Choir", "Library"), c.groups());
        assertEquals("Lends us sheet music.", c.note());
        assertNotNull(c.photo());
        assertEquals("image/png", c.photo().mediaType());
    }

    @Test
    void readsVCard21WithQuotedPrintable() {
        ImportFile file = Samples.file("phone-2.1.vcf");
        assertEquals(FileFormat.VCARD_4, file.format());
        List<Contact> list = file.contacts(null, Locale.ROOT).stream().map(Imported::contact).toList();
        assertEquals(2, list.size());
        Contact j = list.getFirst();
        assertEquals("Jürgen Müller", j.label());
        assertEquals(Name.of("Jürgen", "Müller"), j.name());
        assertEquals(List.of(new Entry(Kind.MOBILE, "+49160555100"), new Entry(Kind.WORK, "+49911555100")), j.phones());
        assertTrue(j.uid().startsWith("urn:uuid:"), "a card without UID gets one");
        assertEquals(List.of(new Entry(Kind.HOME, "+49895550345")), list.get(1).phones());
    }

    @Test
    void aNewContactRoundTripsThroughVCard4() {
        Contact c = new Contact("urn:uuid:42", new Name("Dr.", "Anna", "Maria", "Berger", ""), "Anna Berger", "Annie",
                "Lindner & Partner", "Project Manager",
                List.of(new Entry(Kind.MOBILE, "+49 170 5550142"), new Entry(Kind.FAX, "+49 89 1"),
                        new Entry(Kind.OTHER, "+49 89 2")),
                List.of(new Entry(Kind.WORK, "anna@example.org"), new Entry(Kind.OTHER, "a@b.example")),
                List.of(new PostalAddress(Kind.HOME, "Lindenstraße 12\nHinterhaus", "80331", "München", "Bayern", "Germany")),
                List.of("https://example.org"), new Birthday(0, 11, 14), new TreeSet<>(Set.of("Work", "Choir")),
                "Line one\nLine two; with, punctuation", new Photo(new byte[]{1, 2, 3}, "image/jpeg"));
        String text = VCards.write(List.of(VCards.toVCard(c, NOW)), VCardVersion.V4_0);
        assertTrue(text.contains("VERSION:4.0"));
        assertTrue(text.contains("BDAY:--1114"), text);
        assertTrue(text.contains("PRODID:" + VCards.PRODUCT_ID));
        Contact back = VCards.toContact(VCards.read(text).getFirst());
        assertEquals(c, back);
        Contact back3 = VCards.toContact(VCards.read(VCards.write(List.of(VCards.toVCard(c, NOW)), VCardVersion.V3_0)).getFirst());
        assertEquals(c.name(), back3.name());
        assertEquals(c.phones(), back3.phones());
        assertEquals(c.addresses(), back3.addresses());
        assertEquals(c.groups(), back3.groups());
        assertEquals(c.note(), back3.note());
    }

    @Test
    void anEditKeepsWhatTheAddressBookDoesNotShow() {
        VCard card = VCards.read(new String(Samples.bytes("apple-3.0.vcf"), java.nio.charset.StandardCharsets.UTF_8)).getFirst();
        Contact c = VCards.toContact(card);
        Contact changed = new Contact(c.uid(), c.name(), c.displayName(), c.nickname(), c.organization(), "Head Librarian",
                c.phones(), c.emails(), c.addresses(), c.websites(), c.birthday(), c.groups(), c.note(), c.photo());
        VCards.update(card, changed, NOW);
        String text = VCards.write(List.of(card), VCardVersion.V4_0);
        assertTrue(text.contains("TITLE:Head Librarian"), text);
        assertTrue(text.contains("matrix:u/david:example.org"), "IMPP is kept: " + text);
        assertTrue(text.contains("X-SOCIALPROFILE"), "unknown properties are kept: " + text);
        assertTrue(text.contains("ORG:City Library;Music Department"), "the unchanged ORG keeps its department: " + text);
        assertTrue(text.contains("PREF=1") || text.toLowerCase().contains("pref"), "parameters of unchanged properties stay");
        assertFalse(text.contains("Apple Inc."), "PRODID is ours now");
        assertEquals(changed, VCards.toContact(VCards.read(text).getFirst()));
    }

    @Test
    void removingAllValuesRemovesTheProperties() {
        VCard card = VCards.read(new String(Samples.bytes("apple-3.0.vcf"), java.nio.charset.StandardCharsets.UTF_8)).getFirst();
        Contact c = VCards.toContact(card);
        Contact bare = new Contact(c.uid(), c.name(), "", "", "", "", null, null, null, null, null, null, "", null);
        VCards.update(card, bare, NOW);
        Contact back = VCards.toContact(VCards.read(VCards.write(List.of(card), VCardVersion.V4_0)).getFirst());
        assertTrue(back.emails().isEmpty());
        assertTrue(back.phones().isEmpty());
        assertNull(back.birthday());
        assertNull(back.photo());
        assertEquals("", back.organization());
        assertEquals("David Okafor", back.label(), "FN is written from the name");
    }
}
