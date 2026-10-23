package eu.amadeos.apps.contacts.ui.editor;

import eu.amadeos.apps.contacts.domain.Birthday;
import eu.amadeos.apps.contacts.domain.Contact;
import eu.amadeos.apps.contacts.domain.Entry;
import eu.amadeos.apps.contacts.domain.Kind;
import eu.amadeos.apps.contacts.domain.Name;
import eu.amadeos.apps.contacts.domain.Photo;
import eu.amadeos.apps.contacts.domain.PostalAddress;
import eu.amadeos.apps.contacts.ui.main.Avatar;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.i18n.Mnemonics;
import eu.amadeos.commons.swing.AmadeUI;
import eu.amadeos.commons.swing.DateChooser;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;

/**
 * The form for one contact: name, company, as many phone numbers, e-mail and postal addresses and websites as
 * needed, birthday, groups, notes and photo. Save checks the birthday and that the contact has a name or an
 * address of some kind.
 */
public final class ContactEditor extends JDialog {
    private final Messages msg;
    private final Contact original;
    private final JTextField prefix = field(5);
    private final JTextField given = field(10);
    private final JTextField middle = field(7);
    private final JTextField family = field(10);
    private final JTextField suffix = field(5);
    private final JTextField display = field(24);
    private final JTextField nickname = field(24);
    private final JTextField organization = field(24);
    private final JTextField jobTitle = field(24);
    private final JTextField birthday = field(10);
    private final JTextField newGroups = field(20);
    private final JTextArea note = new JTextArea(3, 30);
    private final Rows<EntryRow> phones;
    private final Rows<EntryRow> emails;
    private final Rows<AddressRow> addresses;
    private final Rows<WebsiteRow> websites;
    private final Map<String, JCheckBox> groupBoxes = new LinkedHashMap<>();
    private final JLabel photoLabel = new JLabel();
    private Photo photo;
    private String shownDisplay = "";
    private Contact result;

    public static Optional<Contact> edit(Component parent, Messages msg, Contact contact, boolean isNew,
                                         List<String> knownGroups) {
        Window owner = parent == null ? null : SwingUtilities.getWindowAncestor(parent);
        if (parent instanceof Window w) owner = w;
        ContactEditor editor = new ContactEditor(owner, msg, contact, isNew, knownGroups);
        editor.setLocationRelativeTo(owner);
        editor.setVisible(true);
        return Optional.ofNullable(editor.result);
    }

