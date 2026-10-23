package eu.amadeos.apps.contacts.io;

import eu.amadeos.apps.contacts.domain.Birthday;
import eu.amadeos.apps.contacts.domain.Contact;
import eu.amadeos.apps.contacts.domain.Entry;
import eu.amadeos.apps.contacts.domain.Kind;
import eu.amadeos.apps.contacts.domain.Name;
import eu.amadeos.apps.contacts.domain.Photo;
import eu.amadeos.apps.contacts.domain.PostalAddress;
import ezvcard.Ezvcard;
import ezvcard.VCard;
import ezvcard.VCardVersion;
import ezvcard.parameter.AddressType;
import ezvcard.parameter.EmailType;
import ezvcard.parameter.ImageType;
import ezvcard.parameter.TelephoneType;
import ezvcard.parameter.VCardParameter;
import ezvcard.property.Address;
import ezvcard.property.Categories;
import ezvcard.property.Email;
import ezvcard.property.FormattedName;
import ezvcard.property.Nickname;
import ezvcard.property.Note;
import ezvcard.property.Organization;
import ezvcard.property.ProductId;
import ezvcard.property.Revision;
import ezvcard.property.StructuredName;
import ezvcard.property.Telephone;
import ezvcard.property.Title;
import ezvcard.property.Uid;
import ezvcard.property.Url;
import ezvcard.util.PartialDate;

import java.io.IOException;
import java.io.InputStream;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoField;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;
import java.util.function.Function;

/**
 * Between vCards (2.1, 3.0 and 4.0, read with ez-vcard) and {@link Contact}s. Writing a changed contact back
 * replaces only the properties whose meaning changed, so parameters, other programs' properties and what the
 * address book does not show (instant messaging, keys, geo position) stay as they were.
 */
public final class VCards {
    public static final String PRODUCT_ID = "-//amadeOS//amade-contacts//EN";

    private VCards() {
    }

    /** All vCards in a stream; a file may hold one or many. Lines ez-vcard cannot read are skipped. */
    public static List<VCard> read(InputStream in) throws IOException {
        return Ezvcard.parse(in).all();
    }

    public static List<VCard> read(String text) {
        return Ezvcard.parse(text).all();
    }

    /** The cards as text in the given version, each with this program's PRODID. */
    public static String write(Collection<VCard> cards, VCardVersion version) {
        return Ezvcard.write(cards).version(version).prodId(false).go();
    }

    // ---------------------------------------------------------------- reading

    public static Contact toContact(VCard card) {
        Uid uid = card.getUid();
        String id = uid == null || uid.getValue() == null || uid.getValue().isBlank() ? Contact.newUid() : uid.getValue();
        StructuredName n = card.getStructuredName();
        Name name = n == null ? Name.EMPTY : new Name(String.join(" ", n.getPrefixes()), n.getGiven(),
                String.join(" ", n.getAdditionalNames()), n.getFamily(), String.join(" ", n.getSuffixes()));
        FormattedName fn = card.getFormattedName();
        Nickname nick = card.getNickname();
        Organization org = card.getOrganization();
        Title title = card.getTitles().isEmpty() ? null : card.getTitles().getFirst();

        List<Entry> phones = new ArrayList<>();
        for (Telephone tel : card.getTelephoneNumbers()) {
            String number = tel.getText() != null ? tel.getText() : tel.getUri() != null ? tel.getUri().getNumber() : "";
            phones.add(new Entry(Kind.of(values(tel.getTypes(), VCardParameter::getValue)), number));
        }
        List<Entry> emails = new ArrayList<>();
        for (Email email : card.getEmails()) {
            emails.add(new Entry(Kind.of(values(email.getTypes(), VCardParameter::getValue)), email.getValue()));
        }
        List<PostalAddress> addresses = new ArrayList<>();
        for (Address adr : card.getAddresses()) {
            List<String> street = new ArrayList<>(adr.getStreetAddresses());
            street.addAll(adr.getExtendedAddresses());
            street.addAll(adr.getPoBoxes());
            addresses.add(new PostalAddress(Kind.of(values(adr.getTypes(), VCardParameter::getValue)),
                    String.join("\n", street.stream().filter(s -> s != null && !s.isBlank()).toList()),
                    adr.getPostalCode(), adr.getLocality(), adr.getRegion(), adr.getCountry()));
        }
        List<String> websites = new ArrayList<>();
        for (Url url : card.getUrls()) {
            if (url.getValue() != null) websites.add(url.getValue());
        }
        TreeSet<String> groups = new TreeSet<>();
        for (Categories categories : card.getCategoriesList()) groups.addAll(categories.getValues());
        List<String> notes = new ArrayList<>();
        for (Note note : card.getNotes()) {
            if (note.getValue() != null) notes.add(note.getValue());
        }
        Contact contact = new Contact(id, name, "", nick == null ? "" : String.join(", ", nick.getValues()),
                org == null ? "" : String.join(", ", org.getValues().stream().filter(v -> v != null && !v.isBlank()).toList()),
                title == null ? "" : title.getValue(),
                phones, emails, addresses, websites, birthday(card), groups, String.join("\n", notes), photo(card));
        // FN that only repeats the name stays out of displayName, so the editor keeps it following the name
        String formatted = fn == null || fn.getValue() == null ? "" : fn.getValue().strip();
        return formatted.isEmpty() || formatted.equals(contact.label()) ? contact : contact.withDisplayName(formatted);
    }

