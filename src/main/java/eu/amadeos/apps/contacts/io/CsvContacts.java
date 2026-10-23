package eu.amadeos.apps.contacts.io;

import eu.amadeos.apps.contacts.domain.Birthday;
import eu.amadeos.apps.contacts.domain.Contact;
import eu.amadeos.apps.contacts.domain.Entry;
import eu.amadeos.apps.contacts.domain.Kind;
import eu.amadeos.apps.contacts.domain.Name;
import eu.amadeos.apps.contacts.domain.PostalAddress;
import eu.amadeos.apps.contacts.domain.StoredContact;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;

/**
 * Contacts from a CSV table and its {@link ColumnMapping}, and CSV in the layouts of Google Contacts and Outlook.
 */
public final class CsvContacts {
    /** Google's own labels that are no group of the user. */
    private static final Set<String> SYSTEM_LABELS = Set.of("mycontacts", "starred", "imported", "other contacts");

    private CsvContacts() {
    }

    /** The kind of table, for the import dialog. */
    public enum Layout {
        GOOGLE, OUTLOOK, OTHER;

        public static Layout of(List<String> headers) {
            List<String> folded = headers.stream().map(Contact::fold).toList();
            if (folded.contains("e-mail 1 - value") || folded.contains("given name") && folded.contains("group membership")) {
                return GOOGLE;
            }
            if (folded.contains("e-mail address") && folded.contains("business phone")
                    || folded.contains("e-mail-adresse") && folded.contains("telefon geschaftlich")) {
                return OUTLOOK;
            }
            return OTHER;
        }
    }

    // ---------------------------------------------------------------- reading

    public static List<Contact> read(CsvTable table, ColumnMapping mapping, Locale locale) {
        List<Contact> contacts = new ArrayList<>();
        for (List<String> row : table.rows()) {
            Contact contact = contact(row, mapping, locale);
            if (!contact.label().isEmpty()) contacts.add(contact);
        }
        return contacts;
    }

    static Contact contact(List<String> row, ColumnMapping mapping, Locale locale) {
        Map<Field, String> single = new EnumMap<>(Field.class);
        List<Entry> phones = new ArrayList<>();
        List<Entry> emails = new ArrayList<>();
        List<String> websites = new ArrayList<>();
        TreeSet<String> groups = new TreeSet<>();
        List<String> notes = new ArrayList<>();
        Map<String, Map<Field, String>> addressParts = new LinkedHashMap<>();
        Map<String, Kind> addressKinds = new LinkedHashMap<>();
        for (int column = 0; column < row.size(); column++) {
            ColumnMapping.Target target = mapping.target(column);
            String value = CsvTable.cell(row, column);
            if (value.isEmpty() || target.field() == Field.IGNORE) continue;
            Kind kind = target.kind();
            if (kind == Kind.OTHER && target.labelColumn() >= 0) {
                kind = ColumnMapping.kindOfLabel(CsvTable.cell(row, target.labelColumn()));
            }
            switch (target.field()) {
                case EMAIL -> {
                    // Google puts several addresses of one kind in one cell: "a@x.org ::: b@y.org"
                    for (String part : value.split("\\s*:::\\s*")) emails.add(new Entry(kind == Kind.FAX || kind == Kind.MOBILE ? Kind.OTHER : kind, part));
                }
                case PHONE -> {
                    for (String part : value.split("\\s*:::\\s*")) phones.add(new Entry(kind, part));
                }
                case WEBSITE -> websites.addAll(List.of(value.split("\\s*:::\\s*")));
                case GROUPS -> groups.addAll(groups(value));
                case NOTE -> notes.add(value);
                case STREET, POSTAL_CODE, CITY, REGION, COUNTRY -> {
                    Map<Field, String> parts = addressParts.computeIfAbsent(target.group(), g -> new EnumMap<>(Field.class));
                    parts.merge(target.field(), value, (a, b) -> a + "\n" + b);
                    addressKinds.putIfAbsent(target.group(), kind == Kind.MOBILE || kind == Kind.FAX ? Kind.OTHER : kind);
                }
                default -> single.merge(target.field(), value, (a, b) -> a + " " + b);
            }
        }
        List<PostalAddress> addresses = new ArrayList<>();
        addressParts.forEach((group, parts) -> addresses.add(new PostalAddress(addressKinds.get(group),
                parts.get(Field.STREET), parts.get(Field.POSTAL_CODE), parts.get(Field.CITY), parts.get(Field.REGION),
                parts.get(Field.COUNTRY))));
        Function<Field, String> get = f -> single.getOrDefault(f, "");
        Name name = new Name(get.apply(Field.PREFIX), get.apply(Field.GIVEN), get.apply(Field.MIDDLE),
                get.apply(Field.FAMILY), get.apply(Field.SUFFIX));
        String display = get.apply(Field.DISPLAY_NAME);
        if (name.isBlank() && !display.isEmpty() && get.apply(Field.ORGANIZATION).isEmpty()) name = Name.parse(display);
        Birthday birthday = Birthday.parse(get.apply(Field.BIRTHDAY), locale).orElse(null);
        return new Contact(Contact.newUid(), name, display, get.apply(Field.NICKNAME), get.apply(Field.ORGANIZATION),
                get.apply(Field.JOB_TITLE), phones, emails, addresses, websites, birthday, groups,
                String.join("\n", notes), null);
    }

