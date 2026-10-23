package eu.amadeos.apps.contacts.ui.main;

import eu.amadeos.apps.contacts.domain.Settings;
import eu.amadeos.commons.i18n.Language;
import eu.amadeos.commons.i18n.LocaleSettings;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.i18n.Mnemonics;
import eu.amadeos.commons.swing.EdtExecutor;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.KeyStroke;
import javax.swing.border.BevelBorder;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Contacts, Edit, View, Options and Help. */
public final class MainMenuBar extends JMenuBar {
    private static final int CTRL = InputEvent.CTRL_DOWN_MASK;
    private static final int SHIFT = InputEvent.SHIFT_DOWN_MASK;

    private final Messages msg;
    private final Map<String, JMenuItem> items = new HashMap<>();

    public MainMenuBar(MainPresenter presenter, Messages msg, LocaleSettings locales) {
        EdtExecutor.requireEdt();
        this.msg = msg;
        setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));

        JMenu contacts = menu("menu.contacts");
        contacts.add(item("menu.contacts.new", key(KeyEvent.VK_N, CTRL), presenter::newContact));
        contacts.add(item("menu.contacts.edit", key(KeyEvent.VK_E, CTRL), presenter::edit));
        contacts.add(item("menu.contacts.delete", key(KeyEvent.VK_DELETE, 0), presenter::delete));
        contacts.addSeparator();
        contacts.add(item("menu.contacts.mail", key(KeyEvent.VK_M, CTRL), presenter::writeMail));
        contacts.add(item("menu.contacts.call", null, presenter::call));
        contacts.addSeparator();
        contacts.add(item("menu.contacts.import", key(KeyEvent.VK_I, CTRL), presenter::importFromChooser));
        contacts.add(item("menu.contacts.export", key(KeyEvent.VK_E, CTRL | SHIFT), presenter::export));
        contacts.addSeparator();
        contacts.add(item("menu.contacts.exit", key(KeyEvent.VK_Q, CTRL), presenter::exit));

        JMenu edit = menu("menu.edit");
        edit.add(item("menu.edit.copy", key(KeyEvent.VK_C, CTRL), presenter::copy));
        edit.add(item("menu.edit.copy-vcard", key(KeyEvent.VK_C, CTRL | SHIFT), presenter::copyAsVCard));
        edit.add(item("menu.edit.paste", key(KeyEvent.VK_V, CTRL), presenter::paste));
        edit.addSeparator();
        edit.add(item("menu.edit.find", key(KeyEvent.VK_F, CTRL), presenter::focusSearch));

        JMenu view = menu("menu.view");
        ButtonGroup sort = new ButtonGroup();
        view.add(radio("menu.view.sort-first", sort, () -> presenter.setSortByFamilyName(false)));
        view.add(radio("menu.view.sort-last", sort, () -> presenter.setSortByFamilyName(true)));
        view.addSeparator();
        view.add(item("menu.view.reload", key(KeyEvent.VK_F5, 0), presenter::reload));

        JMenu options = menu("menu.options");
        if (locales != null) options.add(languageMenu(presenter, locales));

        JMenu help = menu("menu.help");
        help.add(item("menu.help.about", null, presenter::about));
    }

    void update(Settings settings) {
        items.get(settings.sortByFamilyName() ? "menu.view.sort-last" : "menu.view.sort-first").setSelected(true);
    }

    /** Greys out what needs one or more selected contacts. */
    void enable(int selected) {
        items.get("menu.contacts.edit").setEnabled(selected == 1);
        items.get("menu.contacts.call").setEnabled(selected == 1);
        for (String key : List.of("menu.contacts.delete", "menu.contacts.mail", "menu.edit.copy",
                "menu.edit.copy-vcard")) {
            items.get(key).setEnabled(selected > 0);
        }
    }

    JMenuItem item(String name) {
        return items.get(name);
    }

    /** "Desktop setting (Deutsch)" and the five languages; the choice applies at the next start. */
    private JMenu languageMenu(MainPresenter presenter, LocaleSettings locales) {
        JMenu menu = Mnemonics.apply(new JMenu(), msg.get("menu.options.language"));
        menu.setName("menu.options.language");
        Messages commons = Messages.commons(msg.locale());
        List<Optional<Language>> choices = new ArrayList<>();
        choices.add(Optional.empty());
        for (Language language : Language.values()) choices.add(Optional.of(language));
        ButtonGroup group = new ButtonGroup();
        Optional<Language> current = locales.applicationLanguage();
        for (Optional<Language> choice : choices) {
            String label = choice.map(Language::nativeName).orElseGet(() -> commons.format("language.follow-desktop",
                    locales.desktopResolution().language().nativeName()));
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(label, choice.equals(current));
            item.setName(choice.map(Language::code).orElse(LocaleSettings.FOLLOW_DESKTOP));
            item.addActionListener(e -> presenter.setLanguage(choice));
            group.add(item);
            menu.add(item);
            if (choice.isEmpty()) menu.addSeparator();
        }
        return menu;
    }

    private static KeyStroke key(int code, int modifiers) {
        return KeyStroke.getKeyStroke(code, modifiers);
    }

    private JMenu menu(String key) {
        JMenu menu = Mnemonics.apply(new JMenu(), msg.get(key));
        menu.setName(key);
        add(menu);
        return menu;
    }

    private JMenuItem item(String key, KeyStroke accelerator, Runnable action) {
        JMenuItem item = Mnemonics.apply(new JMenuItem(), msg.get(key));
        item.setName(key);
        if (accelerator != null) item.setAccelerator(accelerator);
        item.addActionListener(e -> action.run());
        items.put(key, item);
        return item;
    }

    private JRadioButtonMenuItem radio(String key, ButtonGroup group, Runnable action) {
        JRadioButtonMenuItem item = Mnemonics.apply(new JRadioButtonMenuItem(), msg.get(key));
        item.setName(key);
        item.addActionListener(e -> action.run());
        group.add(item);
        items.put(key, item);
        return item;
    }
}
