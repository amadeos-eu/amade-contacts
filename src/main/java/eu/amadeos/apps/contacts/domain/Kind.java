package eu.amadeos.apps.contacts.domain;

import java.util.List;
import java.util.Locale;

/**
 * What a phone number, e-mail or postal address is for. The vCard {@code TYPE} values it stands for are
 * {@link #vcardTypes()}; {@link #OTHER} has none.
 */
public enum Kind {
    MOBILE("cell"), HOME("home"), WORK("work"), FAX("fax"), OTHER();

    /** The kinds the editor offers for each kind of entry. */
    public static final List<Kind> PHONE = List.of(MOBILE, HOME, WORK, FAX, OTHER);
    public static final List<Kind> EMAIL = List.of(HOME, WORK, OTHER);
    public static final List<Kind> ADDRESS = List.of(HOME, WORK, OTHER);

    private final List<String> vcardTypes;

    Kind(String... vcardTypes) {
        this.vcardTypes = List.of(vcardTypes);
    }

    public List<String> vcardTypes() {
        return vcardTypes;
    }

    /** The text key, {@code kind.mobile} and so on. */
    public String key() {
        return "kind." + name().toLowerCase(Locale.ROOT);
    }

    /**
     * The kind of a vCard entry from its {@code TYPE} values, or of an imported label such as "Mobile",
     * "Business" or "Privat": the first one that is known wins, a fax number of work is {@link #FAX}.
     */
    public static Kind of(List<String> types) {
        Kind found = OTHER;
        for (String type : types) {
            Kind kind = ofLabel(type);
            if (kind == FAX || kind == MOBILE) return kind;
            if (found == OTHER) found = kind;
        }
        return found;
    }

    public static Kind ofLabel(String label) {
        String l = label == null ? "" : label.strip().toLowerCase(Locale.ROOT);
        if (l.startsWith("*")) l = l.substring(1).strip();
        return switch (l) {
            case "cell", "mobile", "mobil", "handy", "cellulare", "móvil", "movil", "portable", "iphone" -> MOBILE;
            case "home", "private", "privat", "personal", "maison", "domicile", "casa", "personnel", "personale" -> HOME;
            case "work", "business", "office", "arbeit", "geschäftlich", "dienstlich", "büro", "travail", "bureau",
                 "trabajo", "lavoro", "ufficio" -> WORK;
            case "fax", "home fax", "work fax", "business fax" -> FAX;
            default -> OTHER;
        };
    }
}
