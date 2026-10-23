package eu.amadeos.apps.contacts.io;

import eu.amadeos.apps.contacts.domain.Contact;
import eu.amadeos.apps.contacts.domain.Kind;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Which field each column of an imported table fills. {@link #guess} reads the column titles of Google Contacts,
 * Outlook and most hand-made tables in the five languages; the user can change every column before the import.
 */
public record ColumnMapping(List<Target> targets) {
    /**
     * @param group       columns of the same group form one address (Google's "Address 1 - ...")
     * @param labelColumn a column that says what this entry is for ("E-mail 1 - Label"), or -1; used when
     *                    {@code kind} is {@link Kind#OTHER}
     */
    public record Target(Field field, Kind kind, String group, int labelColumn) {
        public static final Target IGNORED = new Target(Field.IGNORE, Kind.OTHER, "", -1);

        public Target {
            Objects.requireNonNull(field, "field");
            kind = kind == null ? Kind.OTHER : kind;
            group = group == null ? "" : group;
        }

        public Target with(Field newField, Kind newKind) {
            return new Target(newField, newKind, group, labelColumn);
        }
    }

    public ColumnMapping {
        targets = List.copyOf(targets);
    }

    public Target target(int column) {
        return column < targets.size() ? targets.get(column) : Target.IGNORED;
    }

    public ColumnMapping with(int column, Field field, Kind kind) {
        List<Target> list = new ArrayList<>(targets);
        list.set(column, list.get(column).with(field, kind));
        return new ColumnMapping(list);
    }

    // ---------------------------------------------------------------- guessing

    private static final Pattern NUMBERED = Pattern.compile("(.*?)\\s+-\\s+(.*)");
    private static final Set<String> WORK = Set.of("work", "business", "office", "geschaftlich", "dienstlich", "arbeit",
            "firma", "buro", "travail", "bureau", "professionnel", "trabajo", "lavoro", "ufficio");
    private static final Set<String> HOME = Set.of("home", "private", "privat", "personal", "domicile", "maison",
            "personnel", "casa", "personale");

    public static ColumnMapping guess(List<String> headers) {
        List<Target> targets = new ArrayList<>();
        for (int i = 0; i < headers.size(); i++) targets.add(guess(headers, i));
        return new ColumnMapping(targets);
    }

    private static Target guess(List<String> headers, int column) {
        String header = headers.get(column);
        String group = "";
        int labelColumn = -1;
        Matcher m = NUMBERED.matcher(header.strip());
        if (m.matches()) {
            group = m.group(1);
            String part = Contact.fold(m.group(2));
            if (part.equals("label") || part.equals("type")) return Target.IGNORED;
            for (int j = 0; j < headers.size(); j++) {
                Matcher other = NUMBERED.matcher(headers.get(j).strip());
                if (other.matches() && other.group(1).equals(group)
                        && Set.of("label", "type").contains(Contact.fold(other.group(2)))) {
                    labelColumn = j;
                }
            }
        }
        List<String> words = words(header);
        Field field = field(words);
        if (field == Field.IGNORE) return Target.IGNORED;
        // --8<-- [start:kind]
        Kind kind = Kind.OTHER;
        if (field.hasKind()) {
            if (field == Field.PHONE && any(words, "mobile", "mobil", "mobiltelefon", "handy", "cell", "cellular",
                    "portable", "cellulare", "movil")) kind = Kind.MOBILE;
            else if (field == Field.PHONE && any(words, "fax", "telefax")) kind = Kind.FAX;
            else if (anyIn(words, WORK) || words.stream().anyMatch(w -> w.contains("geschaft"))) kind = Kind.WORK;
            else if (anyIn(words, HOME)) kind = Kind.HOME;
            if (group.isEmpty()) group = field.isAddress() ? kind.name() : header;
        }
        // --8<-- [end:kind]
        return new Target(field, kind, group, labelColumn);
    }

    private static Field field(List<String> w) {
        if (any(w, "phonetic", "yomi", "formatted", "display", "anzeigename", "anrede", "salutation", "department",
                "abteilung", "angezeigter", "photo", "foto", "file")) {
            return any(w, "display", "anzeigename") && !any(w, "mail", "email", "e") ? Field.DISPLAY_NAME : Field.IGNORE;
        }
        if (any(w, "mail", "email", "courriel", "correo", "posta")) {
            return any(w, "type", "typ", "kind") ? Field.IGNORE : Field.EMAIL;
        }
        if (any(w, "phone", "telephone", "telefon", "tel", "fon", "mobile", "mobil", "mobiltelefon", "handy", "cell",
                "fax", "telefax", "pager", "portable", "telefono", "cellulare", "movil")) return Field.PHONE;
        if (any(w, "street", "strasse", "str", "rue", "adresse", "calle", "direccion", "via", "indirizzo", "box",
                "extended")) {
            return Field.STREET;
        }
        if (any(w, "postal", "zip", "plz", "postleitzahl", "cap", "cp") || (any(w, "code") && any(w, "postal"))) {
            return Field.POSTAL_CODE;
        }
        if (any(w, "city", "ort", "stadt", "ville", "ciudad", "citta", "localidad", "locality", "wohnort")) return Field.CITY;
        if (any(w, "country", "land", "pays", "pais", "paese", "nazione")) return Field.COUNTRY;
        if (any(w, "state", "region", "bundesland", "province", "provincia", "departement")) return Field.REGION;
        if (any(w, "birthday", "birth", "geburtstag", "geburtsdatum", "anniversaire", "naissance", "cumpleanos",
                "nacimiento", "compleanno", "nascita", "bday")) return Field.BIRTHDAY;
        if (any(w, "web", "website", "url", "homepage", "webseite", "internet", "site", "sito")) return Field.WEBSITE;
        if (any(w, "notes", "note", "notiz", "notizen", "bemerkung", "bemerkungen", "kommentar", "comment", "comments",
                "remarque", "remarques", "nota", "notas")) return Field.NOTE;
        if (any(w, "labels", "label", "group", "groups", "membership", "categories", "category", "kategorie",
                "kategorien", "gruppe", "gruppen", "groupe", "groupes", "grupo", "grupos", "gruppo", "gruppi",
                "etichette")) return Field.GROUPS;
        if (any(w, "title", "position", "beruf", "fonction", "cargo", "qualifica", "job", "titel")) {
            return any(w, "job", "organization", "organisation", "company", "position", "beruf", "fonction", "cargo",
                    "qualifica") ? Field.JOB_TITLE : Field.PREFIX;
        }
        if (any(w, "company", "organization", "organisation", "firma", "unternehmen", "societe", "empresa", "azienda")) {
            return Field.ORGANIZATION;
        }
        if (any(w, "prefix", "prafix", "prefixe", "prefijo", "prefisso")) return Field.PREFIX;
        if (any(w, "suffix", "suffixe", "sufijo", "suffisso", "namenszusatz")) return Field.SUFFIX;
        if (any(w, "nickname", "spitzname", "surnom", "apodo", "soprannome")) return Field.NICKNAME;
        if (any(w, "middle", "additional") || (any(w, "zweiter") && any(w, "vorname"))) return Field.MIDDLE;
        if (any(w, "first", "given", "firstname", "vorname", "prenom", "nombre", "nome", "forename")) return Field.GIVEN;
        if (any(w, "last", "surname", "family", "lastname", "nachname", "familienname", "nom", "apellido", "apellidos",
                "cognome")) return Field.FAMILY;
        if (any(w, "name", "fullname")) return Field.DISPLAY_NAME;
        return Field.IGNORE;
    }

    /** The words of a column title, folded: "E-mail 2 Address" gives e, mail, 2, address. */
    static List<String> words(String header) {
        List<String> result = new ArrayList<>();
        for (String word : Contact.fold(header).split("[^a-z0-9]+")) {
            if (!word.isEmpty()) result.add(word);
        }
        if (Contact.fold(header).contains("e-mail") || Contact.fold(header).contains("e mail")) result.add("mail");
        return result;
    }

    private static boolean any(List<String> words, String... candidates) {
        for (String c : candidates) {
            if (words.contains(c)) return true;
        }
        return false;
    }

    private static boolean anyIn(List<String> words, Set<String> candidates) {
        return words.stream().anyMatch(candidates::contains);
    }

    /** The kind a label cell names, for {@link Target#labelColumn()}. */
    static Kind kindOfLabel(String label) {
        String l = label.toLowerCase(Locale.ROOT);
        if (l.contains("fax")) return Kind.FAX;
        return Kind.ofLabel(l.split(":::")[0]);
    }
}
