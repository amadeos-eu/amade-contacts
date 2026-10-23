package eu.amadeos.apps.contacts.io;

import eu.amadeos.apps.contacts.domain.Birthday;
import eu.amadeos.apps.contacts.domain.Contact;
import eu.amadeos.apps.contacts.domain.Entry;
import eu.amadeos.apps.contacts.domain.Kind;
import eu.amadeos.apps.contacts.domain.Name;
import eu.amadeos.apps.contacts.domain.PostalAddress;
import eu.amadeos.apps.contacts.domain.StoredContact;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

/**
 * LDIF (RFC 2849) with the attributes of Thunderbird's address book, which also suit LDAP directories.
 * Thunderbird's mailing lists ({@code groupOfNames}) become groups of their members.
 */
public final class Ldif {
    private Ldif() {
    }

    /** One entry: attribute names in lower case to their values, in file order. */
    record Record(Map<String, List<String>> values) {
        String first(String attribute) {
            List<String> list = values.get(attribute);
            return list == null || list.isEmpty() ? "" : list.getFirst().strip();
        }

        List<String> all(String attribute) {
            return values.getOrDefault(attribute, List.of());
        }

        boolean isList() {
            return all("objectclass").stream().anyMatch(o -> o.equalsIgnoreCase("groupOfNames"));
        }
    }

    // ---------------------------------------------------------------- reading

    public static List<Contact> read(String text) {
        List<Record> records = parse(text);
        Map<String, List<String>> listsByMemberDn = new LinkedHashMap<>();
        for (Record r : records) {
            if (!r.isList()) continue;
            for (String member : r.all("member")) {
                listsByMemberDn.computeIfAbsent(normalizeDn(member), k -> new ArrayList<>()).add(r.first("cn"));
            }
        }
        List<Contact> contacts = new ArrayList<>();
        for (Record r : records) {
            if (r.isList()) continue;
            Contact c = contact(r, listsByMemberDn.getOrDefault(normalizeDn(r.first("dn")), List.of()));
            if (!c.label().isEmpty()) contacts.add(c);
        }
        return contacts;
    }

    static List<Record> parse(String text) {
        List<Record> records = new ArrayList<>();
        List<String> lines = unfold(text);
        Map<String, List<String>> current = new LinkedHashMap<>();
        for (String line : lines) {
            if (line.isBlank()) {
                if (!current.isEmpty()) records.add(new Record(current));
                current = new LinkedHashMap<>();
                continue;
            }
            if (line.startsWith("#")) continue;
            int colon = line.indexOf(':');
            if (colon <= 0) continue;
            String attribute = line.substring(0, colon).strip().toLowerCase(Locale.ROOT);
            int semicolon = attribute.indexOf(';');
            if (semicolon > 0) attribute = attribute.substring(0, semicolon);
            String rest = line.substring(colon + 1);
            String value;
            if (rest.startsWith(":")) {
                try {
                    value = new String(Base64.getMimeDecoder().decode(rest.substring(1).strip()), StandardCharsets.UTF_8);
                } catch (IllegalArgumentException e) {
                    continue;
                }
            } else if (rest.startsWith("<")) {
                continue;
            } else {
                value = rest.startsWith(" ") ? rest.substring(1) : rest;
            }
            if (attribute.equals("version") && current.isEmpty()) continue;
            current.computeIfAbsent(attribute, k -> new ArrayList<>()).add(value);
        }
        if (!current.isEmpty()) records.add(new Record(current));
        return records;
    }

