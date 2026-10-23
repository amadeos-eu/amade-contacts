package eu.amadeos.apps.contacts.ui.transfer;

import eu.amadeos.apps.contacts.domain.Kind;
import eu.amadeos.apps.contacts.domain.Source;
import eu.amadeos.apps.contacts.io.ColumnMapping;
import eu.amadeos.apps.contacts.io.CsvTable;
import eu.amadeos.apps.contacts.io.Field;
import eu.amadeos.apps.contacts.io.FileFormat;
import eu.amadeos.apps.contacts.io.ImportFile;
import eu.amadeos.apps.contacts.io.Imported;
import eu.amadeos.apps.contacts.io.TextFiles;
import eu.amadeos.apps.contacts.service.ContactImport;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.i18n.Mnemonics;
import eu.amadeos.commons.swing.AmadeUI;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.DefaultCellEditor;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Import of a vCard, CSV or LDIF file: format and encoding (found by themselves, changeable), for CSV the field of
 * every column with an example value, the address book, what happens to known contacts, and a group for them all.
 */
public final class ImportDialog extends JDialog {
    /** The formats the dialog offers for reading a file. */
    static final List<FileFormat> FORMATS = List.of(FileFormat.VCARD_4, FileFormat.CSV_GOOGLE, FileFormat.CSV_OUTLOOK,
            FileFormat.CSV, FileFormat.LDIF);

    /** A field with its kind, one entry of a column's choice box. */
    record Choice(Field field, Kind kind) {
        static List<Choice> all() {
            List<Choice> list = new ArrayList<>();
            for (Field f : Field.values()) {
                if (!f.hasKind()) {
                    list.add(new Choice(f, Kind.OTHER));
                    continue;
                }
                List<Kind> kinds = f == Field.PHONE ? Kind.PHONE : f == Field.EMAIL ? Kind.EMAIL : Kind.ADDRESS;
                for (Kind k : kinds) list.add(new Choice(f, k));
            }
            return list;
        }
    }

    private final Messages msg;
    private final ImportPreview preview;
    private final JComboBox<FileFormat> format = new JComboBox<>(FORMATS.toArray(FileFormat[]::new));
    private final JComboBox<Charset> charset = new JComboBox<>(TextFiles.CHOICES.toArray(Charset[]::new));
    private final JComboBox<Source.Book> book;
    private final JRadioButton merge;
    private final JRadioButton addNew;
    private final JCheckBox toGroup;
    private final JTextField groupName;
    private final JLabel found = new JLabel(" ");
    private final ColumnsModel columns = new ColumnsModel();
    private final JTable table;
    private final JPanel columnsPanel = new JPanel(new BorderLayout());
    private final JButton ok;
    private ImportFile file;
    private ColumnMapping mapping;
    private int count;
    private ImportChoice result;

    public static Optional<ImportChoice> choose(Component parent, Messages msg, ImportFile file, ImportPreview preview) {
        Window owner = parent instanceof Window w ? w : parent == null ? null : SwingUtilities.getWindowAncestor(parent);
        ImportDialog dialog = new ImportDialog(owner, msg, file, preview);
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
        return Optional.ofNullable(dialog.result);
    }

