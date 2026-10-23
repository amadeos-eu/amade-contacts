package eu.amadeos.apps.contacts.domain;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;

/**
 * A person or an organization as the address book shows and edits it. What a vCard holds beyond this (instant
 * messaging, keys, properties of other programs) stays in the stored card and is written back unchanged.
 *
 * @param uid         the vCard {@code UID}, unique in the address book
 * @param displayName the vCard {@code FN}; may be empty, then {@link #label()} makes one up
 * @param birthday    null if unknown
 * @param photo       null if none
 */
public record Contact(String uid, Name name, String displayName, String nickname, String organization,
                      String jobTitle, List<Entry> phones, List<Entry> emails, List<PostalAddress> addresses,
                      List<String> websites, Birthday birthday, SortedSet<String> groups, String note, Photo photo) {

    public Contact {
        Objects.requireNonNull(uid, "uid");
        name = name == null ? Name.EMPTY : name;
        displayName = clean(displayName);
        nickname = clean(nickname);
        organization = clean(organization);
        jobTitle = clean(jobTitle);
        phones = phones == null ? List.of() : phones.stream().filter(e -> !e.isBlank()).toList();
        emails = emails == null ? List.of() : emails.stream().filter(e -> !e.isBlank()).toList();
        addresses = addresses == null ? List.of() : addresses.stream().filter(a -> !a.isBlank()).toList();
        websites = websites == null ? List.of() : websites.stream().map(String::strip).filter(w -> !w.isEmpty()).toList();
        groups = groups == null ? new TreeSet<>() : cleanGroups(groups);
        note = note == null ? "" : note.strip();
    }

    /** A new, empty contact with a fresh UID. */
    public static Contact create() {
        return new Contact(newUid(), Name.EMPTY, "", "", "", "", List.of(), List.of(), List.of(), List.of(), null,
                null, "", null);
    }

    public static String newUid() {
        return "urn:uuid:" + UUID.randomUUID();
    }

    @Override
    public SortedSet<String> groups() {
        return new TreeSet<>(groups);
    }

    /**
     * The name to show: the display name, else the name from its parts, the organization, the nickname,
     * the first e-mail address or phone number; empty only for a contact without anything.
     */
    public String label() {
        if (!displayName.isEmpty()) return displayName;
        if (!name.isBlank()) return name.full();
        if (!organization.isEmpty()) return organization;
        if (!nickname.isEmpty()) return nickname;
        if (!emails.isEmpty()) return emails.getFirst().value();
        if (!phones.isEmpty()) return phones.getFirst().value();
        return "";
    }

    /** The text the list sorts by: the family name first if {@code byFamilyName} and the contact has one. */
    public String sortKey(boolean byFamilyName) {
        if (byFamilyName && !name.family().isEmpty()) return name.familyFirst();
        return label();
    }

    /** True for an organization without a person's name: shown with the company line only. */
    public boolean isOrganization() {
        return name.isBlank() && displayName.equals(organization) && !organization.isEmpty();
    }

    public Optional<String> firstEmail() {
        return emails.isEmpty() ? Optional.empty() : Optional.of(emails.getFirst().value());
    }

    /** True if the contact has this e-mail address, ignoring case. */
    public boolean hasEmail(String address) {
        return emails.stream().anyMatch(e -> e.value().equalsIgnoreCase(address.strip()));
    }

    /**
     * True if every word of the query is found in the name, nickname, organization, job title, an e-mail address,
     * a postal address or a group, ignoring case and accents; digits also match phone numbers whatever the spaces,
     * dashes and brackets in them.
     */
    public boolean matches(String query) {
        String q = fold(query);
        if (q.isBlank()) return true;
        StringBuilder text = new StringBuilder();
        for (String s : List.of(label(), name.full(), nickname, organization, jobTitle, note)) text.append(s).append('\n');
        emails.forEach(e -> text.append(e.value()).append('\n'));
        addresses.forEach(a -> text.append(a.oneLine()).append('\n'));
        groups.forEach(g -> text.append(g).append('\n'));
        String haystack = fold(text.toString());
        StringBuilder digits = new StringBuilder();
        phones.forEach(p -> digits.append(digitsOf(p.value())).append('\n'));
        for (String word : q.strip().split("\\s+")) {
            String wordDigits = digitsOf(word);
            boolean phone = wordDigits.length() >= 3 && wordDigits.length() >= word.replaceAll("[\\s()+/.-]", "").length();
            if (!haystack.contains(word) && !(phone && digits.toString().contains(wordDigits))) return false;
        }
        return true;
    }

    /** The initials for the picture placeholder: "AB" for Anna Berger, "L" for Lindner &amp; Partner. */
    public String initials() {
        if (!name.given().isEmpty() || !name.family().isEmpty()) {
            return (first(name.given()) + first(name.family())).toUpperCase(Locale.ROOT);
        }
        String[] words = label().split("[\\s@.]+");
        if (words.length == 0 || words[0].isEmpty()) return "?";
        boolean company = label().equals(organization);
        return (first(words[0]) + (words.length > 1 && !company ? first(words[words.length - 1]) : ""))
                .toUpperCase(Locale.ROOT);
    }

    private static String first(String s) {
        return s.isEmpty() ? "" : new String(Character.toChars(s.codePointAt(0)));
    }

    // ---------------------------------------------------------------- copies with one part changed

    public Contact withGroups(Collection<String> newGroups) {
        return new Contact(uid, name, displayName, nickname, organization, jobTitle, phones, emails, addresses,
                websites, birthday, new TreeSet<>(newGroups), note, photo);
    }

    public Contact withDisplayName(String newDisplayName) {
        return new Contact(uid, name, newDisplayName, nickname, organization, jobTitle, phones, emails, addresses,
                websites, birthday, groups, note, photo);
    }

    public Contact withUid(String newUid) {
        return new Contact(newUid, name, displayName, nickname, organization, jobTitle, phones, emails, addresses,
                websites, birthday, groups, note, photo);
    }

    /**
     * This contact with what {@code other} has and this one lacks: further phone numbers, e-mail and postal
     * addresses, websites and groups, and the name, organization, job title, birthday, photo and note if empty
     * here. Used when an imported contact has an e-mail address that is already in the address book.
     */
    public Contact mergedWith(Contact other) {
        List<Entry> p = new ArrayList<>(phones);
        for (Entry e : other.phones) {
            if (p.stream().noneMatch(x -> digitsOf(x.value()).equals(digitsOf(e.value())))) p.add(e);
        }
        List<Entry> m = new ArrayList<>(emails);
        for (Entry e : other.emails) {
            if (!hasEmail(e.value())) m.add(e);
        }
        List<PostalAddress> a = new ArrayList<>(addresses);
        for (PostalAddress x : other.addresses) {
            if (a.stream().noneMatch(y -> fold(y.oneLine()).equals(fold(x.oneLine())))) a.add(x);
        }
        List<String> w = new ArrayList<>(websites);
        for (String x : other.websites) {
            if (!w.contains(x)) w.add(x);
        }
        TreeSet<String> g = new TreeSet<>(groups);
        g.addAll(other.groups);
        String n = note.isEmpty() ? other.note : other.note.isEmpty() || note.contains(other.note) ? note : note + "\n" + other.note;
        return new Contact(uid, name.isBlank() ? other.name : name, displayName.isEmpty() && name.isBlank() ? other.displayName : displayName,
                nickname.isEmpty() ? other.nickname : nickname, organization.isEmpty() ? other.organization : organization,
                jobTitle.isEmpty() ? other.jobTitle : jobTitle, p, m, a, w, birthday == null ? other.birthday : birthday,
                g, n, photo == null ? other.photo : photo);
    }

    // ---------------------------------------------------------------- helpers

    /** Lower case without accents, so "muller" finds "Müller" and "jose" finds "José". */
    public static String fold(String s) {
        if (s == null) return "";
        String lower = s.toLowerCase(Locale.ROOT).replace("ß", "ss");
        String decomposed = Normalizer.normalize(lower, Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}", "");
    }

    public static String digitsOf(String s) {
        return s.replaceAll("[^0-9]", "");
    }

    private static String clean(String s) {
        return s == null ? "" : s.strip();
    }

    private static SortedSet<String> cleanGroups(Collection<String> groups) {
        TreeSet<String> result = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (String g : groups) {
            if (g != null && !g.isBlank()) result.add(g.strip());
        }
        return result;
    }
}