    private static Birthday birthday(VCard card) {
        ezvcard.property.Birthday bday = card.getBirthday();
        if (bday == null) return null;
        try {
            Temporal date = bday.getDate();
            if (date != null) {
                int year = date.get(ChronoField.YEAR);
                // Apple writes 1604 for a birthday without a year
                return new Birthday(year == 1604 ? 0 : year, date.get(ChronoField.MONTH_OF_YEAR),
                        date.get(ChronoField.DAY_OF_MONTH));
            }
            PartialDate partial = bday.getPartialDate();
            if (partial != null && partial.getMonth() != null && partial.getDate() != null) {
                int year = partial.getYear() == null ? 0 : partial.getYear();
                return new Birthday(year, partial.getMonth(), partial.getDate());
            }
            if (bday.getText() != null) {
                return Birthday.parse(bday.getText(), java.util.Locale.ROOT).orElse(null);
            }
        } catch (DateTimeException e) {
            return null;
        }
        return null;
    }

    private static Photo photo(VCard card) {
        for (ezvcard.property.Photo p : card.getPhotos()) {
            if (p.getData() != null && p.getData().length > 0) {
                ImageType type = p.getContentType();
                return new Photo(p.getData(), type == null ? null : type.getMediaType());
            }
        }
        return null;
    }

    private static <T> List<String> values(Collection<T> items, Function<T, String> value) {
        return items.stream().map(value).filter(Objects::nonNull).toList();
    }

    // ---------------------------------------------------------------- writing

    /** A new vCard 4.0 for a contact. */
    public static VCard toVCard(Contact contact, Instant now) {
        VCard card = new VCard(VCardVersion.V4_0);
        card.setUid(new Uid(contact.uid()));
        return update(card, contact, now);
    }