    ImportDialog(Window owner, Messages msg, ImportFile file, ImportPreview preview) {
        super(owner, msg.format("import.title", file.name()), ModalityType.APPLICATION_MODAL);
        this.msg = msg;
        this.preview = preview;
        this.file = file;
        // after msg: the table asks the model for its column titles right away
        table = new JTable(columns);
        setName("import");
        book = new JComboBox<>(preview.books().toArray(Source.Book[]::new));
        merge = Mnemonics.apply(new JRadioButton(), msg.get("import.merge"));
        addNew = Mnemonics.apply(new JRadioButton(), msg.get("import.add-new"));
        toGroup = Mnemonics.apply(new JCheckBox(), msg.get("import.group"));
        groupName = new JTextField(preview.groupName(), 20);
        ok = new JButton();
        ok.setName("import.ok");

        format.setName("import.format");
        format.setSelectedItem(file.format() == FileFormat.VCARD_3 ? FileFormat.VCARD_4 : file.format());
        format.setRenderer(renderer(v -> msg.get(((FileFormat) v).key() + ".import")));
        charset.setName("import.charset");
        if (!TextFiles.CHOICES.contains(file.charset())) charset.addItem(file.charset());
        charset.setSelectedItem(file.charset());
        book.setRenderer(renderer(v -> ((Source.Book) v).name()));

        JPanel top = new JPanel(new GridBagLayout());
        int y = 0;
        y = row(top, y, "import.file", new JLabel(file.name()));
        y = row(top, y, "import.format", format);
        y = row(top, y, "import.charset", charset);
        if (preview.books().size() > 1) row(top, y, "import.book", book);

        table.setName("import.columns");
        table.setRowHeight(Math.round(table.getRowHeight() * 1.25f));
        JComboBox<Choice> choices = new JComboBox<>(Choice.all().toArray(Choice[]::new));
        choices.setRenderer(renderer(v -> choiceLabel((Choice) v)));
        table.getColumnModel().getColumn(2).setCellEditor(new DefaultCellEditor(choices));
        table.getColumnModel().getColumn(2).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            protected void setValue(Object value) {
                setText(value instanceof Choice c ? choiceLabel(c) : "");
            }
        });
        table.getColumnModel().getColumn(0).setPreferredWidth(170);
        table.getColumnModel().getColumn(1).setPreferredWidth(170);
        table.getColumnModel().getColumn(2).setPreferredWidth(170);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setPreferredSize(new Dimension(Math.round(540 * AmadeUI.fontScale()), Math.round(220 * AmadeUI.fontScale())));
        columnsPanel.setBorder(BorderFactory.createTitledBorder(msg.get("import.columns")));
        columnsPanel.add(scroll, BorderLayout.CENTER);

        ButtonGroup known = new ButtonGroup();
        known.add(merge);
        known.add(addNew);
        merge.setSelected(true);
        merge.setName("import.merge");
        addNew.setName("import.add-new");
        toGroup.setSelected(true);
        toGroup.setName("import.group");
        groupName.setName("import.group.name");
        toGroup.addActionListener(e -> groupName.setEnabled(toGroup.isSelected()));
        JPanel options = new JPanel(new GridLayout(0, 1));
        found.setName("import.found");
        options.add(found);
        options.add(merge);
        options.add(addNew);
        JPanel group = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        group.add(toGroup);
        group.add(groupName);
        options.add(group);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        ok.addActionListener(e -> accept());
        buttons.add(ok);
        JButton cancel = Mnemonics.apply(new JButton(), msg.get("import.cancel"));
        cancel.addActionListener(e -> dispose());
        buttons.add(cancel);
        getRootPane().setDefaultButton(ok);
        getRootPane().registerKeyboardAction(e -> dispose(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);

        JPanel south = new JPanel(new BorderLayout());
        south.add(options, BorderLayout.CENTER);
        south.add(buttons, BorderLayout.SOUTH);
        JPanel body = new JPanel(new BorderLayout(0, 8));
        body.setBorder(BorderFactory.createEmptyBorder(10, 10, 6, 10));
        body.add(top, BorderLayout.NORTH);
        body.add(columnsPanel, BorderLayout.CENTER);
        body.add(south, BorderLayout.SOUTH);
        add(body);

        format.addActionListener(e -> reread());
        charset.addActionListener(e -> reread());
        reread();
        pack();
    }

    /** Reads the file again in the chosen format and encoding, guessing the columns anew. */
    private void reread() {
        file = file.withFormat((FileFormat) format.getSelectedItem()).withCharset((Charset) charset.getSelectedItem());
        boolean csv = file.isCsv();
        columnsPanel.setVisible(csv);
        if (csv) {
            CsvTable t = file.table();
            mapping = ColumnMapping.guess(t.headers());
            columns.set(t, mapping);
        } else {
            mapping = new ColumnMapping(List.of());
            columns.set(new CsvTable(List.of(), List.of()), mapping);
        }
        update();
        if (isVisible()) pack();
    }

    private void update() {
        List<Imported> contacts;
        try {
            contacts = file.contacts(mapping, preview.locale());
        } catch (RuntimeException e) {
            contacts = List.of();
        }
        count = contacts.size();
        int duplicates = count == 0 ? 0 : preview.duplicates().applyAsInt(contacts);
        found.setText(msg.format("import.found", count, duplicates));
        Mnemonics.apply(merge, msg.format("import.merge", duplicates));
        Mnemonics.apply(ok, msg.format("import.ok", count));
        ok.setEnabled(count > 0);
    }

    private void accept() {
        String group = groupName.getText().strip();
        Optional<String> g = toGroup.isSelected() && !group.isEmpty() ? Optional.of(group) : Optional.empty();
        Source.Book target = (Source.Book) book.getSelectedItem();
        result = new ImportChoice(file, mapping, new ContactImport.Options(target.id(), merge.isSelected(), g));
        dispose();
    }

    private String choiceLabel(Choice c) {
        String field = msg.get(c.field().key());
        return c.field().hasKind() && c.kind() != Kind.OTHER ? field + " (" + msg.get(c.kind().key()) + ")" : field;
    }

    private static <T> DefaultListCellRenderer renderer(java.util.function.Function<Object, String> text) {
        return new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected,
                                                          boolean focus) {
                super.getListCellRendererComponent(list, value, index, selected, focus);
                if (value != null) setText(text.apply(value));
                return this;
            }
        };
    }

    private int row(JPanel panel, int y, String key, JComponent component) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = y;
        c.anchor = GridBagConstraints.EAST;
        c.insets = new Insets(2, 3, 2, 6);
        JLabel label = Mnemonics.apply(new JLabel(), msg.get(key));
        label.setLabelFor(component);
        panel.add(label, c);
        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        panel.add(component, c);
        return y + 1;
    }

    /** CSV column, an example value and the field. */
    private final class ColumnsModel extends AbstractTableModel {
        private CsvTable table = new CsvTable(List.of(), List.of());
        private final List<String> examples = new ArrayList<>();

        void set(CsvTable newTable, ColumnMapping newMapping) {
            table = newTable;
            examples.clear();
            for (int c = 0; c < table.headers().size(); c++) {
                String example = "";
                for (List<String> row : table.rows()) {
                    example = CsvTable.cell(row, c);
                    if (!example.isEmpty()) break;
                }
                examples.add(example);
            }
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return table.headers().size();
        }

        @Override
        public int getColumnCount() {
            return 3;
        }

        @Override
        public String getColumnName(int column) {
            return msg.get(switch (column) {
                case 0 -> "import.column.title";
                case 1 -> "import.column.example";
                default -> "import.column.field";
            });
        }

        @Override
        public Object getValueAt(int row, int column) {
            return switch (column) {
                case 0 -> table.headers().get(row);
                case 1 -> examples.get(row);
                default -> {
                    ColumnMapping.Target t = mapping.target(row);
                    yield new Choice(t.field(), t.field().hasKind() ? t.kind() : Kind.OTHER);
                }
            };
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            return column == 2;
        }

        @Override
        public void setValueAt(Object value, int row, int column) {
            if (column != 2 || !(value instanceof Choice c)) return;
            mapping = mapping.with(row, c.field(), c.kind());
            update();
            fireTableRowsUpdated(row, row);
        }
    }

    // ---------------------------------------------------------------- for tests

    JComboBox<FileFormat> formatBox() {
        return format;
    }

    JTable columnTable() {
        return table;
    }

    String foundText() {
        return found.getText();
    }

    void pressImport() {
        accept();
    }

    JCheckBox groupBox() {
        return toGroup;
    }

    JRadioButton addNewButton() {
        return addNew;
    }

    Optional<ImportChoice> result() {
        return Optional.ofNullable(result);
    }
}
