package eu.amadeos.apps.contacts.bootstrap;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApplicationTest {
    @Test
    void commandLineFilesAndUris() {
        assertEquals(List.of(Path.of("/home/anna/a b.vcf"), Path.of("list.csv")),
                AmadeContactsApplication.files(new String[]{"file:///home/anna/a%20b.vcf", "--foo", "list.csv", " "}));
        assertEquals("dev", AmadeContactsApplication.version());
        assertEquals(Path.of("/home/anna/.local/share/amadeos/contacts"),
                ContactsModule.contactsFolder(new eu.amadeos.commons.config.XdgDirectories(java.util.Map.of(), "/home/anna")));
    }
}
