package eu.amadeos.apps.contacts.ui.main;

import eu.amadeos.apps.contacts.domain.Birthday;
import eu.amadeos.apps.contacts.domain.Contact;
import eu.amadeos.apps.contacts.domain.Entry;
import eu.amadeos.apps.contacts.domain.PostalAddress;
import eu.amadeos.apps.contacts.domain.Settings;
import eu.amadeos.apps.contacts.domain.Source;
import eu.amadeos.apps.contacts.domain.StoredContact;
import eu.amadeos.apps.contacts.io.ColumnMapping;
import eu.amadeos.apps.contacts.io.Exports;
import eu.amadeos.apps.contacts.io.FileFormat;
import eu.amadeos.apps.contacts.io.ImportFile;
import eu.amadeos.apps.contacts.io.Imported;
import eu.amadeos.apps.contacts.repository.AddressBooks;
import eu.amadeos.apps.contacts.repository.ContactsPreferences;
import eu.amadeos.apps.contacts.service.ContactImport;
import eu.amadeos.apps.contacts.ui.transfer.ExportChoice;
import eu.amadeos.apps.contacts.ui.transfer.ImportChoice;
import eu.amadeos.apps.contacts.ui.transfer.ImportPreview;
import eu.amadeos.commons.desktop.DesktopLauncher;
import eu.amadeos.commons.i18n.Language;
import eu.amadeos.commons.i18n.LocaleSettings;
import eu.amadeos.commons.i18n.Messages;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Collator;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Everything the window does, without Swing: which contacts the list shows, editing, deleting, import and export,
 * and handing addresses to the mail program, the browser and the map.
 */
public class MainPresenter {
    private static final Logger LOG = LoggerFactory.getLogger(MainPresenter.class);

    /** What the presenter works with, so tests can pass their own. */
    public record Services(AddressBooks books, ContactsPreferences preferences, DesktopLauncher launcher, Clock clock,
                           Runnable exit) {
    }

    private final MainView view;
    private final Messages msg;
    private final LocaleSettings locales;
    private final Services services;
    private final Collator collator;
    private Settings settings = Settings.DEFAULT;
    private Source source = new Source.All();
    private String query = "";
    private List<StoredContact> shown = List.of();
    private List<String> selected = List.of();

    public MainPresenter(MainView view, Messages msg, LocaleSettings locales, Services services) {
        this.view = view;
        this.msg = msg;
        this.locales = locales;
        this.services = services;
        this.collator = Collator.getInstance(msg.locale());
        collator.setStrength(Collator.SECONDARY);
    }

    /** Reads the address books and shows them; files from the command line are imported. */
    public void start(List<Path> files) {
        settings = services.preferences().settings();
        view.showSettings(settings);
        load();
        refresh();
        for (Path file : files) importFile(file);
    }

    private boolean load() {
        try {
            return services.books().reload();
        } catch (UncheckedIOException e) {
            view.showError(msg.format("error.read", services.books().root(), e.getCause().getMessage()));
            return false;
        }
    }

    /** Reads files changed by other programs; called when the window is activated. */
    public void reloadIfChanged() {
        if (load()) refresh();
    }

    public void reload() {
        load();
        refresh();
    }

    // ---------------------------------------------------------------- what the list shows

    public void selectSource(Source newSource) {
        if (newSource == null || newSource.equals(source)) return;
        source = newSource;
        refresh();
    }

    public void search(String text) {
        String q = text == null ? "" : text;
        if (q.equals(query)) return;
        query = q;
        refresh();
    }

    public void selectContacts(List<String> uids) {
        selected = List.copyOf(uids);
        showCard();
    }

    public void focusSearch() {
        view.focusSearch();
    }