    /**
     * Writes the contact into {@code card}: properties that mean the same as before stay untouched, the others are
     * replaced. Also sets UID, FN (required by vCard 4.0), REV and PRODID.
     */
    public static VCard update(VCard card, Contact contact, Instant now) {
        Contact before = toContact(card);
        if (card.getUid() == null || !contact.uid().equals(before.uid())) card.setUid(new Uid(contact.uid()));
        if (!contact.name().equals(before.name())) {
            card.removeProperties(StructuredName.class);
            if (!contact.name().isBlank()) card.setStructuredName(structuredName(contact.name()));
        }
        String fn = contact.label();
        if (card.getFormattedName() == null || !fn.equals(before.label())) {
            card.removeProperties(FormattedName.class);
            card.setFormattedName(fn);
        }
        if (!contact.nickname().equals(before.nickname())) {
            card.removeProperties(Nickname.class);
            if (!contact.nickname().isEmpty()) card.setNickname(contact.nickname().split("\\s*,\\s*"));
        }
        if (!contact.organization().equals(before.organization())) {
            card.removeProperties(Organization.class);
            if (!contact.organization().isEmpty()) card.setOrganization(contact.organization());
        }
        if (!contact.jobTitle().equals(before.jobTitle())) {
            card.removeProperties(Title.class);
            if (!contact.jobTitle().isEmpty()) card.addTitle(contact.jobTitle());
        }
        if (!contact.phones().equals(before.phones())) {
            card.removeProperties(Telephone.class);
            for (Entry phone : contact.phones()) {
                Telephone tel = new Telephone(phone.value());
                for (String type : phone.kind().vcardTypes()) tel.getTypes().add(TelephoneType.get(type));
                if (phone.kind() == Kind.MOBILE || phone.kind() == Kind.HOME || phone.kind() == Kind.WORK) {
                    tel.getTypes().add(TelephoneType.VOICE);
                }
                card.addTelephoneNumber(tel);
            }
        }
        if (!contact.emails().equals(before.emails())) {
            card.removeProperties(Email.class);
            for (Entry email : contact.emails()) {
                Email e = new Email(email.value());
                for (String type : email.kind().vcardTypes()) e.getTypes().add(EmailType.get(type));
                card.addEmail(e);
            }
        }
        if (!contact.addresses().equals(before.addresses())) {
            card.removeProperties(Address.class);
            for (PostalAddress a : contact.addresses()) {
                Address adr = new Address();
                adr.setStreetAddress(a.street().isEmpty() ? null : a.street());
                adr.setPostalCode(blankToNull(a.postalCode()));
                adr.setLocality(blankToNull(a.city()));
                adr.setRegion(blankToNull(a.region()));
                adr.setCountry(blankToNull(a.country()));
                for (String type : a.kind().vcardTypes()) adr.getTypes().add(AddressType.get(type));
                card.addAddress(adr);
            }
        }
        if (!contact.websites().equals(before.websites())) {
            card.removeProperties(Url.class);
            for (String url : contact.websites()) card.addUrl(url);
        }
        if (!Objects.equals(contact.birthday(), before.birthday())) {
            card.removeProperties(ezvcard.property.Birthday.class);
            Birthday b = contact.birthday();
            if (b != null) {
                card.setBirthday(b.hasYear() ? new ezvcard.property.Birthday(LocalDate.of(b.year(), b.month(), b.day()))
                        : new ezvcard.property.Birthday(PartialDate.builder().month(b.month()).date(b.day()).build()));
            }
        }
        if (!contact.groups().equals(before.groups())) {
            card.removeProperties(Categories.class);
            if (!contact.groups().isEmpty()) {
                Categories categories = new Categories();
                categories.getValues().addAll(contact.groups());
                card.addCategories(categories);
            }
        }
        if (!contact.note().equals(before.note())) {
            card.removeProperties(Note.class);
            if (!contact.note().isEmpty()) card.addNote(contact.note());
        }
        if (!Objects.equals(contact.photo(), before.photo())) {
            card.removeProperties(ezvcard.property.Photo.class);
            Photo p = contact.photo();
            if (p != null) {
                ImageType type = ImageType.find(null, p.mediaType(), null);
                card.addPhoto(new ezvcard.property.Photo(p.data(), type == null ? ImageType.JPEG : type));
            }
        }
        card.setRevision(new Revision(now));
        card.setProductId(new ProductId(PRODUCT_ID));
        return card;
    }

    private static StructuredName structuredName(Name name) {
        StructuredName n = new StructuredName();
        n.setFamily(blankToNull(name.family()));
        n.setGiven(blankToNull(name.given()));
        if (!name.additional().isEmpty()) n.getAdditionalNames().add(name.additional());
        if (!name.prefix().isEmpty()) n.getPrefixes().add(name.prefix());
        if (!name.suffix().isEmpty()) n.getSuffixes().add(name.suffix());
        return n;
    }

    private static String blankToNull(String s) {
        return s == null || s.isEmpty() ? null : s;
    }
}