    /** Group names from a cell: Google's "Choir ::: * myContacts", Outlook's "Choir;Work" or "Choir, Work". */
    static List<String> groups(String cell) {
        List<String> result = new ArrayList<>();
        for (String part : cell.split("\\s*(:::|;|,)\\s*")) {
            String g = part.strip();
            if (g.startsWith("*")) g = g.substring(1).strip();
            if (g.isEmpty() || SYSTEM_LABELS.contains(g.toLowerCase(Locale.ROOT))) continue;
            result.add(g);
        }
        return result;
    }

    // ---------------------------------------------------------------- writing

    /** CSV that Google Contacts imports, with as many e-mail, phone, address and website columns as needed. */
    public static String google(List<StoredContact> stored) {
        List<Contact> contacts = stored.stream().map(StoredContact::contact).toList();
        int mails = max(contacts, c -> c.emails().size());
        int phones = max(contacts, c -> c.phones().size());
        int adrs = max(contacts, c -> c.addresses().size());
        int webs = max(contacts, c -> c.websites().size());
        List<String> header = new ArrayList<>(List.of("First Name", "Middle Name", "Last Name", "Name Prefix",
                "Name Suffix", "Nickname", "Organization Name", "Organization Title", "Birthday", "Notes", "Labels"));
        for (int i = 1; i <= mails; i++) header.addAll(List.of("E-mail " + i + " - Label", "E-mail " + i + " - Value"));
        for (int i = 1; i <= phones; i++) header.addAll(List.of("Phone " + i + " - Label", "Phone " + i + " - Value"));
        for (int i = 1; i <= adrs; i++) {
            for (String part : List.of("Label", "Street", "City", "Region", "Postal Code", "Country")) {
                header.add("Address " + i + " - " + part);
            }
        }
        for (int i = 1; i <= webs; i++) header.addAll(List.of("Website " + i + " - Label", "Website " + i + " - Value"));
        StringBuilder sb = new StringBuilder(CsvTable.line(header));
        for (Contact c : contacts) {
            Name n = c.name();
            List<String> row = new ArrayList<>(List.of(n.given(), n.additional(), n.family(), n.prefix(), n.suffix(),
                    c.nickname(), c.organization(), c.jobTitle(), isoBirthday(c), c.note(), googleLabels(c)));
            for (int i = 0; i < mails; i++) {
                Entry e = i < c.emails().size() ? c.emails().get(i) : null;
                row.addAll(e == null ? List.of("", "") : List.of(googleLabel(e.kind()), e.value()));
            }
            for (int i = 0; i < phones; i++) {
                Entry e = i < c.phones().size() ? c.phones().get(i) : null;
                row.addAll(e == null ? List.of("", "") : List.of(googleLabel(e.kind()), e.value()));
            }
            for (int i = 0; i < adrs; i++) {
                PostalAddress a = i < c.addresses().size() ? c.addresses().get(i) : null;
                row.addAll(a == null ? List.of("", "", "", "", "", "")
                        : List.of(googleLabel(a.kind()), a.street(), a.city(), a.region(), a.postalCode(), a.country()));
            }
            for (int i = 0; i < webs; i++) {
                row.addAll(i < c.websites().size() ? List.of("", c.websites().get(i)) : List.of("", ""));
            }
            sb.append(CsvTable.line(row));
        }
        return sb.toString();
    }

