package eu.amadeos.apps.contacts.ui.transfer;

import eu.amadeos.apps.contacts.io.ColumnMapping;
import eu.amadeos.apps.contacts.io.ImportFile;
import eu.amadeos.apps.contacts.service.ContactImport;

/** What the user chose in the import dialog: the file as read (encoding, format), the columns and where to. */
public record ImportChoice(ImportFile file, ColumnMapping mapping, ContactImport.Options options) {
}
