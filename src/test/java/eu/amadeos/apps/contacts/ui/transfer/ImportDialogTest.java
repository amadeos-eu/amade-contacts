package eu.amadeos.apps.contacts.ui.transfer;

import eu.amadeos.apps.contacts.OnEdt;
import eu.amadeos.apps.contacts.bootstrap.ContactsModule;
import eu.amadeos.apps.contacts.domain.Kind;
import eu.amadeos.apps.contacts.domain.Source;
import eu.amadeos.apps.contacts.io.Field;
import eu.amadeos.apps.contacts.io.FileFormat;
import eu.amadeos.apps.contacts.io.ImportFile;
import eu.amadeos.apps.contacts.io.Samples;
import eu.amadeos.commons.i18n.Messages;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The import dialog without showing it: what it finds and what it hands back. */
class ImportDialogTest {
    private static final Messages MSG = Messages.load(ContactsModule.class, ContactsModule.MESSAGES, Locale.US);
    private static final ImportPreview PREVIEW = new ImportPreview(List.of(new Source.Book("personal", "Personal")),
            "Imported 2026-10-23", list -> (int) list.stream().filter(i -> i.contact().hasEmail("h.kranz@example.de")).count(),
            Locale.US);

    private static ImportDialog dialog(ImportFile file) throws Exception {
        AtomicReference<ImportDialog> dialog = new AtomicReference<>();
        OnEdt.run(() -> dialog.set(new ImportDialog(null, MSG, file, PREVIEW)));
        return dialog.get();
    }

    @Test
    void aGoogleTableWithItsColumns() throws Exception {
        ImportDialog dialog = dialog(Samples.file("google.csv"));
        OnEdt.run(() -> {
            assertEquals(FileFormat.CSV_GOOGLE, dialog.formatBox().getSelectedItem());
            assertTrue(dialog.columnTable().isShowing() || dialog.columnTable().getRowCount() > 10);
            assertTrue(dialog.foundText().endsWith("1 with an e-mail address that is already known"), dialog.foundText());
            dialog.pressImport();
            ImportChoice choice = dialog.result().orElseThrow();
            assertEquals(new eu.amadeos.apps.contacts.service.ContactImport.Options("personal", true,
                    Optional.of("Imported 2026-10-23")), choice.options());
            dialog.dispose();
        });
    }

    @Test
    void changingAColumnChangesWhatIsImported() throws Exception {
        String text = "Name,Info\nAnna Berger,anna@example.org\n";
        ImportDialog dialog = dialog(ImportFile.of("list.csv", text.getBytes(StandardCharsets.UTF_8)));
        OnEdt.run(() -> {
            assertEquals("Info", dialog.columnTable().getValueAt(1, 0));
            assertEquals("anna@example.org", dialog.columnTable().getValueAt(1, 1));
            dialog.columnTable().setValueAt(new ImportDialog.Choice(Field.EMAIL, Kind.WORK), 1, 2);
            dialog.groupBox().setSelected(false);
            dialog.addNewButton().setSelected(true);
            dialog.pressImport();
            ImportChoice choice = dialog.result().orElseThrow();
            assertFalse(choice.options().merge());
            assertEquals(Optional.empty(), choice.options().group());
            var contacts = choice.file().contacts(choice.mapping(), Locale.US);
            assertEquals("anna@example.org", contacts.getFirst().contact().emails().getFirst().value());
            assertEquals(Kind.WORK, contacts.getFirst().contact().emails().getFirst().kind());
            dialog.dispose();
        });
    }

    @Test
    void vCardsNeedNoColumns() throws Exception {
        ImportDialog dialog = dialog(Samples.file("phone-2.1.vcf"));
        OnEdt.run(() -> {
            assertEquals(FileFormat.VCARD_4, dialog.formatBox().getSelectedItem());
            assertTrue(dialog.foundText().startsWith("2 contacts found"), dialog.foundText());
            dialog.dispose();
        });
    }
}
