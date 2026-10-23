package eu.amadeos.apps.contacts.service;

import eu.amadeos.apps.contacts.domain.Contact;
import eu.amadeos.apps.contacts.domain.StoredContact;
import eu.amadeos.apps.contacts.io.Imported;
import eu.amadeos.apps.contacts.repository.AddressBooks;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;

/** Puts imported contacts into an address book, merging them with known ones if asked. */
public final class ContactImport {
    /**
     * @param book  the address book for new contacts
     * @param merge true to merge a contact with a known one that has one of its e-mail addresses
     * @param group a group every imported contact joins, or empty
     */
    public record Options(String book, boolean merge, Optional<String> group) {
    }

    /** What happened: contacts added and merged. */
    public record Result(int added, int merged, List<String> uids) {
    }

    private ContactImport() {
    }

    /** How many of the imported contacts share an e-mail address with a known contact. */
    public static int duplicates(AddressBooks books, List<Imported> imported) {
        List<StoredContact> known = books.contacts();
        return (int) imported.stream().filter(i -> match(known, i.contact()).isPresent()).count();
    }

    // --8<-- [start:apply]
    public static Result apply(AddressBooks books, List<Imported> imported, Options options) {
        int added = 0, merged = 0;
        List<String> uids = new ArrayList<>();
        for (Imported item : imported) {
            Contact contact = item.contact();
            if (options.group().isPresent()) {
                TreeSet<String> groups = new TreeSet<>(contact.groups());
                groups.add(options.group().get());
                contact = contact.withGroups(groups);
            }
            Optional<StoredContact> same = options.merge() ? match(books.contacts(), contact) : Optional.empty();
            if (same.isPresent()) {
                StoredContact saved = books.save(same.get().book(), same.get().contact().mergedWith(contact));
                uids.add(saved.uid());
                merged++;
            } else {
                if (books.find(contact.uid()).isPresent()) contact = contact.withUid(Contact.newUid());
                StoredContact saved = books.save(options.book(), contact, item.vcard());
                uids.add(saved.uid());
                added++;
            }
        }
        return new Result(added, merged, uids);
    }
    // --8<-- [end:apply]

    private static Optional<StoredContact> match(List<StoredContact> known, Contact contact) {
        return known.stream().filter(k -> contact.emails().stream().anyMatch(e -> k.contact().hasEmail(e.value())))
                .findFirst();
    }
}
