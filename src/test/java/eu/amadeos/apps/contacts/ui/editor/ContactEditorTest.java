package eu.amadeos.apps.contacts.ui.editor;

import eu.amadeos.apps.contacts.OnEdt;
import eu.amadeos.apps.contacts.bootstrap.ContactsModule;
import eu.amadeos.apps.contacts.domain.Birthday;
import eu.amadeos.apps.contacts.domain.Contact;
import eu.amadeos.apps.contacts.domain.Entry;
import eu.amadeos.apps.contacts.domain.Kind;
import eu.amadeos.apps.contacts.domain.Name;
import eu.amadeos.apps.contacts.domain.PostalAddress;
import eu.amadeos.commons.i18n.Messages;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The editor without showing it: fields filled from a contact and a contact made from the fields. */
class ContactEditorTest {
    private static final Messages MSG = Messages.load(ContactsModule.class, ContactsModule.MESSAGES, Locale.US);

    private static final Contact ANNA = new Contact("anna", Name.of("Anna", "Berger"), "", "Anni", "", "",
            List.of(new Entry(Kind.MOBILE, "+49 170 5550101")), List.of(new Entry(Kind.HOME, "anna@example.org")),
            List.of(new PostalAddress(Kind.HOME, "Lindenweg 4\nHinterhaus", "80331", "München", "", "Deutschland")),
            List.of(), new Birthday(1986, 11, 14), new TreeSet<>(Set.of("Choir")), "Sopran", null);

    private static ContactEditor editor(Contact contact, boolean isNew) throws Exception {
        AtomicReference<ContactEditor> editor = new AtomicReference<>();
        OnEdt.run(() -> editor.set(new ContactEditor(null, MSG, contact, isNew, List.of("Choir", "Work"))));
        return editor.get();
    }

    @Test
    void unchangedFieldsGiveTheSameContact() throws Exception {
        ContactEditor editor = editor(ANNA, false);
        OnEdt.run(() -> {
            assertEquals("Anna", editor.givenField().getText());
            assertEquals("1986-11-14", editor.birthdayField().getText());
            assertEquals(1, editor.addressRows().rows().size());
            editor.pressSave();
            assertEquals(ANNA, editor.result().orElseThrow());
            editor.dispose();
        });
    }

    @Test
    void theDisplayNameFollowsTheNameUntilTyped() throws Exception {
        ContactEditor editor = editor(Contact.create(), true);
        OnEdt.run(() -> {
            editor.givenField().setText("Dora");
            editor.familyField().setText("Ziegler");
        });
        OnEdt.run(() -> {
            assertEquals("Dora Ziegler", editor.displayField().getText());
            editor.displayField().setText("Dr. Dora");
            editor.givenField().setText("Doris");
        });
        OnEdt.run(() -> {
            assertEquals("Dr. Dora", editor.displayField().getText());
            editor.dispose();
        });
    }

    @Test
    void newRowsGroupsAndABirthdayWithoutYear() throws Exception {
        ContactEditor editor = editor(ANNA, false);
        OnEdt.run(() -> {
            EntryRow row = editor.phoneRows().addRow();
            row.valueField().setText("089 555 0123");
            row.kindBox().setSelectedItem(Kind.WORK);
            editor.emailRows().addRow();
            editor.birthdayField().setText("--11-14");
            editor.newGroupsField().setText("Neighbours, Work");
            editor.pressSave();
            Contact c = editor.result().orElseThrow();
            assertEquals(List.of(new Entry(Kind.MOBILE, "+49 170 5550101"), new Entry(Kind.WORK, "089 555 0123")),
                    c.phones());
            assertEquals(1, c.emails().size(), "empty rows are left out");
            assertEquals(new Birthday(0, 11, 14), c.birthday());
            assertEquals(List.of("Choir", "Neighbours", "Work"), List.copyOf(c.groups()));
            editor.dispose();
        });
    }

    @Test
    void aBadBirthdayAndAnEmptyContactAreRefused() throws Exception {
        ContactEditor editor = editor(ANNA, false);
        OnEdt.run(() -> {
            editor.birthdayField().setText("someday");
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class, editor::contact);
            assertTrue(e.getMessage().contains("\"someday\" is not a date"), e.getMessage());
            editor.dispose();
        });
        ContactEditor empty = editor(Contact.create(), true);
        OnEdt.run(() -> {
            assertThrows(IllegalArgumentException.class, empty::contact);
            empty.dispose();
        });
    }
}
