package eu.amadeos.apps.contacts.ui.transfer;

import eu.amadeos.apps.contacts.domain.Source;
import eu.amadeos.apps.contacts.io.Imported;

import java.util.List;
import java.util.Locale;
import java.util.function.ToIntFunction;

/**
 * What the import dialog needs besides the file.
 *
 * @param books      the address books to choose from, the first is preselected
 * @param groupName  the suggested group for the imported contacts, "Imported 2026-10-23"
 * @param duplicates how many contacts share an e-mail address with known ones
 */
public record ImportPreview(List<Source.Book> books, String groupName, ToIntFunction<List<Imported>> duplicates,
                            Locale locale) {
}