    private static String googleLabels(Contact c) {
        List<String> labels = new ArrayList<>(c.groups());
        labels.add("* myContacts");
        return String.join(" ::: ", labels);
    }

    private static String googleLabel(Kind kind) {
        return switch (kind) {
            case MOBILE -> "Mobile";
            case HOME -> "Home";
            case WORK -> "Work";
            case FAX -> "Work Fax";
            case OTHER -> "Other";
        };
    }

    private static String isoBirthday(Contact c) {
        if (c.birthday() == null) return "";
        var b = c.birthday();
        return b.hasYear() ? String.format("%04d-%02d-%02d", b.year(), b.month(), b.day())
                : String.format("--%02d-%02d", b.month(), b.day());
    }

    private static final List<String> OUTLOOK_HEADER = List.of("First Name", "Middle Name", "Last Name", "Title",
            "Suffix", "Nickname", "Company", "Job Title", "E-mail Address", "E-mail 2 Address", "E-mail 3 Address",
            "Home Phone", "Business Phone", "Mobile Phone", "Business Fax", "Other Phone",
            "Home Street", "Home City", "Home State", "Home Postal Code", "Home Country/Region",
            "Business Street", "Business City", "Business State", "Business Postal Code", "Business Country/Region",
            "Other Street", "Other City", "Other State", "Other Postal Code", "Other Country/Region",
            "Birthday", "Web Page", "Notes", "Categories");

    /** CSV in the columns of Outlook's export, which Outlook and most other programs import. */
    public static String outlook(List<StoredContact> stored) {
        StringBuilder sb = new StringBuilder(CsvTable.line(OUTLOOK_HEADER));
        for (StoredContact s : stored) {
            Contact c = s.contact();
            Name n = c.name();
            List<String> row = new ArrayList<>(List.of(n.given(), n.additional(), n.family(), n.prefix(), n.suffix(),
                    c.nickname(), c.organization(), c.jobTitle()));
            for (int i = 0; i < 3; i++) row.add(i < c.emails().size() ? c.emails().get(i).value() : "");
            for (Kind kind : List.of(Kind.HOME, Kind.WORK, Kind.MOBILE, Kind.FAX, Kind.OTHER)) {
                row.add(c.phones().stream().filter(p -> p.kind() == kind).map(Entry::value).findFirst().orElse(""));
            }
            for (Kind kind : List.of(Kind.HOME, Kind.WORK, Kind.OTHER)) {
                PostalAddress a = c.addresses().stream().filter(x -> x.kind() == kind).findFirst().orElse(null);
                row.addAll(a == null ? List.of("", "", "", "", "")
                        : List.of(a.street(), a.city(), a.region(), a.postalCode(), a.country()));
            }
            var b = c.birthday();
            row.add(b == null ? "" : b.month() + "/" + b.day() + "/" + (b.hasYear() ? b.year() : 1604));
            row.add(c.websites().isEmpty() ? "" : c.websites().getFirst());
            row.add(c.note());
            row.add(String.join(";", c.groups()));
            sb.append(CsvTable.line(row));
        }
        return sb.toString();
    }

    private static int max(List<Contact> contacts, java.util.function.ToIntFunction<Contact> count) {
        return contacts.stream().mapToInt(count).max().orElse(0);
    }
}
