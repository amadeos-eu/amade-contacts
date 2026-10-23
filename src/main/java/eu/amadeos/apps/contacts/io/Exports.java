package eu.amadeos.apps.contacts.io;

import eu.amadeos.apps.contacts.domain.StoredContact;
import ezvcard.VCard;
import ezvcard.VCardVersion;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/** Contacts as the text of an export file. */
public final class Exports {
    private Exports() {
    }

    /**
     * @param stored the stored vCard of a contact, with properties the address book does not show; empty makes a
     *               new one from the contact
     */
    public static String write(List<StoredContact> contacts, FileFormat format, Function<String, Optional<VCard>> stored,
                               Instant now) {
        return switch (format) {
            case VCARD_4, VCARD_3 -> VCards.write(contacts.stream()
                    .map(c -> stored.apply(c.uid()).orElseGet(() -> VCards.toVCard(c.contact(), now))).toList(),
                    format == FileFormat.VCARD_4 ? VCardVersion.V4_0 : VCardVersion.V3_0);
            case CSV_GOOGLE, CSV -> CsvContacts.google(contacts);
            case CSV_OUTLOOK -> CsvContacts.outlook(contacts);
            case LDIF -> Ldif.write(contacts);
        };
    }
}
