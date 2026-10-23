package eu.amadeos.apps.contacts.ui.main;

import eu.amadeos.apps.contacts.bootstrap.AmadeContactsApplication;
import eu.amadeos.apps.contacts.domain.Contact;
import eu.amadeos.apps.contacts.domain.Settings;
import eu.amadeos.apps.contacts.domain.StoredContact;
import eu.amadeos.apps.contacts.io.ImportFile;
import eu.amadeos.apps.contacts.ui.editor.ContactEditor;
import eu.amadeos.apps.contacts.ui.transfer.ExportChoice;
import eu.amadeos.apps.contacts.ui.transfer.ExportDialog;
import eu.amadeos.apps.contacts.ui.transfer.ImportChoice;
import eu.amadeos.apps.contacts.ui.transfer.ImportDialog;
import eu.amadeos.apps.contacts.ui.transfer.ImportPreview;
import eu.amadeos.commons.i18n.LocaleSettings;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.swing.AmadeUI;
import eu.amadeos.commons.swing.EdtExecutor;
import eu.amadeos.commons.swing.StandardGlyph;
import eu.amadeos.commons.swing.ToolButtons;
import eu.amadeos.commons.swing.ToolIcon;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.TransferHandler;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The window: toolbar; address books, groups and birthdays on the left; search and the list of contacts in the
 * middle; the selected contact's card on the right.
 */
public final class MainWindow extends JFrame implements MainView {
    private final Messages msg;
    private final LocaleSettings locales;
    private final DefaultListModel<SourceItem> sourceModel = new DefaultListModel<>();
    private final JList<SourceItem> sourceList = new JList<>(sourceModel);
    private final DefaultListModel<StoredContact> contactModel = new DefaultListModel<>();
    private final JList<StoredContact> contactList = new JList<>(contactModel);
    private final JTextField search = new JTextField(16);
    private final JLabel status = new JLabel(" ");
    private final Map<String, Action> actions = new HashMap<>();
    private MainPresenter presenter;
    private MainMenuBar menuBar;
    private ContactCard card;
    private boolean updating;
    private boolean birthdays;

    public MainWindow(Messages msg, LocaleSettings locales) {
        super(msg.get("window.title"));
        EdtExecutor.requireEdt();
        this.msg = msg;
        this.locales = locales;
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        java.net.URL icon = getClass().getResource("/icons/amade-contacts.png");
        if (icon != null) setIconImage(new javax.swing.ImageIcon(icon).getImage());
    }

