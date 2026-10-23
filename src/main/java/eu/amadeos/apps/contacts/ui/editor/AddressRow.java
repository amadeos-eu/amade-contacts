package eu.amadeos.apps.contacts.ui.editor;

import eu.amadeos.apps.contacts.domain.Kind;
import eu.amadeos.apps.contacts.domain.PostalAddress;
import eu.amadeos.commons.i18n.Messages;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

/** A postal address: street, postal code and city, region and country, each field with its hint as tooltip. */
final class AddressRow extends JPanel implements ContactEditor.Row {
    private final JComboBox<Kind> kind;
    private final JTextField street;
    private final JTextField postalCode;
    private final JTextField city;
    private final JTextField region;
    private final JTextField country;
    private final JButton remove;

    AddressRow(Messages msg, PostalAddress address) {
        super(new BorderLayout(4, 0));
        kind = ContactEditor.kindBox(msg, Kind.ADDRESS, address.kind());
        street = field(address.street().replace("\n", "; "), 22, msg.get("editor.address.street"));
        postalCode = field(address.postalCode(), 6, msg.get("editor.address.postal-code"));
        city = field(address.city(), 14, msg.get("editor.address.city"));
        region = field(address.region(), 10, msg.get("editor.address.region"));
        country = field(address.country(), 10, msg.get("editor.address.country"));
        remove = ContactEditor.minus(() -> {
        });
        JPanel fields = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(0, 0, 2, 4);
        c.gridx = 0;
        c.gridy = 0;
        c.gridwidth = 2;
        c.weightx = 1;
        fields.add(street, c);
        c.gridy = 1;
        c.gridwidth = 1;
        c.weightx = 0;
        fields.add(postalCode, c);
        c.gridx = 1;
        c.weightx = 1;
        fields.add(city, c);
        c.gridx = 0;
        c.gridy = 2;
        c.weightx = 0;
        fields.add(region, c);
        c.gridx = 1;
        c.weightx = 1;
        fields.add(country, c);
        JPanel west = new JPanel(new BorderLayout());
        west.add(kind, BorderLayout.NORTH);
        JPanel east = new JPanel(new BorderLayout());
        east.add(remove, BorderLayout.NORTH);
        add(west, BorderLayout.WEST);
        add(fields, BorderLayout.CENTER);
        add(east, BorderLayout.EAST);
        setBorder(BorderFactory.createEmptyBorder(1, 0, 4, 0));
    }

    private static JTextField field(String value, int columns, String hint) {
        JTextField f = new JTextField(value, columns);
        f.setToolTipText(hint);
        f.setName("editor.address." + hint);
        return f;
    }

    PostalAddress address() {
        return new PostalAddress((Kind) kind.getSelectedItem(), street.getText().replaceAll("\\s*;\\s*", "\n"),
                postalCode.getText(), city.getText(), region.getText(), country.getText());
    }

    void set(String newStreet, String newPostalCode, String newCity, String newCountry) {
        street.setText(newStreet);
        postalCode.setText(newPostalCode);
        city.setText(newCity);
        country.setText(newCountry);
    }

    @Override
    public void onRemove(Runnable action) {
        for (var l : remove.getActionListeners()) remove.removeActionListener(l);
        remove.addActionListener(e -> action.run());
    }

    @Override
    public void focus() {
        street.requestFocusInWindow();
    }
}
