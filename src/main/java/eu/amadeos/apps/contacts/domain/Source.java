package eu.amadeos.apps.contacts.domain;

import java.time.LocalDate;
import java.util.Objects;

/** What the list on the left offers: everyone, one address book, one group, or the coming birthdays. */
public sealed interface Source {
    /** Days ahead that {@link Birthdays} looks. */
    int BIRTHDAY_DAYS = 30;

    boolean includes(StoredContact contact, LocalDate today);

    record All() implements Source {
        @Override
        public boolean includes(StoredContact contact, LocalDate today) {
            return true;
        }
    }

    record Book(String id, String name) implements Source {
        public Book {
            Objects.requireNonNull(id, "id");
        }

        @Override
        public boolean includes(StoredContact contact, LocalDate today) {
            return contact.book().equals(id);
        }
    }

    record Group(String name) implements Source {
        @Override
        public boolean includes(StoredContact contact, LocalDate today) {
            return contact.contact().groups().stream().anyMatch(g -> g.equalsIgnoreCase(name));
        }
    }

    record Birthdays() implements Source {
        @Override
        public boolean includes(StoredContact contact, LocalDate today) {
            Birthday b = contact.contact().birthday();
            return b != null && b.daysUntil(today) <= BIRTHDAY_DAYS;
        }
    }
}
