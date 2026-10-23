package eu.amadeos.apps.contacts.ui.editor;

import eu.amadeos.apps.contacts.domain.Entry;
import eu.amadeos.apps.contacts.domain.Kind;
import eu.amadeos.commons.i18n.Messages;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.util.List;

/** A phone number or e-mail address with what it is for. */
final class EntryRow extends JPanel implements ContactEditor.Row {
    private final JComboBox<Kind> kind;
    private final JTextField value;
    private final JButton remove;

    EntryRow(Messages msg, List<Kind> kinds, Entry entry) {
        super(new BorderLayout(4, 0));
        kind = ContactEditor.kindBox(msg, kinds, entry.kind());
        value = new JTextField(entry.value(), 22);
        value.setName("editor.value");
        remove = ContactEditor.minus(() -> {
        });
        add(kind, BorderLayout.WEST);
        add(value, BorderLayout.CENTER);
        add(remove, BorderLayout.EAST);
        setBorder(javax.swing.BorderFactory.createEmptyBorder(1, 0, 1, 0));
    }

    Entry entry() {
        return new Entry((Kind) kind.getSelectedItem(), value.getText());
    }

    JTextField valueField() {
        return value;
    }

    JComboBox<Kind> kindBox() {
        return kind;
    }

    @Override
    public void onRemove(Runnable action) {
        for (var l : remove.getActionListeners()) remove.removeActionListener(l);
        remove.addActionListener(e -> action.run());
    }

    @Override
    public void focus() {
        value.requestFocusInWindow();
    }
}
