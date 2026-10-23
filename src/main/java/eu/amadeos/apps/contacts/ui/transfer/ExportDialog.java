package eu.amadeos.apps.contacts.ui.transfer;

import eu.amadeos.apps.contacts.io.FileFormat;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.i18n.Mnemonics;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.GridLayout;
import java.io.File;
import java.util.Optional;

/**
 * The file dialog for export with the format and which contacts beside it; the file name follows the format's
 * extension.
 */
public final class ExportDialog {
    private ExportDialog() {
    }

    public static Optional<ExportChoice> choose(Component parent, Messages msg, int selected, int shown, String name) {
        JComboBox<FileFormat> format = new JComboBox<>(FileFormat.EXPORT.toArray(FileFormat[]::new));
        format.setName("export.format");
        format.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected,
                                                          boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof FileFormat f) setText(msg.get(f.key()));
                return this;
            }
        });
        JRadioButton onlySelected = Mnemonics.apply(new JRadioButton(), msg.format("export.selected", selected));
        JRadioButton all = Mnemonics.apply(new JRadioButton(), msg.format("export.shown", shown));
        ButtonGroup group = new ButtonGroup();
        group.add(onlySelected);
        group.add(all);
        onlySelected.setEnabled(selected > 0);
        (selected > 1 ? onlySelected : all).setSelected(true);

        JPanel accessory = new JPanel(new GridLayout(0, 1, 0, 2));
        accessory.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 0));
        JLabel formatLabel = Mnemonics.apply(new JLabel(), msg.get("export.format"));
        formatLabel.setLabelFor(format);
        accessory.add(formatLabel);
        accessory.add(format);
        accessory.add(onlySelected);
        accessory.add(all);
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.add(accessory, BorderLayout.NORTH);

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(msg.get("export.title"));
        chooser.setAccessory(wrap);
        String base = name.replaceAll("[/\\\\:*?\"<>|]", "_");
        chooser.setSelectedFile(new File(base + ".vcf"));
        format.addActionListener(e -> {
            File current = chooser.getSelectedFile();
            String fileName = current == null ? base : current.getName();
            int dot = fileName.lastIndexOf('.');
            if (dot > 0) fileName = fileName.substring(0, dot);
            chooser.setSelectedFile(new File(chooser.getCurrentDirectory(), fileName + "." + ((FileFormat) format.getSelectedItem()).extension()));
        });
        while (true) {
            if (chooser.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION) return Optional.empty();
            File file = chooser.getSelectedFile();
            FileFormat f = (FileFormat) format.getSelectedItem();
            if (!file.getName().contains(".")) file = new File(file.getParentFile(), file.getName() + "." + f.extension());
            if (file.exists() && JOptionPane.showConfirmDialog(parent, msg.format("export.overwrite", file.getName()),
                    msg.get("export.title"), JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) {
                continue;
            }
            return Optional.of(new ExportChoice(file.toPath(), f, onlySelected.isSelected()));
        }
    }
}
