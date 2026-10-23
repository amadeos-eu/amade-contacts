package eu.amadeos.apps.contacts.domain;

import org.junit.jupiter.api.Test;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NameAndBirthdayTest {
    @Test
    void nameInReadingOrderAndFamilyFirst() {
        Name n = new Name("Dr.", "Anna", "Maria", "Berger", "Jr.");
        assertEquals("Dr. Anna Maria Berger Jr.", n.full());
        assertEquals("Berger, Anna Maria", n.familyFirst());
        assertEquals("Anna", Name.of("Anna", "").familyFirst());
        assertTrue(Name.EMPTY.isBlank());
    }

    @Test
    void parsesTypedNames() {
        assertEquals(Name.of("Anna", "Berger"), Name.parse("Anna Berger"));
        assertEquals(Name.of("Anna", "Berger"), Name.parse("Berger, Anna"));
        assertEquals(new Name("Dr.", "Anna", "Maria", "Berger", ""), Name.parse("  Dr. Anna  Maria Berger "));
        assertEquals(Name.of("Cher", ""), Name.parse("Cher"));
        assertEquals(Name.EMPTY, Name.parse(" "));
    }

    @Test
    void parsesTheUsualDateForms() {
        assertEquals(Optional.of(new Birthday(1986, 11, 14)), Birthday.parse("1986-11-14", Locale.GERMAN));
        assertEquals(Optional.of(new Birthday(1986, 11, 14)), Birthday.parse("19861114", Locale.GERMAN));
        assertEquals(Optional.of(new Birthday(0, 11, 14)), Birthday.parse("--1114", Locale.GERMAN));
        assertEquals(Optional.of(new Birthday(0, 11, 14)), Birthday.parse("--11-14", Locale.GERMAN));
        assertEquals(Optional.of(new Birthday(1986, 11, 14)), Birthday.parse("14.11.1986", Locale.GERMAN));
        assertEquals(Optional.of(new Birthday(0, 11, 14)), Birthday.parse("14.11.", Locale.GERMAN));
        assertEquals(Optional.of(new Birthday(1986, 11, 4)), Birthday.parse("11/4/1986", Locale.US));
        assertEquals(Optional.of(new Birthday(1986, 4, 11)), Birthday.parse("11/4/1986", Locale.UK));
        assertEquals(Optional.of(new Birthday(1986, 11, 14)), Birthday.parse("14/11/1986", Locale.US));
        assertEquals(Optional.of(new Birthday(1986, 11, 14)), Birthday.parse("1986-11-14T00:00:00Z", Locale.ROOT));
    }

    @Test
    void year1604MeansNoYear() {
        assertEquals(Optional.of(new Birthday(0, 3, 2)), Birthday.parse("1604-03-02", Locale.ROOT));
    }

    @Test
    void rejectsWhatIsNoDate() {
        assertEquals(Optional.empty(), Birthday.parse("next week", Locale.ROOT));
        assertEquals(Optional.empty(), Birthday.parse("31.02.1990", Locale.GERMAN));
        assertEquals(Optional.empty(), Birthday.parse("", Locale.GERMAN));
        assertThrows(DateTimeException.class, () -> new Birthday(2023, 2, 29));
    }

    @Test
    void nextBirthdayAndAge() {
        Birthday b = new Birthday(1986, 11, 14);
        LocalDate today = LocalDate.of(2026, 10, 23);
        assertEquals(LocalDate.of(2026, 11, 14), b.next(today));
        assertEquals(22, b.daysUntil(today));
        assertEquals(Optional.of(40), b.ageAtNext(today));
        assertEquals(0, b.daysUntil(LocalDate.of(2026, 11, 14)));
        assertEquals(LocalDate.of(2027, 11, 14), b.next(LocalDate.of(2026, 11, 15)));
        assertFalse(new Birthday(0, 11, 14).ageAtNext(today).isPresent());
    }

    @Test
    void leapDayIsCelebratedOn28FebruaryInOtherYears() {
        Birthday b = new Birthday(2000, 2, 29);
        assertEquals(LocalDate.of(2027, 2, 28), b.next(LocalDate.of(2026, 10, 1)));
        assertEquals(LocalDate.of(2028, 2, 29), b.next(LocalDate.of(2027, 3, 1)));
    }

    @Test
    void formatsInTheLanguage() {
        assertEquals("14. November 1986", new Birthday(1986, 11, 14).format(Locale.GERMAN));
        assertEquals("14. November", new Birthday(0, 11, 14).format(Locale.GERMAN));
        assertEquals("14.11.1986", new Birthday(1986, 11, 14).toInput(Locale.GERMAN));
        assertEquals("14.11.", new Birthday(0, 11, 14).toInput(Locale.GERMAN));
        assertEquals("1986-11-14", new Birthday(1986, 11, 14).toInput(Locale.ENGLISH));
        assertEquals("--11-14", new Birthday(0, 11, 14).toInput(Locale.ENGLISH));
        for (Locale l : new Locale[]{Locale.GERMAN, Locale.ENGLISH, Locale.FRENCH, Locale.ITALIAN, Locale.forLanguageTag("es")}) {
            Birthday b = new Birthday(1986, 11, 14);
            assertEquals(Optional.of(b), Birthday.parse(b.toInput(l), l), l.toString());
            Birthday noYear = new Birthday(0, 2, 29);
            assertEquals(Optional.of(noYear), Birthday.parse(noYear.toInput(l), l), l.toString());
        }
    }

    @Test
    void kindsFromVCardTypesAndLabels() {
        assertEquals(Kind.MOBILE, Kind.of(java.util.List.of("voice", "CELL")));
        assertEquals(Kind.FAX, Kind.of(java.util.List.of("work", "fax")));
        assertEquals(Kind.WORK, Kind.of(java.util.List.of("work", "voice")));
        assertEquals(Kind.OTHER, Kind.of(java.util.List.of("internet")));
        assertEquals(Kind.HOME, Kind.ofLabel("Privat"));
        assertEquals(Kind.WORK, Kind.ofLabel("* Business"));
        assertEquals(Kind.MOBILE, Kind.ofLabel("Handy"));
    }
}
