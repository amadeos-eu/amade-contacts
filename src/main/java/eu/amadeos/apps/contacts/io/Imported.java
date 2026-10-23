package eu.amadeos.apps.contacts.io;

import eu.amadeos.apps.contacts.domain.Contact;
import ezvcard.VCard;

import java.util.Objects;

/**
 * A contact read from a file, before it goes into an address book.
 *
 * @param vcard the vCard it came from, so that its other properties are kept; null for CSV and LDIF
 */
public record Imported(Contact contact, VCard vcard) {
    public Imported {
        Objects.requireNonNull(contact, "contact");
    }
}
