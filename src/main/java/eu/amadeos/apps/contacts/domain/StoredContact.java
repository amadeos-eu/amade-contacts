package eu.amadeos.apps.contacts.domain;

import java.util.Objects;

/** A contact and the address book it is in. */
public record StoredContact(String book, Contact contact) {
    public StoredContact {
        Objects.requireNonNull(book, "book");
        Objects.requireNonNull(contact, "contact");
    }

    public String uid() {
        return contact.uid();
    }
}
