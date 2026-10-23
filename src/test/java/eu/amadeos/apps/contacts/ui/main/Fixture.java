package eu.amadeos.apps.contacts.ui.main;

import eu.amadeos.apps.contacts.OnEdt;
import eu.amadeos.apps.contacts.bootstrap.ContactsModule;
import eu.amadeos.apps.contacts.domain.Birthday;
import eu.amadeos.apps.contacts.domain.Contact;
import eu.amadeos.apps.contacts.domain.Entry;
import eu.amadeos.apps.contacts.domain.Kind;
import eu.amadeos.apps.contacts.domain.Name;
import eu.amadeos.apps.contacts.domain.PostalAddress;
import eu.amadeos.apps.contacts.repository.AddressBooks;
import eu.amadeos.apps.contacts.repository.ContactsPreferences;
import eu.amadeos.commons.desktop.DesktopLauncher;
import eu.amadeos.commons.i18n.Messages;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/** An address book with three made-up people in a temporary folder, and the services of a presenter. */
final class Fixture {
    static final Messages MSG = Messages.load(ContactsModule.class, ContactsModule.MESSAGES, Locale.US);
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-23T12:00:00Z"), ZoneOffset.UTC);

    final Path dir;
    final AddressBooks books;
    final ContactsPreferences preferences;
    final List<String> browsed = new ArrayList<>();
    /** True to act as if no program opens {@code tel:} links. */
    boolean noPhoneProgram;
    int exits;

    Fixture(Path dir) {
        this.dir = dir;
        this.books = new AddressBooks(dir.resolve("contacts"), CLOCK, "Personal");
        this.preferences = new ContactsPreferences(dir.resolve("contacts.properties"));
    }

    /** Anna Berger (choir, birthday in 22 days), Bernd Albers (phone only), Carla Weiß (work, no birthday). */
    Fixture withPeople() {
        books.reload();
        books.save("personal", new Contact("anna", Name.of("Anna", "Berger"), "", "", "", "",
                List.of(new Entry(Kind.MOBILE, "+49 170 5550101")), List.of(new Entry(Kind.HOME, "anna@example.org")),
                List.of(new PostalAddress(Kind.HOME, "Lindenweg 4", "80331", "München", "", "Deutschland")),
                List.of("anna.example.org"), new Birthday(1986, 11, 14), new TreeSet<>(Set.of("Choir")), "", null));
        books.save("personal", new Contact("bernd", Name.of("Bernd", "Albers"), "", "", "", "",
                List.of(new Entry(Kind.HOME, "089 555 0123")), List.of(), List.of(), List.of(), null, new TreeSet<>(Set.of()), "", null));
        books.save("personal", new Contact("carla", Name.of("Carla", "Weiß"), "", "", "Grünbau AG", "Architect",
                List.of(), List.of(new Entry(Kind.WORK, "c.weiss@gruenbau.example")), List.of(), List.of(), null,
                new TreeSet<>(Set.of("Choir", "Work")), "", null));
        return this;
    }

    MainPresenter.Services services() {
        DesktopLauncher launcher = new DesktopLauncher() {
            @Override
            public void browse(String uri) throws IOException {
                if (noPhoneProgram && uri.startsWith("tel:")) throw new IOException("no program for tel:");
                browsed.add(uri);
            }
        };
        return new MainPresenter.Services(books, preferences, launcher, CLOCK, () -> exits++);
    }

    MainPresenter presenter(MainView view) {
        return new MainPresenter(view, MSG, null, services());
    }

    static void edt(OnEdtTest test) throws Exception {
        OnEdt.run(() -> {
            try {
                test.run();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    interface OnEdtTest {
        void run() throws Exception;
    }
}