    private void refresh() {
        LocalDate today = LocalDate.now(services.clock());
        List<StoredContact> all = services.books().contacts();
        List<SourceItem> items = sources(all, today);
        if (items.stream().noneMatch(i -> i.source().equals(source))) source = new Source.All();
        int index = 0;
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).source().equals(source)) index = i;
        }
        view.showSources(items, index);

        Source current = source;
        List<StoredContact> list = new ArrayList<>(all.stream()
                .filter(c -> current.includes(c, today) && c.contact().matches(query)).toList());
        boolean birthdays = current instanceof Source.Birthdays;
        if (birthdays) {
            list.sort(Comparator.comparingLong(c -> c.contact().birthday().daysUntil(today)));
        } else {
            list.sort(Comparator.comparing((StoredContact c) -> c.contact().sortKey(settings.sortByFamilyName()), collator)
                    .thenComparing(StoredContact::uid));
        }
        shown = List.copyOf(list);
        Set<String> uids = new LinkedHashSet<>();
        shown.forEach(c -> uids.add(c.uid()));
        List<String> keep = selected.stream().filter(uids::contains).toList();
        if (keep.isEmpty() && !shown.isEmpty()) keep = List.of(shown.getFirst().uid());
        selected = keep;
        String status = query.isBlank() && current instanceof Source.All ? msg.format("status.all", all.size())
                : msg.format("status.shown", shown.size(), all.size());
        view.showContacts(shown, selected, birthdays, status);
        showCard();
    }

    private List<SourceItem> sources(List<StoredContact> all, LocalDate today) {
        List<SourceItem> items = new ArrayList<>();
        items.add(new SourceItem(new Source.All(), msg.get("source.all"), all.size()));
        List<Source.Book> books = services.books().books();
        if (books.size() > 1) {
            for (Source.Book book : books) {
                items.add(new SourceItem(book, book.name(), count(all, book, today)));
            }
        }
        TreeSet<String> groups = new TreeSet<>(collator);
        all.forEach(c -> groups.addAll(c.contact().groups()));
        for (String group : groups) {
            Source.Group g = new Source.Group(group);
            items.add(new SourceItem(g, group, count(all, g, today)));
        }
        Source.Birthdays birthdays = new Source.Birthdays();
        items.add(new SourceItem(birthdays, msg.format("source.birthdays", Source.BIRTHDAY_DAYS),
                count(all, birthdays, today)));
        return items;
    }

    private static int count(List<StoredContact> all, Source source, LocalDate today) {
        return (int) all.stream().filter(c -> source.includes(c, today)).count();
    }

    private void showCard() {
        if (selected.size() == 1) {
            Optional<StoredContact> contact = shown.stream().filter(c -> c.uid().equals(selected.getFirst())).findFirst();
            view.showContact(contact, "");
        } else {
            view.showContact(Optional.empty(), selected.isEmpty() ? (shown.isEmpty() && !query.isBlank()
                    ? msg.format("card.nothing-found", query) : msg.get("card.none"))
                    : msg.format("card.several", selected.size()));
        }
    }

    private List<StoredContact> selectedContacts() {
        return shown.stream().filter(c -> selected.contains(c.uid())).toList();
    }

    private Optional<StoredContact> selectedContact() {
        List<StoredContact> list = selectedContacts();
        return list.size() == 1 ? Optional.of(list.getFirst()) : Optional.empty();
    }

    // ---------------------------------------------------------------- editing

    public void newContact() {
        Contact fresh = Contact.create();
        if (source instanceof Source.Group group) fresh = fresh.withGroups(List.of(group.name()));
        String book = source instanceof Source.Book b ? b.id() : services.books().books().getFirst().id();
        Optional<Contact> edited = view.edit(fresh, true, knownGroups());
        if (edited.isEmpty() || edited.get().label().isEmpty()) return;
        save(book, edited.get());
    }

    public void edit() {
        Optional<StoredContact> contact = selectedContact();
        if (contact.isEmpty()) return;
        view.edit(contact.get().contact(), false, knownGroups())
                .ifPresent(changed -> save(contact.get().book(), changed));
    }

    private void save(String book, Contact contact) {
        try {
            StoredContact saved = services.books().save(book, contact);
            if (!(source instanceof Source.All) && !source.includes(saved, LocalDate.now(services.clock()))) {
                source = new Source.All();
            }
            query = "";
            view.showQuery("");
            selected = List.of(saved.uid());
            refresh();
        } catch (UncheckedIOException e) {
            view.showError(msg.format("error.save", contact.label(), e.getCause().getMessage()));
        }
    }

    private List<String> knownGroups() {
        TreeSet<String> groups = new TreeSet<>(collator);
        services.books().contacts().forEach(c -> groups.addAll(c.contact().groups()));
        return List.copyOf(groups);
    }

    public void delete() {
        List<StoredContact> list = selectedContacts();
        if (list.isEmpty()) return;
        if (!view.confirmDelete(list.stream().map(c -> c.contact().label()).toList())) return;
        int index = shown.indexOf(list.getFirst());
        for (StoredContact c : list) {
            try {
                services.books().delete(c.uid());
            } catch (UncheckedIOException e) {
                view.showError(msg.format("error.delete", c.contact().label(), e.getCause().getMessage()));
            }
        }
        List<StoredContact> rest = shown.stream().filter(c -> !list.contains(c)).toList();
        selected = rest.isEmpty() ? List.of() : List.of(rest.get(Math.min(index, rest.size() - 1)).uid());
        refresh();
    }

    // ---------------------------------------------------------------- reaching people

    /** A new message to the selected contacts' first addresses. */
    public void writeMail() {
        List<String> addresses = new ArrayList<>();
        for (StoredContact c : selectedContacts()) {
            c.contact().firstEmail().ifPresent(mail -> addresses.add(mailbox(c.contact().label(), mail)));
        }
        if (addresses.isEmpty()) {
            view.showInfo(msg.get("info.no-email"));
            return;
        }
        browse("mailto:" + String.join(",", addresses.stream().map(MainPresenter::encodeMailbox).toList()));
    }

    public void writeMail(String address) {
        String name = selectedContact().map(c -> c.contact().label()).orElse("");
        browse("mailto:" + encodeMailbox(mailbox(name, address)));
    }

    /** "Anna Berger &lt;anna@example.org&gt;", with quotes if the name has special characters. */
    // --8<-- [start:mailbox]
    static String mailbox(String name, String address) {
        if (name.isBlank() || name.equalsIgnoreCase(address)) return address;
        String n = name.matches("[\\p{L}\\p{N} .'-]*") ? name : "\"" + name.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        return n + " <" + address + ">";
    }
    // --8<-- [end:mailbox]

    private static String encodeMailbox(String mailbox) {
        return URLEncoder.encode(mailbox, StandardCharsets.UTF_8).replace("+", "%20").replace("%40", "@");
    }

    /** Dials with the program for {@code tel:} links; without one, copies the number. */
    public void call() {
        selectedContact().flatMap(c -> c.contact().phones().stream().findFirst()).ifPresentOrElse(
                p -> call(p.value()), () -> view.showInfo(msg.get("info.no-phone")));
    }

    public void call(String number) {
        String digits = number.replaceAll("[^0-9+*#]", "");
        try {
            services.launcher().browse("tel:" + digits);
        } catch (IOException | RuntimeException e) {
            view.copyText(number);
            view.showInfo(msg.format("info.number-copied", number));
        }
    }

    public void showOnMap(PostalAddress address) {
        browse("https://www.openstreetmap.org/search?query="
                + URLEncoder.encode(address.oneLine(), StandardCharsets.UTF_8));
    }

    public void openWebsite(String url) {
        browse(url.matches("(?i)[a-z][a-z0-9+.-]*:.*") ? url : "https://" + url);
    }

    private void browse(String uri) {
        try {
            services.launcher().browse(uri);
        } catch (IOException | RuntimeException e) {
            view.showError(msg.format("error.open", uri, e.getMessage()));
        }
    }

    // ---------------------------------------------------------------- clipboard

    /** The selected contacts as "Name &lt;address&gt;" lines, or their names, for pasting into a mail. */
    public void copy() {
        List<String> lines = new ArrayList<>();
        for (StoredContact c : selectedContacts()) {
            lines.add(c.contact().firstEmail().map(m -> mailbox(c.contact().label(), m)).orElse(c.contact().label()));
        }
        if (!lines.isEmpty()) view.copyText(String.join("\n", lines));
    }

    public void copyAsVCard() {
        List<StoredContact> list = selectedContacts();
        if (list.isEmpty()) return;
        view.copyText(Exports.write(list, FileFormat.VCARD_3, services.books()::vcard, services.clock().instant()));
    }

    /** Pastes vCards from the clipboard as new contacts. */
    public void paste() {
        Optional<String> text = view.clipboardText();
        if (text.isEmpty() || !text.get().toUpperCase(Locale.ROOT).contains("BEGIN:VCARD")) {
            view.showInfo(msg.get("info.paste-nothing"));
            return;
        }
        importData(ImportFile.of(msg.get("import.clipboard"), text.get().getBytes(StandardCharsets.UTF_8)));
    }

    // ---------------------------------------------------------------- import and export

    public void importFromChooser() {
        view.chooseImportFile().ifPresent(this::importFile);
    }

    public void importFile(Path file) {
        try {
            importData(ImportFile.read(file));
        } catch (IOException | UncheckedIOException e) {
            view.showError(msg.format("error.import", file.getFileName(), e.getMessage()));
        }
    }

    private void importData(ImportFile file) {
        ImportPreview preview = new ImportPreview(services.books().books(),
                msg.format("import.group.name", LocalDate.now(services.clock())),
                list -> ContactImport.duplicates(services.books(), list), msg.locale());
        Optional<ImportChoice> choice = view.chooseImport(file, preview);
        if (choice.isEmpty()) return;
        try {
            List<Imported> imported = choice.get().file().contacts(choice.get().mapping(), msg.locale());
            ContactImport.Result result = ContactImport.apply(services.books(), imported, choice.get().options());
            source = choice.get().options().group().<Source>map(Source.Group::new).orElse(new Source.All());
            query = "";
            view.showQuery("");
            selected = result.uids().isEmpty() ? List.of() : List.of(result.uids().getFirst());
            refresh();
            view.showInfo(msg.format("info.imported", result.added(), result.merged()));
        } catch (RuntimeException e) {
            LOG.warn("Import of {} failed", file.name(), e);
            view.showError(msg.format("error.import", file.name(), e.getMessage()));
        }
    }

    /** The mapping the import dialog starts with: guessed from the column titles for CSV, none otherwise. */
    public static ColumnMapping initialMapping(ImportFile file) {
        return file.isCsv() ? ColumnMapping.guess(file.table().headers()) : new ColumnMapping(List.of());
    }

    public void export() {
        if (shown.isEmpty()) {
            view.showInfo(msg.get("info.export-nothing"));
            return;
        }
        String name = source instanceof Source.Group g ? g.name() : source instanceof Source.Book b ? b.name()
                : msg.get("export.file-name");
        Optional<ExportChoice> choice = view.chooseExport(selected.size(), shown.size(), name);
        if (choice.isEmpty()) return;
        List<StoredContact> list = choice.get().selectedOnly() ? selectedContacts() : shown;
        try {
            String text = Exports.write(list, choice.get().format(), services.books()::vcard, services.clock().instant());
            Path file = choice.get().file();
            if (choice.get().format() == FileFormat.CSV_OUTLOOK) {
                // Outlook reads CSV in the Windows code page unless the file starts with a byte order mark
                Files.writeString(file, "﻿" + text, StandardCharsets.UTF_8);
            } else {
                Files.writeString(file, text, StandardCharsets.UTF_8);
            }
            view.showInfo(msg.format("info.exported", list.size(), file.getFileName()));
        } catch (IOException e) {
            view.showError(msg.format("error.export", choice.get().file(), e.getMessage()));
        }
    }

    // ---------------------------------------------------------------- settings and the rest

    public void setSortByFamilyName(boolean on) {
        settings = settings.withSortByFamilyName(on);
        services.preferences().setSettings(settings);
        view.showSettings(settings);
        refresh();
    }

    public void setLanguage(Optional<Language> language) {
        try {
            locales.setApplicationLanguage(language);
        } catch (IOException e) {
            LOG.warn("Could not save the language", e);
            view.showError(msg.format("error.save-language", e.getMessage()));
            return;
        }
        if (locales.restartRequired()) {
            view.showInfo(Messages.commons(msg.locale()).format("language.restart", "amade-contacts"));
        }
    }

    public void about() {
        view.showAbout();
    }

    public void exit() {
        view.close();
        services.exit().run();
    }

    /** The line under a name in the list: the next birthday, or the first e-mail address, phone or company. */
    public String subtitle(Contact contact, boolean birthdays) {
        if (birthdays && contact.birthday() != null) return birthdayText(contact.birthday());
        if (!contact.organization().isEmpty() && !contact.label().equals(contact.organization())
                && contact.emails().isEmpty()) {
            return contact.organization();
        }
        return contact.firstEmail().orElseGet(() -> contact.phones().stream().map(Entry::value).findFirst()
                .orElse(contact.label().equals(contact.organization()) ? "" : contact.organization()));
    }

    /** "14 November 1986 (turns 40 in 22 days)" and the like. */
    public String birthdayText(Birthday birthday) {
        LocalDate today = LocalDate.now(services.clock());
        long days = birthday.daysUntil(today);
        String when = days == 0 ? msg.get("birthday.today") : days == 1 ? msg.get("birthday.tomorrow")
                : msg.format("birthday.in-days", days);
        String text = birthday.format(msg.locale());
        return birthday.ageAtNext(today).map(age -> msg.format("birthday.turns", text, age, when))
                .orElseGet(() -> msg.format("birthday.next", text, when));
    }

    // ---------------------------------------------------------------- for tests

    List<StoredContact> shown() {
        return shown;
    }

    List<String> selected() {
        return selected;
    }

    Source source() {
        return source;
    }
}
