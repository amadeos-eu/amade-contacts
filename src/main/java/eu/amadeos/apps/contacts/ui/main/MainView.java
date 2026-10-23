package eu.amadeos.apps.contacts.ui.main;

import eu.amadeos.apps.contacts.domain.Contact;
import eu.amadeos.apps.contacts.domain.Settings;
import eu.amadeos.apps.contacts.domain.StoredContact;
import eu.amadeos.apps.contacts.ui.transfer.ExportChoice;
import eu.amadeos.apps.contacts.ui.transfer.ImportChoice;
import eu.amadeos.apps.contacts.ui.transfer.ImportPreview;
import eu.amadeos.apps.contacts.io.ImportFile;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/** The window as the presenter sees it. All methods run on the EDT. */
public interface MainView {
    void showSettings(Settings settings);

    /** The list on the left and which line is selected. */
    void showSources(List<SourceItem> sources, int selected);

    /**
     * The contacts of the selected line that match the search, and which of them are selected.
     *
     * @param birthdays true to show the next birthday under the names instead of an address
     */
    void showContacts(List<StoredContact> contacts, List<String> selectedUids, boolean birthdays, String status);

    /** The card on the right: one contact, or nothing (none or several selected). */
    void showContact(Optional<StoredContact> contact, String placeholder);

    /** The search field, after the presenter cleared it. */
    void showQuery(String query);

    /** The contact editor; empty if cancelled. */
    Optional<Contact> edit(Contact contact, boolean isNew, List<String> knownGroups);

    boolean confirmDelete(List<String> names);

    Optional<Path> chooseImportFile();

    /** The import dialog for a file; empty if cancelled. */
    Optional<ImportChoice> chooseImport(ImportFile file, ImportPreview preview);

    /** The export dialog; empty if cancelled. */
    Optional<ExportChoice> chooseExport(int selected, int shown, String suggestedName);

    void copyText(String text);

    /** Text on the clipboard, if any. */
    Optional<String> clipboardText();

    void focusSearch();

    void showError(String message);

    void showInfo(String message);

    void showAbout();

    void close();
}
