package eu.amadeos.apps.contacts.ui.editor;

import eu.amadeos.commons.i18n.Messages;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.BorderLayout;

/** A website address. */
final class WebsiteRow extends JPanel implements ContactEditor.Row {
    private final JTextField url;
    private final JButton remove;

    WebsiteRow(Messages msg, String value) {
        super(new BorderLayout(4, 0));
        url = new JTextField(value, 28);
        remove = ContactEditor.minus(() -> {
        });
        add(url, BorderLayout.CENTER);
        add(remove, BorderLayout.EAST);
        setBorder(javax.swing.BorderFactory.createEmptyBorder(1, 0, 1, 0));
    }

    String url() {
        return url.getText();
    }

    @Override
    public void onRemove(Runnable action) {
        for (var l : remove.getActionListeners()) remove.removeActionListener(l);
        remove.addActionListener(e -> action.run());
    }

    @Override
    public void focus() {
        url.requestFocusInWindow();
    }
}
