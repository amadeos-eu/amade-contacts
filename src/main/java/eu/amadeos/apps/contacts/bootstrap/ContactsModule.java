package eu.amadeos.apps.contacts.bootstrap;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import eu.amadeos.apps.contacts.repository.AddressBooks;
import eu.amadeos.apps.contacts.repository.ContactsPreferences;
import eu.amadeos.apps.contacts.ui.main.MainPresenter;
import eu.amadeos.apps.contacts.ui.main.MainWindow;
import eu.amadeos.commons.config.XdgDirectories;
import eu.amadeos.commons.desktop.DesktopLauncher;
import eu.amadeos.commons.i18n.LocaleSettings;
import eu.amadeos.commons.i18n.Messages;

import java.nio.file.Path;
import java.time.Clock;
import java.util.Locale;

/**
 * amade-contacts bindings. Theme, language, directories and the other shared services come from
 * {@link eu.amadeos.commons.app.CommonsModule}.
 */
public final class ContactsModule extends AbstractModule {
    /** The texts, {@code messages.properties} and its translations. */
    public static final String MESSAGES = "eu.amadeos.apps.contacts.messages";

    @Override
    protected void configure() {
        bind(ContactsPreferences.class).in(Scopes.SINGLETON);
    }

    /** The texts in the language {@code AmadeApplication} chose. */
    @Provides
    @Singleton
    Messages messages(Locale locale) {
        return Messages.load(ContactsModule.class, MESSAGES, locale);
    }

    // --8<-- [start:books]
    /** The address books in {@code ~/.local/share/amadeos/contacts}, one folder each, one vCard file per contact. */
    @Provides
    @Singleton
    AddressBooks addressBooks(XdgDirectories xdg, Messages msg) {
        return new AddressBooks(contactsFolder(xdg), Clock.systemDefaultZone(), msg.get("book.default"));
    }

    public static Path contactsFolder(XdgDirectories xdg) {
        return xdg.dataHome().resolve("amadeos").resolve("contacts");
    }
    // --8<-- [end:books]

    @Provides
    @Singleton
    MainWindow window(Messages msg, LocaleSettings locales) {
        return new MainWindow(msg, locales);
    }

    @Provides
    @Singleton
    MainPresenter presenter(MainWindow window, Messages msg, LocaleSettings locales, AddressBooks books,
                            ContactsPreferences preferences, DesktopLauncher launcher) {
        return new MainPresenter(window, msg, locales, new MainPresenter.Services(books, preferences, launcher,
                Clock.systemDefaultZone(), () -> System.exit(0)));
    }
}
