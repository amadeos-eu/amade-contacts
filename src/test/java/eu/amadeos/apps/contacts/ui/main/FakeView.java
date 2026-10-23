package eu.amadeos.apps.contacts.ui.main;

import eu.amadeos.apps.contacts.domain.Contact;
import eu.amadeos.apps.contacts.domain.Settings;
import eu.amadeos.apps.contacts.domain.StoredContact;
import eu.amadeos.apps.contacts.io.ImportFile;
import eu.amadeos.apps.contacts.ui.transfer.ExportChoice;
import eu.amadeos.apps.contacts.ui.transfer.ImportChoice;
import eu.amadeos.apps.contacts.ui.transfer.ImportPreview;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.UnaryOperator;

/** Remembers what the presenter showed and answers the dialogs as the test says. */
final class FakeView implements MainView {
    Settings settings;
    List<SourceItem> sources = List.of();
    int sourceIndex = -1;
    List<StoredContact> contacts = List.of();
    List<String> selected = List.of();
    boolean birthdays;
    String status = "";
    Optional<StoredContact> card = Optional.empty();
    String placeholder = "";
    String query;
    final List<String> errors = new ArrayList<>();
    final List<String> infos = new ArrayList<>();
    String copied;
    boolean closed;

    /** What the editor returns for a contact; empty means cancelled. */
    UnaryOperator<Contact> editor = c -> null;
    Contact edited;
    boolean editedIsNew;
    List<String> editorGroups;
    boolean deleteAnswer = true;
    List<String> deleteAsked;
    Optional<Path> importFile = Optional.empty();
    BiFunction<ImportFile, ImportPreview, ImportChoice> importer = (f, p) -> null;
    ImportPreview preview;
    ExportChoice exportChoice;
    String clipboard;

    List<String> names() {
        return contacts.stream().map(c -> c.contact().label()).toList();
    }

    List<String> sourceLabels() {
        return sources.stream().map(s -> s.label() + " " + s.count()).toList();
    }

    @Override
    public void showSettings(Settings newSettings) {
        settings = newSettings;
    }

    @Override
    public void showSources(List<SourceItem> newSources, int selectedIndex) {
        sources = newSources;
        sourceIndex = selectedIndex;
    }

    @Override
    public void showContacts(List<StoredContact> newContacts, List<String> selectedUids, boolean newBirthdays,
                             String newStatus) {
        contacts = newContacts;
        selected = selectedUids;
        birthdays = newBirthdays;
        status = newStatus;
    }

    @Override
    public void showContact(Optional<StoredContact> contact, String newPlaceholder) {
        card = contact;
        placeholder = newPlaceholder;
    }

    @Override
    public void showQuery(String newQuery) {
        query = newQuery;
    }

    @Override
    public Optional<Contact> edit(Contact contact, boolean isNew, List<String> knownGroups) {
        edited = contact;
        editedIsNew = isNew;
        editorGroups = knownGroups;
        return Optional.ofNullable(editor.apply(contact));
    }

    @Override
    public boolean confirmDelete(List<String> names) {
        deleteAsked = names;
        return deleteAnswer;
    }

    @Override
    public Optional<Path> chooseImportFile() {
        return importFile;
    }

    @Override
    public Optional<ImportChoice> chooseImport(ImportFile file, ImportPreview newPreview) {
        preview = newPreview;
        return Optional.ofNullable(importer.apply(file, newPreview));
    }

    @Override
    public Optional<ExportChoice> chooseExport(int selectedCount, int shown, String suggestedName) {
        return Optional.ofNullable(exportChoice);
    }

    @Override
    public void copyText(String text) {
        copied = text;
    }

    @Override
    public Optional<String> clipboardText() {
        return Optional.ofNullable(clipboard);
    }

    @Override
    public void focusSearch() {
    }

    @Override
    public void showError(String message) {
        errors.add(message);
    }

    @Override
    public void showInfo(String message) {
        infos.add(message);
    }

    @Override
    public void showAbout() {
    }

    @Override
    public void close() {
        closed = true;
    }
}
