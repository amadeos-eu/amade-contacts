package eu.amadeos.apps.contacts.bootstrap;

import eu.amadeos.apps.contacts.ui.main.MainPresenter;
import eu.amadeos.apps.contacts.ui.main.MainWindow;
import eu.amadeos.commons.app.AmadeApplication;
import eu.amadeos.commons.app.AppInfo;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class AmadeContactsApplication {
    public static final AppInfo APP = new AppInfo("amade-contacts", "Contacts", "contacts");

    private AmadeContactsApplication() {
    }

    /** The version from the jar's manifest; "dev" when run from the IDE. */
    public static String version() {
        String version = AmadeContactsApplication.class.getPackage().getImplementationVersion();
        return version == null ? "dev" : version;
    }

    // --8<-- [start:main]
    public static void main(String[] args) {
        if (args.length > 0 && (args[0].equals("--version") || args[0].equals("-v"))) {
            System.out.println("amade-contacts " + version());
            return;
        }
        List<Path> files = files(args);
        AmadeApplication.launch(APP, injector -> {
            MainWindow window = injector.getInstance(MainWindow.class);
            MainPresenter presenter = injector.getInstance(MainPresenter.class);
            window.bind(presenter);
            window.setVisible(true);
            presenter.start(files);
        }, new ContactsModule());
    }
    // --8<-- [end:main]

    /** Files to import, from the command line; the desktop entry passes opened .vcf files with %F. */
    static List<Path> files(String[] args) {
        List<Path> paths = new ArrayList<>();
        for (String arg : args) {
            if (arg.isBlank() || arg.startsWith("-")) continue;
            paths.add(Path.of(arg.startsWith("file://") ? java.net.URI.create(arg).getPath() : arg));
        }
        return paths;
    }
}
