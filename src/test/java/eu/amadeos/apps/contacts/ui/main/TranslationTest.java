package eu.amadeos.apps.contacts.ui.main;

import eu.amadeos.apps.contacts.bootstrap.ContactsModule;
import eu.amadeos.commons.i18n.Language;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.i18n.Mnemonics;
import eu.amadeos.commons.i18n.TranslationCheck;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TranslationTest {
    private static Messages texts(Language language) {
        return Messages.load(ContactsModule.class, ContactsModule.MESSAGES, Locale.forLanguageTag(language.code()));
    }

    @Test
    void everyLanguageHasEveryText() {
        assertEquals(Map.of(), TranslationCheck.problems(ContactsModule.class, ContactsModule.MESSAGES));
    }

    /** No two entries share a mnemonic letter in the menu bar, in a menu or in a dialog. */
    @Test
    void mnemonicsAreUnique() {
        Map<String, List<String>> groups = new HashMap<>();
        groups.put("bar", List.of("menu.contacts", "menu.edit", "menu.view", "menu.options", "menu.help"));
        groups.put("contacts", List.of("menu.contacts.new", "menu.contacts.edit", "menu.contacts.delete",
                "menu.contacts.mail", "menu.contacts.call", "menu.contacts.import", "menu.contacts.export",
                "menu.contacts.exit"));
        groups.put("edit", List.of("menu.edit.copy", "menu.edit.copy-vcard", "menu.edit.paste", "menu.edit.find"));
        groups.put("view", List.of("menu.view.sort-first", "menu.view.sort-last", "menu.view.reload"));
        groups.put("editor", List.of("editor.photo.choose", "editor.photo.remove", "editor.save", "editor.cancel"));
        groups.put("import", List.of("import.format", "import.charset", "import.book", "import.merge",
                "import.add-new", "import.group", "import.ok", "import.cancel"));
        groups.put("export", List.of("export.format", "export.selected", "export.shown"));
        for (Language language : Language.values()) {
            Messages texts = texts(language);
            for (Map.Entry<String, List<String>> group : groups.entrySet()) {
                Map<Integer, String> used = new HashMap<>();
                for (String key : group.getValue()) {
                    Mnemonics.Text text = Mnemonics.parse(texts.get(key));
                    assertTrue(text.index() >= 0, language + " " + key + " has no mnemonic");
                    String before = used.put(text.keyCode(), key);
                    assertEquals(null, before, language + " " + group.getKey() + ": " + key + " and " + before
                            + " share a letter");
                }
            }
        }
    }

    @Test
    void textsWithApostrophesAndNumbers() {
        assertEquals("Kontakte", texts(Language.GERMAN).get("window.title"));
        assertEquals("Anna Berger - Contacts", Fixture.MSG.format("window.title.contact", "Anna Berger"));
        assertEquals("2 of 3 contacts", Fixture.MSG.format("status.shown", 2, 3));
        for (Language language : Language.values()) {
            String text = texts(language).format("import.found", 12, 3);
            assertTrue(text.contains("12") && text.contains("3") && !text.contains("{"), language + ": " + text);
        }
    }
}
