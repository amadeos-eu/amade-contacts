package eu.amadeos.apps.contacts.ui.main;

import eu.amadeos.apps.contacts.OnEdt;
import eu.amadeos.apps.contacts.domain.Source;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/** The real window with the made-up people: lists, card, tool bar and menu states. Needs a display. */
class MainWindowTest {
    @TempDir
    Path dir;

    private Fixture fixture;
    private MainWindow window;
    private MainPresenter presenter;

    private void open() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display");
        fixture = new Fixture(dir).withPeople();
        OnEdt.run(() -> {
            window = new MainWindow(Fixture.MSG, null);
            presenter = fixture.presenter(window);
            window.bind(presenter);
            window.setVisible(true);
            presenter.start(List.of());
        });
    }

    @AfterEach
    void close() throws Exception {
        if (window != null) OnEdt.run(() -> window.close());
    }

    @Test
    void aContactWithACallKeyHasCallButtons() throws Exception {
        open();
        String key = "0f6e3ea2f8d4c8c6".repeat(4);
        Path file = fixture.books.file("anna").orElseThrow();
        java.nio.file.Files.writeString(file, java.nio.file.Files.readString(file)
                        .replace("END:VCARD", "IMPP:amadeos-call:" + key + "\r\nEND:VCARD"));
        OnEdt.run(() -> {
            fixture.books.reload();
            presenter.selectContacts(List.of("anna"));
            List<String> values = window.card().values();
            assertTrue(values.contains("0f6e 3ea2 f8d4 c8c6"), values.toString());
            javax.swing.JButton video = find(window.card(), "card.video-call");
            assertEquals("Video call", video.getToolTipText());
            video.doClick();
            assertEquals(List.of("amadeos-call:" + key + "?kind=video"), fixture.browsed);
        });
    }

    @SuppressWarnings("unchecked")
    private static <T extends java.awt.Component> T find(java.awt.Container in, String name) {
        for (java.awt.Component c : in.getComponents()) {
            if (name.equals(c.getName())) return (T) c;
            if (c instanceof java.awt.Container k) {
                T found = find(k, name);
                if (found != null) return found;
            }
        }
        return null;
    }

    @Test
    void showsTheListsAndTheCard() throws Exception {
        open();
        OnEdt.run(() -> {
            assertEquals(3, window.contactList().getModel().getSize());
            assertEquals(4, window.sourceList().getModel().getSize());
            assertEquals("3 contacts", window.statusText());
            List<String> values = window.card().values();
            assertTrue(values.contains("Anna Berger"), values.toString());
            assertTrue(values.contains("anna@example.org"), values.toString());
            assertTrue(values.contains("+49 170 5550101"), values.toString());
            assertTrue(values.stream().anyMatch(v -> v.contains("Lindenweg 4")), values.toString());
            assertTrue(values.stream().anyMatch(v -> v.contains("turns 40")), values.toString());
            assertTrue(window.getTitle().contains("Contacts"), window.getTitle());
        });
    }

    @Test
    void toolBarAndMenuFollowTheSelection() throws Exception {
        open();
        OnEdt.run(() -> {
            assertTrue(window.toolAction("edit").isEnabled());
            assertTrue(window.menu().item("menu.contacts.edit").isEnabled());
            window.contactList().setSelectedIndices(new int[]{0, 2});
        });
        OnEdt.run(() -> {
            assertFalse(window.toolAction("edit").isEnabled(), "one contact at a time");
            assertTrue(window.toolAction("delete").isEnabled());
            assertTrue(window.toolAction("mail").isEnabled());
            assertFalse(window.menu().item("menu.contacts.call").isEnabled());
            assertTrue(window.card().values().contains("2 contacts selected"), window.card().values().toString());
            window.contactList().clearSelection();
        });
        OnEdt.run(() -> {
            assertFalse(window.toolAction("delete").isEnabled());
            assertTrue(window.toolAction("new").isEnabled());
            assertTrue(window.toolAction("import").isEnabled());
        });
    }

    @Test
    void searchAndSourcesFilterTheList() throws Exception {
        open();
        OnEdt.run(() -> window.searchField().setText("gruen"));
        OnEdt.run(() -> {
            assertEquals(1, window.contactList().getModel().getSize());
            assertEquals("1 of 3 contacts", window.statusText());
            window.searchField().setText("");
        });
        OnEdt.run(() -> presenter.selectSource(new Source.Group("Choir")));
        OnEdt.run(() -> {
            assertEquals(2, window.contactList().getModel().getSize());
            assertEquals(1, window.sourceList().getSelectedIndex());
        });
    }

    @Test
    void theWindowPaints() throws Exception {
        open();
        OnEdt.run(() -> {
            BufferedImage image = new BufferedImage(window.getWidth(), window.getHeight(), BufferedImage.TYPE_INT_RGB);
            var g = image.createGraphics();
            window.getContentPane().paint(g);
            g.dispose();
            assertTrue(window.getWidth() >= 1000, "at least 1000 wide: " + window.getWidth());
        });
    }
}
