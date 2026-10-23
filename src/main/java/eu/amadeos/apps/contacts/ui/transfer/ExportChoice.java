package eu.amadeos.apps.contacts.ui.transfer;

import eu.amadeos.apps.contacts.io.FileFormat;

import java.nio.file.Path;

/**
 * What the user chose in the export dialog.
 *
 * @param selectedOnly the selected contacts only, else all that the list shows
 */
public record ExportChoice(Path file, FileFormat format, boolean selectedOnly) {
}
