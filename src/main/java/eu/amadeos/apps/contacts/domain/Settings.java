package eu.amadeos.apps.contacts.domain;

/**
 * What the user chose in the menus, kept between starts.
 *
 * @param sortByFamilyName sort and show "Berger, Anna" instead of "Anna Berger"
 */
public record Settings(boolean sortByFamilyName) {
    public static final Settings DEFAULT = new Settings(false);

    public Settings withSortByFamilyName(boolean value) {
        return new Settings(value);
    }
}
