package eu.amadeos.apps.contacts.io;

import java.util.Locale;

/** What a column of an imported table holds. Phone, e-mail and address parts come with a {@code Kind}. */
public enum Field {
    IGNORE, DISPLAY_NAME, PREFIX, GIVEN, MIDDLE, FAMILY, SUFFIX, NICKNAME, ORGANIZATION, JOB_TITLE,
    EMAIL, PHONE, STREET, POSTAL_CODE, CITY, REGION, COUNTRY, WEBSITE, BIRTHDAY, NOTE, GROUPS;

    /** True for the fields that are for home, work and so on. */
    public boolean hasKind() {
        return this == EMAIL || this == PHONE || isAddress();
    }

    public boolean isAddress() {
        return this == STREET || this == POSTAL_CODE || this == CITY || this == REGION || this == COUNTRY;
    }

    /** The text key, {@code field.given} and so on. */
    public String key() {
        return "field." + name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
