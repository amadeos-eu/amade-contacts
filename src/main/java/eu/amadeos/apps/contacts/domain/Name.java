package eu.amadeos.apps.contacts.domain;

import java.util.ArrayList;
import java.util.List;

/** The parts of a person's name, the vCard {@code N} property. Every part may be empty. */
public record Name(String prefix, String given, String additional, String family, String suffix) {
    public static final Name EMPTY = new Name("", "", "", "", "");

    public Name {
        prefix = clean(prefix);
        given = clean(given);
        additional = clean(additional);
        family = clean(family);
        suffix = clean(suffix);
    }

    public static Name of(String given, String family) {
        return new Name("", given, "", family, "");
    }

    public boolean isBlank() {
        return (prefix + given + additional + family + suffix).isBlank();
    }

    /** "Dr. Anna Maria Berger Jr.": the parts in reading order. */
    public String full() {
        List<String> parts = new ArrayList<>();
        for (String part : List.of(prefix, given, additional, family, suffix)) {
            if (!part.isEmpty()) parts.add(part);
        }
        return String.join(" ", parts);
    }

    /** "Berger, Anna": for lists sorted by last name. */
    public String familyFirst() {
        if (family.isEmpty()) return full();
        String rest = String.join(" ", List.of(given, additional).stream().filter(p -> !p.isEmpty()).toList());
        return rest.isEmpty() ? family : family + ", " + rest;
    }

    /**
     * A name typed in one line, as in "Anna Berger", "Berger, Anna" or "Dr. Anna Maria Berger": the last word is
     * the family name unless a comma comes first.
     */
    public static Name parse(String text) {
        String t = text == null ? "" : text.strip().replaceAll("\\s+", " ");
        if (t.isEmpty()) return EMPTY;
        int comma = t.indexOf(',');
        if (comma > 0) {
            return new Name("", t.substring(comma + 1).strip(), "", t.substring(0, comma).strip(), "");
        }
        List<String> words = new ArrayList<>(List.of(t.split(" ")));
        String prefix = "";
        if (words.size() > 1 && words.getFirst().endsWith(".")) prefix = words.removeFirst();
        if (words.size() == 1) return new Name(prefix, words.getFirst(), "", "", "");
        String family = words.removeLast();
        String given = words.removeFirst();
        return new Name(prefix, given, String.join(" ", words), family, "");
    }

    private static String clean(String s) {
        return s == null ? "" : s.strip();
    }
}
