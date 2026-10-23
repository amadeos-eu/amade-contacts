package eu.amadeos.apps.contacts.ui.main;

import eu.amadeos.apps.contacts.domain.Birthday;
import eu.amadeos.apps.contacts.domain.Contact;
import eu.amadeos.apps.contacts.domain.Entry;
import eu.amadeos.apps.contacts.domain.Kind;
import eu.amadeos.apps.contacts.domain.Name;
import eu.amadeos.apps.contacts.domain.Source;
import eu.amadeos.apps.contacts.io.FileFormat;
import eu.amadeos.apps.contacts.service.ContactImport;
import eu.amadeos.apps.contacts.ui.transfer.ExportChoice;
import eu.amadeos.apps.contacts.ui.transfer.ImportChoice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static eu.amadeos.apps.contacts.ui.main.Fixture.edt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MainPresenterTest {
    @TempDir
    Path dir;

    private Fixture fixture;
    private FakeView view;
    private MainPresenter presenter;

    @BeforeEach
    void setUp() throws Exception {
        fixture = new Fixture(dir).withPeople();
        view = new FakeView();
        presenter = fixture.presenter(view);
        edt(() -> presenter.start(List.of()));
    }

    @Test
    void startShowsEveryoneSortedByFirstNameAndTheFirstCard() {
        assertEquals(List.of("Anna Berger", "Bernd Albers", "Carla Weiß"), view.names());
        assertEquals(List.of("All Contacts 3", "Choir 2", "Work 1", "Birthdays (30 days) 1"), view.sourceLabels());
        assertEquals(0, view.sourceIndex);
        assertEquals(List.of("anna"), view.selected);
        assertEquals("anna", view.card.orElseThrow().uid());
        assertEquals("3 contacts", view.status);
        assertFalse(view.settings.sortByFamilyName());
    }

    @Test
    void sortingByLastNameIsRemembered() throws Exception {
        edt(() -> presenter.setSortByFamilyName(true));
        assertEquals(List.of("Bernd Albers", "Anna Berger", "Carla Weiß"), view.names());
        assertTrue(fixture.preferences.settings().sortByFamilyName());
    }

    @Test
    void groupsAndBirthdays() throws Exception {
        edt(() -> presenter.selectSource(new Source.Group("Choir")));
        assertEquals(List.of("Anna Berger", "Carla Weiß"), view.names());
        assertEquals("2 of 3 contacts", view.status);
        edt(() -> presenter.selectSource(new Source.Birthdays()));
        assertTrue(view.birthdays);
        assertEquals(List.of("Anna Berger"), view.names());
        assertEquals("November 14, 1986 (turns 40 in 22 days)",
                presenter.subtitle(view.contacts.getFirst().contact(), true));
    }

    @Test
    void searchFindsNamesMailsAndDigitsOfNumbers() throws Exception {
        edt(() -> presenter.search("weiss"));
        assertEquals(List.of("Carla Weiß"), view.names());
        edt(() -> presenter.search("5550123"));
        assertEquals(List.of("Bernd Albers"), view.names());
        edt(() -> presenter.search("nobody"));
        assertEquals(List.of(), view.names());
        assertEquals("No contact matches \"nobody\"", view.placeholder);
    }

    @Test
    void severalSelectedShowNoCard() throws Exception {
        edt(() -> presenter.selectContacts(List.of("anna", "carla")));
        assertTrue(view.card.isEmpty());
        assertEquals("2 contacts selected", view.placeholder);
    }

    @Test
    void aNewContactInAGroupStartsInThatGroupAndIsSaved() throws Exception {
        edt(() -> presenter.selectSource(new Source.Group("Choir")));
        view.editor = c -> new Contact(c.uid(), Name.of("Dora", "Ziegler"), "", "", "", "", List.of(),
                List.of(new Entry(Kind.HOME, "dora@example.org")), List.of(), List.of(), null, c.groups(), "", null);
        edt(presenter::newContact);
        assertTrue(view.editedIsNew);
        assertEquals(List.of("Choir"), List.copyOf(view.edited.groups()));
        assertEquals(List.of("Choir", "Work"), view.editorGroups);
        assertEquals(List.of("Anna Berger", "Carla Weiß", "Dora Ziegler"), view.names());
        String uid = view.selected.getFirst();
        assertEquals("Dora Ziegler", view.card.orElseThrow().contact().label());
        assertTrue(Files.exists(fixture.books.file(uid).orElseThrow()));
    }

    @Test
    void cancellingTheEditorChangesNothing() throws Exception {
        edt(presenter::newContact);
        edt(presenter::edit);
        assertEquals(3, view.contacts.size());
        assertEquals("Anna Berger", view.edited.label());
    }

    @Test
    void editingAContactOutOfItsGroupShowsAllContacts() throws Exception {
        edt(() -> presenter.selectSource(new Source.Group("Work")));
        view.editor = c -> c.withGroups(List.of());
        edt(presenter::edit);
        assertEquals(0, view.sourceIndex, "back to All Contacts, so the contact stays in sight");
        assertEquals(List.of("carla"), view.selected);
        assertEquals(List.of("All Contacts 3", "Choir 1", "Birthdays (30 days) 1"), view.sourceLabels());
    }

    @Test
    void deleteAsksAndSelectsTheNextContact() throws Exception {
        view.deleteAnswer = false;
        edt(presenter::delete);
        assertEquals(List.of("Anna Berger"), view.deleteAsked);
        assertEquals(3, view.contacts.size());
        view.deleteAnswer = true;
        edt(presenter::delete);
        assertEquals(List.of("Bernd Albers", "Carla Weiß"), view.names());
        assertEquals(List.of("bernd"), view.selected);
        assertTrue(fixture.books.find("anna").isEmpty());
    }

    @Test
    void mailToTheSelectedPeople() throws Exception {
        edt(() -> presenter.selectContacts(List.of("anna", "bernd", "carla")));
        edt(presenter::writeMail);
        assertEquals(List.of("mailto:Anna%20Berger%20%3Canna@example.org%3E,Carla%20Wei%C3%9F%20%3Cc.weiss@gruenbau.example%3E"),
                fixture.browsed);
        edt(() -> presenter.selectContacts(List.of("bernd")));
        edt(presenter::writeMail);
        assertEquals(List.of("The selected contacts have no e-mail address."), view.infos);
        assertEquals("\"Weiß, Carla\" <c@x.org>", MainPresenter.mailbox("Weiß, Carla", "c@x.org"));
        assertEquals("c@x.org", MainPresenter.mailbox("", "c@x.org"));
    }

    @Test
    void callsWithATelLinkOrCopiesTheNumber() throws Exception {
        edt(presenter::call);
        assertEquals(List.of("tel:+491705550101"), fixture.browsed);
        fixture.noPhoneProgram = true;
        edt(() -> presenter.call("089 555 0123"));
        assertEquals("089 555 0123", view.copied);
        assertEquals("There is no program for calls. The number 089 555 0123 has been copied.", view.infos.getLast());
    }

    @Test
    void mapAndWebsite() throws Exception {
        edt(() -> presenter.showOnMap(view.card.orElseThrow().contact().addresses().getFirst()));
        edt(() -> presenter.openWebsite("anna.example.org"));
        edt(() -> presenter.openWebsite("http://old.example.org"));
        assertEquals(List.of("https://www.openstreetmap.org/search?query=Lindenweg+4%2C+80331+M%C3%BCnchen%2C+Deutschland",
                "https://anna.example.org", "http://old.example.org"), fixture.browsed);
    }

    @Test
    void copyAndPasteVCards() throws Exception {
        edt(() -> presenter.selectContacts(List.of("anna", "bernd")));
        edt(presenter::copy);
        assertEquals("Anna Berger <anna@example.org>\nBernd Albers", view.copied);
        edt(presenter::copyAsVCard);
        assertTrue(view.copied.startsWith("BEGIN:VCARD\r\nVERSION:3.0"), view.copied);

        view.clipboard = "just text";
        edt(presenter::paste);
        assertEquals("The clipboard holds no vCard.", view.infos.getLast());

        view.clipboard = "BEGIN:VCARD\r\nVERSION:3.0\r\nFN:Emil Fuchs\r\nN:Fuchs;Emil;;;\r\nEMAIL:emil@example.org\r\nEND:VCARD\r\n";
        view.importer = (file, preview) -> new ImportChoice(file, MainPresenter.initialMapping(file),
                new ContactImport.Options("personal", true, Optional.empty()));
        edt(presenter::paste);
        assertEquals("Clipboard", view.preview == null ? "" : "Clipboard");
        assertEquals(List.of("Anna Berger", "Bernd Albers", "Carla Weiß", "Emil Fuchs"), view.names());
        assertEquals("1 contacts added, 0 merged with known contacts.", view.infos.getLast());
    }

    @Test
    void importIntoANewGroupShowsThatGroup() throws Exception {
        Path csv = dir.resolve("list.csv");
        Files.writeString(csv, "Name,E-mail,Birthday\nFranz Huber,franz@example.org,1970-01-05\n"
                + "Anna B.,ANNA@example.org,\n", StandardCharsets.UTF_8);
        view.importer = (file, preview) -> {
            assertEquals("Imported 2026-10-23", preview.groupName());
            assertEquals(1, preview.duplicates().applyAsInt(file.contacts(MainPresenter.initialMapping(file),
                    preview.locale())));
            return new ImportChoice(file, MainPresenter.initialMapping(file),
                    new ContactImport.Options("personal", true, Optional.of(preview.groupName())));
        };
        edt(() -> presenter.importFile(csv));
        assertEquals(List.of("Anna Berger", "Franz Huber"), view.names());
        assertEquals("Imported 2026-10-23 2", view.sourceLabels().get(view.sourceIndex));
        assertEquals("1 contacts added, 1 merged with known contacts.", view.infos.getLast());
        assertEquals(new Birthday(1970, 1, 5), fixture.books.contacts().stream()
                .filter(c -> c.contact().label().equals("Franz Huber")).findFirst().orElseThrow().contact().birthday());
    }

    @Test
    void anUnreadableFileIsAnError() throws Exception {
        edt(() -> presenter.importFile(dir.resolve("missing.vcf")));
        assertEquals(1, view.errors.size());
        assertTrue(view.errors.getFirst().startsWith("missing.vcf cannot be imported"), view.errors.getFirst());
    }

    @Test
    void exportWritesTheChosenFormat() throws Exception {
        Path out = dir.resolve("choir.csv");
        edt(() -> presenter.selectSource(new Source.Group("Choir")));
        view.exportChoice = new ExportChoice(out, FileFormat.CSV_OUTLOOK, false);
        edt(presenter::export);
        byte[] bytes = Files.readAllBytes(out);
        assertEquals((byte) 0xEF, bytes[0], "Outlook CSV starts with a byte order mark");
        String text = new String(bytes, StandardCharsets.UTF_8);
        assertTrue(text.contains("Anna") && text.contains("Weiß"), text);
        assertEquals("2 contacts saved in choir.csv.", view.infos.getLast());

        Path vcf = dir.resolve("anna.vcf");
        view.exportChoice = new ExportChoice(vcf, FileFormat.VCARD_4, true);
        edt(presenter::export);
        String card = Files.readString(vcf);
        assertTrue(card.startsWith("BEGIN:VCARD\r\nVERSION:4.0") && card.contains("Berger") && !card.contains("Weiß"), card);
    }

    @Test
    void filesChangedByOtherProgramsAreReadWhenTheWindowIsActivated() throws Exception {
        Path file = dir.resolve("contacts/personal/from-phone.vcf");
        Files.writeString(file, "BEGIN:VCARD\r\nVERSION:3.0\r\nUID:phone-1\r\nFN:Gerda Lang\r\nN:Lang;Gerda;;;\r\nEND:VCARD\r\n");
        edt(presenter::reloadIfChanged);
        assertEquals(List.of("Anna Berger", "Bernd Albers", "Carla Weiß", "Gerda Lang"), view.names());
    }

    @Test
    void subtitles() {
        Contact carla = fixture.books.find("carla").orElseThrow().contact();
        assertEquals("c.weiss@gruenbau.example", presenter.subtitle(carla, false));
        Contact bernd = fixture.books.find("bernd").orElseThrow().contact();
        assertEquals("089 555 0123", presenter.subtitle(bernd, false));
    }

    @Test
    void exitClosesTheWindow() throws Exception {
        edt(presenter::exit);
        assertTrue(view.closed);
        assertEquals(1, fixture.exits);
    }
}