    ContactEditor(Window owner, Messages msg, Contact contact, boolean isNew, List<String> knownGroups) {
        super(owner, msg.get(isNew ? "editor.title.new" : "editor.title"), ModalityType.APPLICATION_MODAL);
        this.msg = msg;
        this.original = contact;
        this.photo = contact.photo();
        setName("editor");
        phones = new Rows<>(msg, "editor.add-phone", () -> new EntryRow(msg, Kind.PHONE, new Entry(Kind.MOBILE, "")));
        emails = new Rows<>(msg, "editor.add-email", () -> new EntryRow(msg, Kind.EMAIL, new Entry(Kind.HOME, "")));
        addresses = new Rows<>(msg, "editor.add-address",
                () -> new AddressRow(msg, new PostalAddress(Kind.HOME, "", "", "", "", "")));
        websites = new Rows<>(msg, "editor.add-website", () -> new WebsiteRow(msg, ""));
        fill(contact, knownGroups);

        JPanel form = new JPanel(new GridBagLayout());
        form.setName("editor.form");
        int y = 0;
        JPanel name = new JPanel(new GridLayout(1, 3, 4, 0));
        name.add(given);
        name.add(middle);
        name.add(family);
        y = row(form, y, "editor.name", name);
        JPanel titles = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        titles.add(prefix);
        titles.add(new JLabel(msg.get("editor.suffix")));
        titles.add(suffix);
        y = row(form, y, "editor.prefix", titles);
        y = row(form, y, "editor.display", display);
        y = row(form, y, "editor.nickname", nickname);
        y = row(form, y, "editor.organization", organization);
        y = row(form, y, "editor.job-title", jobTitle);
        y = row(form, y, "editor.phone", phones);
        y = row(form, y, "editor.email", emails);
        y = row(form, y, "editor.address", addresses);
        y = row(form, y, "editor.website", websites);
        JPanel bday = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        bday.add(birthday);
        bday.add(DateChooser.button(this::birthdayDate, d -> birthday.setText(Birthday.of(d).toInput(msg.locale())),
                msg::locale));
        JLabel hint = new JLabel(msg.get("editor.birthday.hint"));
        hint.setForeground(AmadeUI.theme().textInactive());
        bday.add(hint);
        y = row(form, y, "editor.birthday", bday);
        JPanel groups = new JPanel(new BorderLayout(0, 2));
        JPanel boxes = new JPanel(new GridLayout(0, 3, 6, 0));
        groupBoxes.values().forEach(boxes::add);
        if (!groupBoxes.isEmpty()) groups.add(boxes, BorderLayout.NORTH);
        JPanel more = new JPanel(new BorderLayout(4, 0));
        more.add(new JLabel(msg.get("editor.groups.new")), BorderLayout.WEST);
        more.add(newGroups, BorderLayout.CENTER);
        groups.add(more, BorderLayout.SOUTH);
        y = row(form, y, "editor.groups", groups);
        note.setLineWrap(true);
        note.setWrapStyleWord(true);
        note.setName("editor.note");
        row(form, y, "editor.note", new JScrollPane(note));

        JPanel photoPanel = new JPanel();
        photoPanel.setLayout(new BoxLayout(photoPanel, BoxLayout.Y_AXIS));
        photoLabel.setAlignmentX(CENTER_ALIGNMENT);
        photoPanel.add(photoLabel);
        photoPanel.add(Box.createVerticalStrut(6));
        JButton choose = button("editor.photo.choose", this::choosePhoto);
        JButton remove = button("editor.photo.remove", () -> {
            photo = null;
            updatePhoto();
        });
        JPanel photoButtons = new JPanel(new GridLayout(2, 1, 0, 4));
        photoButtons.add(choose);
        photoButtons.add(remove);
        photoButtons.setAlignmentX(CENTER_ALIGNMENT);
        photoButtons.setMaximumSize(photoButtons.getPreferredSize());
        photoPanel.add(photoButtons);
        JPanel west = new JPanel(new BorderLayout());
        west.add(photoPanel, BorderLayout.NORTH);
        updatePhoto();

        JPanel body = new JPanel(new BorderLayout(12, 0));
        body.setBorder(BorderFactory.createEmptyBorder(10, 10, 6, 10));
        body.add(west, BorderLayout.WEST);
        body.add(form, BorderLayout.CENTER);
        JScrollPane scroll = new JScrollPane(body, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(16);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton save = button("editor.save", this::save);
        save.setName("editor.save");
        buttons.add(save);
        buttons.add(button("editor.cancel", this::dispose));
        getRootPane().setDefaultButton(save);
        getRootPane().registerKeyboardAction(e -> dispose(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);

        add(scroll, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
        pack();
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        setSize(Math.min(getWidth() + 24, screen.width - 40), Math.min(getHeight(), screen.height * 9 / 10));
        given.requestFocusInWindow();
    }

    private void fill(Contact c, List<String> knownGroups) {
        Name n = c.name();
        prefix.setText(n.prefix());
        given.setText(n.given());
        middle.setText(n.additional());
        family.setText(n.family());
        suffix.setText(n.suffix());
        display.setText(c.displayName());
        shownDisplay = n.full().equals(c.displayName()) || c.displayName().isEmpty() ? c.displayName() : null;
        nickname.setText(c.nickname());
        organization.setText(c.organization());
        jobTitle.setText(c.jobTitle());
        c.phones().forEach(p -> phones.add(new EntryRow(msg, Kind.PHONE, p)));
        c.emails().forEach(e -> emails.add(new EntryRow(msg, Kind.EMAIL, e)));
        c.addresses().forEach(a -> addresses.add(new AddressRow(msg, a)));
        c.websites().forEach(w -> websites.add(new WebsiteRow(msg, w)));
        if (phones.rows().isEmpty()) phones.add(new EntryRow(msg, Kind.PHONE, new Entry(Kind.MOBILE, "")));
        if (emails.rows().isEmpty()) emails.add(new EntryRow(msg, Kind.EMAIL, new Entry(Kind.HOME, "")));
        birthday.setText(c.birthday() == null ? "" : c.birthday().toInput(msg.locale()));
        TreeSet<String> all = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        all.addAll(knownGroups);
        all.addAll(c.groups());
        for (String g : all) {
            JCheckBox box = new JCheckBox(g, c.groups().stream().anyMatch(x -> x.equalsIgnoreCase(g)));
            box.setName("editor.group." + g);
            groupBoxes.put(g, box);
        }
        note.setText(c.note());
        // the display name follows the name parts until it is typed by hand
        DocumentListener follow = new DocumentListener() {
            public void insertUpdate(DocumentEvent e) {
                followName();
            }

            public void removeUpdate(DocumentEvent e) {
                followName();
            }

            public void changedUpdate(DocumentEvent e) {
                followName();
            }
        };
        for (JTextField f : List.of(prefix, given, middle, family, suffix)) f.getDocument().addDocumentListener(follow);
    }

    private void followName() {
        if (shownDisplay == null || !display.getText().equals(shownDisplay)) {
            shownDisplay = null;
            return;
        }
        shownDisplay = name().full();
        display.setText(shownDisplay);
    }

    private Name name() {
        return new Name(prefix.getText(), given.getText(), middle.getText(), family.getText(), suffix.getText());
    }

    private LocalDate birthdayDate() {
        return Birthday.parse(birthday.getText(), msg.locale())
                .map(b -> LocalDate.of(b.hasYear() ? b.year() : LocalDate.now().getYear() - 30, b.month(),
                        b.month() == 2 && b.day() == 29 && !b.hasYear() ? 28 : b.day()))
                .orElse(null);
    }

    private void updatePhoto() {
        Contact preview = new Contact(original.uid(), name(), display.getText(), "", organization.getText(), "",
                List.of(), List.of(), List.of(), List.of(), null, null, "", photo);
        photoLabel.setIcon(new Avatar(preview, Math.round(96 * AmadeUI.fontScale())));
    }

    private void choosePhoto() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter(msg.get("filter.pictures"), "jpg", "jpeg", "png", "gif", "bmp"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try {
            photo = Photos.read(chooser.getSelectedFile().toPath());
            updatePhoto();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, msg.format("error.photo", e.getMessage()), msg.get("dialog.error.title"),
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    /** The contact as entered, or the reason it cannot be saved. */
    Contact contact() throws IllegalArgumentException {
        String bdayText = birthday.getText().strip();
        Birthday bday = null;
        if (!bdayText.isEmpty()) {
            bday = Birthday.parse(bdayText, msg.locale())
                    .orElseThrow(() -> new IllegalArgumentException(msg.format("editor.error.birthday", bdayText)));
        }
        TreeSet<String> groups = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        groupBoxes.forEach((g, box) -> {
            if (box.isSelected()) groups.add(g);
        });
        for (String g : newGroups.getText().split("[,;]")) {
            if (!g.isBlank()) groups.add(g.strip());
        }
        Contact c = new Contact(original.uid(), name(), display.getText(), nickname.getText(), organization.getText(),
                jobTitle.getText(), phones.rows().stream().map(EntryRow::entry).toList(),
                emails.rows().stream().map(EntryRow::entry).toList(),
                addresses.rows().stream().map(AddressRow::address).toList(),
                websites.rows().stream().map(WebsiteRow::url).toList(), bday, groups, note.getText(), photo);
        if (c.label().isEmpty()) throw new IllegalArgumentException(msg.get("editor.error.empty"));
        return c;
    }

    private void save() {
        try {
            result = contact();
            dispose();
        } catch (IllegalArgumentException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), msg.get("dialog.error.title"), JOptionPane.ERROR_MESSAGE);
        }
    }

    // ---------------------------------------------------------------- layout helpers

    private int row(JPanel form, int y, String key, JComponent component) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = y;
        c.anchor = GridBagConstraints.NORTHEAST;
        c.insets = new Insets(5, 3, 2, 6);
        JLabel label = Mnemonics.apply(new JLabel(), msg.get(key));
        label.setHorizontalAlignment(SwingConstants.RIGHT);
        if (component instanceof JTextField || component instanceof JScrollPane) label.setLabelFor(component);
        form.add(label, c);
        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(2, 0, 2, 0);
        form.add(component, c);
        return y + 1;
    }

    private static JTextField field(int columns) {
        return new JTextField(columns);
    }

    private JButton button(String key, Runnable action) {
        JButton b = Mnemonics.apply(new JButton(), msg.get(key));
        b.setName(key);
        b.addActionListener(e -> action.run());
        return b;
    }

    static JComboBox<Kind> kindBox(Messages msg, List<Kind> kinds, Kind selected) {
        JComboBox<Kind> box = new JComboBox<>(kinds.toArray(Kind[]::new));
        box.setSelectedItem(kinds.contains(selected) ? selected : Kind.OTHER);
        box.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected,
                                                          boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof Kind k) setText(msg.get(k.key()));
                return this;
            }
        });
        Dimension d = box.getPreferredSize();
        box.setPreferredSize(new Dimension(Math.max(d.width, Math.round(96 * AmadeUI.fontScale())), d.height));
        return box;
    }

    static JButton minus(Runnable action) {
        JButton b = new JButton("−");
        b.setMargin(new Insets(0, 6, 0, 6));
        b.setFocusable(false);
        b.addActionListener(e -> action.run());
        return b;
    }

    // ---------------------------------------------------------------- for tests

    JTextField givenField() {
        return given;
    }

    JTextField familyField() {
        return family;
    }

    JTextField displayField() {
        return display;
    }

    JTextField birthdayField() {
        return birthday;
    }

    JTextField newGroupsField() {
        return newGroups;
    }

    Rows<EntryRow> phoneRows() {
        return phones;
    }

    Rows<EntryRow> emailRows() {
        return emails;
    }

    Rows<AddressRow> addressRows() {
        return addresses;
    }

    Optional<Contact> result() {
        return Optional.ofNullable(result);
    }

    void pressSave() {
        save();
    }

    /** A list of rows with "+ Add ..." below it. */
    static final class Rows<R extends JComponent & Row> extends JPanel {
        private final JPanel list = new JPanel();
        private final List<R> rows = new ArrayList<>();
        private final java.util.function.Supplier<R> factory;

        Rows(Messages msg, String addKey, java.util.function.Supplier<R> factory) {
            super(new BorderLayout(0, 2));
            this.factory = factory;
            list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
            add(list, BorderLayout.CENTER);
            JButton add = new JButton("+ " + msg.get(addKey));
            add.setName(addKey);
            add.setMargin(new Insets(0, 6, 0, 6));
            add.setFocusable(false);
            add.addActionListener(e -> {
                R row = factory.get();
                add(row);
                row.focus();
            });
            JPanel south = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            south.add(add);
            add(south, BorderLayout.SOUTH);
        }

        void add(R row) {
            rows.add(row);
            row.onRemove(() -> {
                rows.remove(row);
                list.remove(row);
                refresh();
            });
            list.add(row);
            refresh();
        }

        List<R> rows() {
            return List.copyOf(rows);
        }

        R addRow() {
            R row = factory.get();
            add(row);
            return row;
        }

        private void refresh() {
            list.revalidate();
            list.repaint();
            Window w = SwingUtilities.getWindowAncestor(this);
            if (w != null && w.isVisible()) {
                Dimension now = w.getSize();
                Dimension wanted = w.getPreferredSize();
                Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
                if (wanted.height > now.height) w.setSize(now.width, Math.min(wanted.height, screen.height * 9 / 10));
            }
        }
    }

    /** One row of a {@link Rows} list. */
    interface Row {
        void onRemove(Runnable action);

        void focus();
    }
}