    private static List<String> unfold(String text) {
        List<String> result = new ArrayList<>();
        for (String raw : text.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1)) {
            if (raw.startsWith(" ") && !result.isEmpty() && !result.getLast().isEmpty()) {
                result.set(result.size() - 1, result.getLast() + raw.substring(1));
            } else {
                result.add(raw);
            }
        }
        return result;
    }

    private static String normalizeDn(String dn) {
        return dn.strip().toLowerCase(Locale.ROOT).replaceAll("\\s*,\\s*", ",");
    }

    private static Contact contact(Record r, List<String> groups) {
        Name name = new Name("", r.first("givenname"), "", r.first("sn"), "");
        String display = r.first("cn");
        if (display.isEmpty()) display = r.first("displayname");
        List<Entry> emails = new ArrayList<>();
        for (String mail : r.all("mail")) emails.add(new Entry(Kind.OTHER, mail));
        for (String mail : r.all("mozillasecondemail")) emails.add(new Entry(Kind.OTHER, mail));
        List<Entry> phones = new ArrayList<>();
        phone(phones, r, "telephonenumber", Kind.WORK);
        phone(phones, r, "homephone", Kind.HOME);
        phone(phones, r, "mobile", Kind.MOBILE);
        phone(phones, r, "facsimiletelephonenumber", Kind.FAX);
        phone(phones, r, "fax", Kind.FAX);
        phone(phones, r, "pager", Kind.OTHER);
        List<PostalAddress> addresses = new ArrayList<>();
        PostalAddress home = new PostalAddress(Kind.HOME, lines(r.first("mozillahomestreet"), r.first("mozillahomestreet2")),
                r.first("mozillahomepostalcode"), r.first("mozillahomelocalityname"), r.first("mozillahomestate"),
                r.first("mozillahomecountryname"));
        PostalAddress work = new PostalAddress(Kind.WORK, lines(first(r, "street", "postaladdress"),
                r.first("mozillaworkstreet2")), r.first("postalcode"), r.first("l"), r.first("st"),
                first(r, "c", "co"));
        if (!home.isBlank()) addresses.add(home);
        if (!work.isBlank()) addresses.add(work);
        List<String> websites = new ArrayList<>();
        for (String attribute : List.of("mozillahomeurl", "mozillaworkurl", "workurl", "homeurl", "labeleduri")) {
            websites.addAll(r.all(attribute));
        }
        Birthday birthday = null;
        try {
            int month = Integer.parseInt(r.first("birthmonth"));
            int day = Integer.parseInt(r.first("birthday"));
            String y = r.first("birthyear");
            birthday = new Birthday(y.isEmpty() ? 0 : Integer.parseInt(y), month, day);
        } catch (RuntimeException e) {
            // no or broken birthday
        }
        return new Contact(Contact.newUid(), name, display, r.first("mozillanickname"), r.first("o"), r.first("title"),
                phones, emails, addresses, websites, birthday, new TreeSet<>(groups), r.first("description"), null);
    }

    private static void phone(List<Entry> phones, Record r, String attribute, Kind kind) {
        for (String number : r.all(attribute)) phones.add(new Entry(kind, number));
    }

    private static String first(Record r, String... attributes) {
        for (String a : attributes) {
            if (!r.first(a).isEmpty()) return r.first(a);
        }
        return "";
    }

    private static String lines(String a, String b) {
        return b.isEmpty() ? a : a + "\n" + b;
    }

    // ---------------------------------------------------------------- writing

    /** LDIF for Thunderbird; every group becomes a mailing list of its members that have an e-mail address. */
    public static String write(List<StoredContact> stored) {
        StringBuilder sb = new StringBuilder();
        Map<String, List<String>> groups = new LinkedHashMap<>();
        for (StoredContact s : stored) {
            Contact c = s.contact();
            String cn = c.label();
            String dn = "cn=" + escapeDn(cn) + c.firstEmail().map(m -> ",mail=" + escapeDn(m)).orElse("");
            attribute(sb, "dn", dn);
            for (String oc : List.of("top", "person", "organizationalPerson", "inetOrgPerson", "mozillaAbPersonAlpha")) {
                attribute(sb, "objectclass", oc);
            }
            attribute(sb, "givenName", c.name().given());
            attribute(sb, "sn", c.name().family());
            attribute(sb, "cn", cn);
            attribute(sb, "mozillaNickname", c.nickname());
            if (!c.emails().isEmpty()) attribute(sb, "mail", c.emails().get(0).value());
            if (c.emails().size() > 1) attribute(sb, "mozillaSecondEmail", c.emails().get(1).value());
            phone(sb, c, Kind.WORK, "telephoneNumber");
            phone(sb, c, Kind.HOME, "homePhone");
            phone(sb, c, Kind.MOBILE, "mobile");
            phone(sb, c, Kind.FAX, "facsimiletelephonenumber");
            phone(sb, c, Kind.OTHER, "pager");
            for (PostalAddress a : c.addresses()) {
                String[] street = a.street().split("\n", 2);
                if (a.kind() == Kind.WORK) {
                    attribute(sb, "street", street[0]);
                    if (street.length > 1) attribute(sb, "mozillaWorkStreet2", street[1].replace('\n', ' '));
                    attribute(sb, "l", a.city());
                    attribute(sb, "st", a.region());
                    attribute(sb, "postalCode", a.postalCode());
                    attribute(sb, "c", a.country());
                } else {
                    attribute(sb, "mozillaHomeStreet", street[0]);
                    if (street.length > 1) attribute(sb, "mozillaHomeStreet2", street[1].replace('\n', ' '));
                    attribute(sb, "mozillaHomeLocalityName", a.city());
                    attribute(sb, "mozillaHomeState", a.region());
                    attribute(sb, "mozillaHomePostalCode", a.postalCode());
                    attribute(sb, "mozillaHomeCountryName", a.country());
                }
            }
            attribute(sb, "o", c.organization());
            attribute(sb, "title", c.jobTitle());
            if (!c.websites().isEmpty()) attribute(sb, "mozillaWorkUrl", c.websites().getFirst());
            if (c.birthday() != null) {
                if (c.birthday().hasYear()) attribute(sb, "birthyear", String.valueOf(c.birthday().year()));
                attribute(sb, "birthmonth", String.format("%02d", c.birthday().month()));
                attribute(sb, "birthday", String.format("%02d", c.birthday().day()));
            }
            attribute(sb, "description", c.note());
            sb.append('\n');
            if (c.firstEmail().isPresent()) {
                for (String g : c.groups()) groups.computeIfAbsent(g, k -> new ArrayList<>()).add(dn);
            }
        }
        groups.forEach((group, members) -> {
            attribute(sb, "dn", "cn=" + escapeDn(group));
            for (String oc : List.of("top", "groupOfNames")) attribute(sb, "objectclass", oc);
            attribute(sb, "cn", group);
            for (String member : members) attribute(sb, "member", member);
            sb.append('\n');
        });
        return sb.toString();
    }

    private static void phone(StringBuilder sb, Contact c, Kind kind, String attribute) {
        c.phones().stream().filter(p -> p.kind() == kind).findFirst().ifPresent(p -> attribute(sb, attribute, p.value()));
    }

    /** One line; values that are not plain ASCII, or start with a space, colon or "<", in Base64. */
    static void attribute(StringBuilder sb, String name, String value) {
        if (value == null || value.isEmpty()) return;
        boolean safe = value.chars().allMatch(ch -> ch >= 0x20 && ch < 0x7F)
                && !value.startsWith(" ") && !value.startsWith(":") && !value.startsWith("<") && !value.endsWith(" ");
        if (safe) {
            sb.append(name).append(": ").append(value).append('\n');
        } else {
            sb.append(name).append(":: ")
                    .append(Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8))).append('\n');
        }
    }

    private static String escapeDn(String value) {
        return value.replaceAll("([,+\"\\\\<>;=#])", "\\\\$1");
    }
}
