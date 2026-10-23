package eu.amadeos.apps.contacts.repository;

import com.google.inject.Inject;
import eu.amadeos.apps.contacts.domain.Settings;
import eu.amadeos.commons.config.AppDirectories;
import eu.amadeos.commons.config.PropertiesFile;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Properties;

/**
 * Settings of amade-contacts in {@code ~/.config/amadeos/contacts/contacts.properties}. Each change is written at
 * once; unknown or broken values fall back to the defaults. The contacts themselves are in {@link AddressBooks}.
 */
public class ContactsPreferences {
    public static final String FILE = "contacts.properties";

    private final Path file;
    private final Properties properties;

    @Inject
    public ContactsPreferences(AppDirectories directories) {
        this(directories.configFile(FILE));
    }

    public ContactsPreferences(Path file) {
        this.file = Objects.requireNonNull(file, "file");
        this.properties = PropertiesFile.load(file);
    }

    public Settings settings() {
        String sort = properties.getProperty("sort", "");
        return new Settings(sort.strip().equals("family-name"));
    }

    public void setSettings(Settings settings) {
        properties.setProperty("sort", settings.sortByFamilyName() ? "family-name" : "display-name");
        PropertiesFile.store(file, properties, "amade-contacts");
    }
}