    public void bind(MainPresenter newPresenter) {
        EdtExecutor.requireEdt();
        presenter = newPresenter;
        menuBar = new MainMenuBar(presenter, msg, locales);
        setJMenuBar(menuBar);
        card = new ContactCard(msg, presenter);

        sourceList.setName("sources");
        sourceList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        sourceList.setCellRenderer(sourceRenderer());
        sourceList.addListSelectionListener(e -> {
            if (!updating && !e.getValueIsAdjusting() && sourceList.getSelectedValue() != null) {
                presenter.selectSource(sourceList.getSelectedValue().source());
            }
        });
        JScrollPane sources = new JScrollPane(sourceList);
        sources.setPreferredSize(scaled(190, 400));

        contactList.setName("contacts");
        contactList.setCellRenderer(contactRenderer());
        contactList.addListSelectionListener(e -> {
            if (!updating && !e.getValueIsAdjusting()) {
                presenter.selectContacts(contactList.getSelectedValuesList().stream().map(StoredContact::uid).toList());
            }
        });
        contactList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && contactList.locationToIndex(e.getPoint()) >= 0) presenter.edit();
            }
        });
        contactList.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "edit");
        contactList.getActionMap().put("edit", run(presenter::edit));

        search.setName("search");
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                searched();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                searched();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                searched();
            }
        });
        search.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "list");
        search.getActionMap().put("list", run(() -> {
            contactList.requestFocusInWindow();
            if (contactList.getSelectedIndex() < 0 && contactModel.size() > 0) contactList.setSelectedIndex(0);
        }));
        search.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "clear");
        search.getActionMap().put("clear", run(() -> search.setText("")));
        JPanel searchRow = new JPanel(new BorderLayout(6, 0));
        searchRow.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
        JLabel searchLabel = new JLabel(msg.get("search.label"));
        searchLabel.setLabelFor(search);
        searchRow.add(searchLabel, BorderLayout.WEST);
        searchRow.add(search, BorderLayout.CENTER);
        status.setName("status");
        status.setForeground(AmadeUI.theme().textInactive());
        JPanel middle = new JPanel(new BorderLayout(0, 2));
        middle.add(searchRow, BorderLayout.NORTH);
        JScrollPane contacts = new JScrollPane(contactList);
        contacts.setPreferredSize(scaled(280, 400));
        middle.add(contacts, BorderLayout.CENTER);
        middle.add(status, BorderLayout.SOUTH);

        JScrollPane cardScroll = new JScrollPane(card);
        cardScroll.setPreferredSize(scaled(430, 400));
        cardScroll.getVerticalScrollBar().setUnitIncrement(16);

        JSplitPane right = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, middle, cardScroll);
        right.setBorder(null);
        right.setResizeWeight(0.35);
        right.setContinuousLayout(true);
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, sources, right);
        split.setBorder(null);
        split.setResizeWeight(0.0);
        split.setContinuousLayout(true);
        JPanel centre = new JPanel(new BorderLayout());
        centre.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        centre.add(split, BorderLayout.CENTER);

        add(toolBar(), BorderLayout.NORTH);
        add(centre, BorderLayout.CENTER);
        setTransferHandler(dropHandler());
        contactList.setTransferHandler(getTransferHandler());
        sourceList.setTransferHandler(getTransferHandler());

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                presenter.exit();
            }

            @Override
            public void windowActivated(WindowEvent e) {
                presenter.reloadIfChanged();
            }
        });
        pack();
        Dimension wanted = scaled(1000, 640);
        setSize(Math.max(getWidth(), wanted.width), Math.max(getHeight(), wanted.height));
        setMinimumSize(new Dimension(640, 400));
        setLocationRelativeTo(null);
    }

    private static Dimension scaled(int w, int h) {
        return new Dimension(Math.round(w * AmadeUI.fontScale()), Math.round(h * AmadeUI.fontScale()));
    }

    private void searched() {
        if (!updating) presenter.search(search.getText());
    }

    // ---------------------------------------------------------------- toolbar

    private JToolBar toolBar() {
        JToolBar bar = new JToolBar();
        bar.setFloatable(false);
        bar.add(ToolButtons.button(action("new", ContactsGlyph.NEW_CONTACT, presenter::newContact)));
        bar.add(ToolButtons.button(action("edit", StandardGlyph.EDIT, presenter::edit)));
        bar.add(ToolButtons.button(action("delete", StandardGlyph.DELETE, presenter::delete)));
        bar.addSeparator();
        bar.add(ToolButtons.button(action("mail", StandardGlyph.MAIL, presenter::writeMail)));
        bar.add(ToolButtons.button(action("call", ContactsGlyph.CALL, presenter::call)));
        bar.addSeparator();
        bar.add(ToolButtons.button(action("import", StandardGlyph.IMPORT, presenter::importFromChooser)));
        bar.add(ToolButtons.button(action("export", StandardGlyph.EXPORT, presenter::export)));
        return bar;
    }

    private Action action(String key, ToolIcon.Glyph glyph, Runnable run) {
        Action action = run(run);
        action.putValue(Action.ACTION_COMMAND_KEY, "tool." + key);
        action.putValue(Action.SHORT_DESCRIPTION, msg.get("tool." + key));
        action.putValue(Action.LARGE_ICON_KEY, new ToolIcon(glyph));
        actions.put(key, action);
        return action;
    }

    private static Action run(Runnable run) {
        return new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                run.run();
            }
        };
    }

    // ---------------------------------------------------------------- list renderers

    private ListCellRenderer<SourceItem> sourceRenderer() {
        return (list, item, index, selected, focus) -> {
            JLabel l = new JLabel(item.label() + "   " + item.count());
            l.setName("source." + index);
            l.setOpaque(true);
            l.setBorder(BorderFactory.createEmptyBorder(3, item.source() instanceof eu.amadeos.apps.contacts.domain.Source.Group ? 18 : 6, 3, 6));
            l.setBackground(selected ? list.getSelectionBackground() : list.getBackground());
            l.setForeground(selected ? list.getSelectionForeground() : list.getForeground());
            if (!(item.source() instanceof eu.amadeos.apps.contacts.domain.Source.Group)) {
                l.setFont(l.getFont().deriveFont(Font.BOLD));
            }
            return l;
        };
    }

    private ListCellRenderer<StoredContact> contactRenderer() {
        int size = Math.round(32 * AmadeUI.fontScale());
        return (list, item, index, selected, focus) -> {
            Contact c = item.contact();
            String sub = presenter.subtitle(c, birthdays);
            String colour = selected ? "" : String.format(" color='#%06x'", AmadeUI.theme().textInactive().getRGB() & 0xFFFFFF);
            JLabel l = new JLabel("<html><b>" + html(c.sortKey(sortByFamilyName)) + "</b>"
                    + (sub.isEmpty() ? "" : "<br><font" + colour + ">" + html(sub) + "</font>") + "</html>",
                    new Avatar(c, size), SwingConstants.LEFT);
            l.setIconTextGap(8);
            l.setOpaque(true);
            l.setBorder(BorderFactory.createEmptyBorder(3, 5, 3, 5));
            l.setBackground(selected ? list.getSelectionBackground() : list.getBackground());
            l.setForeground(selected ? list.getSelectionForeground() : list.getForeground());
            return l;
        };
    }

    private boolean sortByFamilyName;

    private static String html(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    // ---------------------------------------------------------------- MainView

    @Override
    public void showSettings(Settings settings) {
        sortByFamilyName = settings.sortByFamilyName();
        menuBar.update(settings);
        contactList.repaint();
    }

    @Override
    public void showSources(List<SourceItem> sources, int selected) {
        updating = true;
        try {
            sourceModel.clear();
            sourceModel.addAll(sources);
            sourceList.setSelectedIndex(selected);
        } finally {
            updating = false;
        }
    }

    @Override
    public void showContacts(List<StoredContact> contacts, List<String> selectedUids, boolean birthdays, String text) {
        updating = true;
        try {
            this.birthdays = birthdays;
            contactModel.clear();
            contactModel.addAll(contacts);
            List<Integer> indices = new ArrayList<>();
            for (int i = 0; i < contacts.size(); i++) {
                if (selectedUids.contains(contacts.get(i).uid())) indices.add(i);
            }
            contactList.setSelectedIndices(indices.stream().mapToInt(Integer::intValue).toArray());
            if (!indices.isEmpty()) contactList.ensureIndexIsVisible(indices.getFirst());
            status.setText(text);
        } finally {
            updating = false;
        }
        enable(selectedUids.size());
    }

    private void enable(int selected) {
        menuBar.enable(selected);
        actions.get("edit").setEnabled(selected == 1);
        actions.get("call").setEnabled(selected == 1);
        actions.get("delete").setEnabled(selected > 0);
        actions.get("mail").setEnabled(selected > 0);
    }

    @Override
    public void showContact(Optional<StoredContact> contact, String placeholder) {
        card.show(contact, placeholder);
        enable(contactList.getSelectedIndices().length);
        setTitle(contact.map(c -> msg.format("window.title.contact", c.contact().label())).orElse(msg.get("window.title")));
    }

    @Override
    public void showQuery(String query) {
        updating = true;
        try {
            search.setText(query);
        } finally {
            updating = false;
        }
    }

    @Override
    public Optional<Contact> edit(Contact contact, boolean isNew, List<String> knownGroups) {
        return ContactEditor.edit(this, msg, contact, isNew, knownGroups);
    }

    @Override
    public boolean confirmDelete(List<String> names) {
        String text = names.size() == 1 ? msg.format("dialog.delete.one", names.getFirst())
                : msg.format("dialog.delete.many", names.size());
        return JOptionPane.showConfirmDialog(this, text, msg.get("dialog.delete.title"), JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE) == JOptionPane.OK_OPTION;
    }

    @Override
    public Optional<Path> chooseImportFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(msg.get("dialog.import.choose"));
        chooser.setFileFilter(new FileNameExtensionFilter(msg.get("filter.contacts"), "vcf", "vcard", "csv", "ldif", "ldi", "txt"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return Optional.empty();
        return Optional.of(chooser.getSelectedFile().toPath());
    }

    @Override
    public Optional<ImportChoice> chooseImport(ImportFile file, ImportPreview preview) {
        return ImportDialog.choose(this, msg, file, preview);
    }

    @Override
    public Optional<ExportChoice> chooseExport(int selected, int shown, String suggestedName) {
        return ExportDialog.choose(this, msg, selected, shown, suggestedName);
    }

    @Override
    public void copyText(String text) {
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
    }

    @Override
    public Optional<String> clipboardText() {
        try {
            var clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            if (!clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) return Optional.empty();
            return Optional.of((String) clipboard.getData(DataFlavor.stringFlavor));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Override
    public void focusSearch() {
        search.requestFocusInWindow();
        search.selectAll();
    }

    @Override
    public void showError(String message) {
        JOptionPane.showMessageDialog(this, plain(message), msg.get("dialog.error.title"), JOptionPane.ERROR_MESSAGE);
    }

    @Override
    public void showInfo(String message) {
        JOptionPane.showMessageDialog(this, plain(message), msg.get("window.title"), JOptionPane.INFORMATION_MESSAGE);
    }

    @Override
    public void showAbout() {
        JOptionPane.showMessageDialog(this,
                "<html><center><h2>amade-contacts " + AmadeContactsApplication.version() + "</h2>"
                        + "<p>" + msg.get("dialog.about.text") + "</p><br>"
                        + "<small>" + msg.get("dialog.about.libraries") + "</small><br><br>"
                        + "<small>&copy; 2026 datazuul.com &bull; EUPL-1.2</small></center></html>",
                msg.get("dialog.about.title"), JOptionPane.INFORMATION_MESSAGE);
    }

    @Override
    public void close() {
        dispose();
    }

    private static JTextArea plain(String message) {
        JTextArea text = new JTextArea(message);
        text.setEditable(false);
        text.setOpaque(false);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        text.setColumns(40);
        return text;
    }

    /** Files dropped on the window are imported. */
    private TransferHandler dropHandler() {
        return new TransferHandler() {
            @Override
            public boolean canImport(TransferSupport support) {
                return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
            }

            @Override
            @SuppressWarnings("unchecked")
            public boolean importData(TransferSupport support) {
                try {
                    List<File> files = (List<File>) support.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    for (File f : files) presenter.importFile(f.toPath());
                    return true;
                } catch (Exception e) {
                    return false;
                }
            }
        };
    }

    // ---------------------------------------------------------------- for tests

    MainMenuBar menu() {
        return menuBar;
    }

    ContactCard card() {
        return card;
    }

    JList<StoredContact> contactList() {
        return contactList;
    }

    JList<SourceItem> sourceList() {
        return sourceList;
    }

    JTextField searchField() {
        return search;
    }

    String statusText() {
        return status.getText();
    }

    Action toolAction(String key) {
        return actions.get(key);
    }

    JComponent cardComponent() {
        return card;
    }
}
