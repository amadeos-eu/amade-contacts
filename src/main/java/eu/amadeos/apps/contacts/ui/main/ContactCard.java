package eu.amadeos.apps.contacts.ui.main;

import eu.amadeos.apps.contacts.domain.Contact;
import eu.amadeos.apps.contacts.domain.Entry;
import eu.amadeos.apps.contacts.domain.PostalAddress;
import eu.amadeos.apps.contacts.domain.StoredContact;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.swing.AmadeUI;
import eu.amadeos.commons.theme.DesktopTheme;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The card on the right: photo or initials, name, company, and the phone numbers, e-mail and postal addresses,
 * birthday, websites and notes, each with a button that acts on it.
 */
final class ContactCard extends JPanel implements Scrollable {
    private final Messages msg;
    private final MainPresenter presenter;

    ContactCard(Messages msg, MainPresenter presenter) {
        this.msg = msg;
        this.presenter = presenter;
        setName("card");
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        AmadeUI.style(this, (c, t) -> c.setBackground(t.fieldBackground()));
    }

    void show(Optional<StoredContact> stored, String placeholder) {
        removeAll();
        if (stored.isEmpty()) {
            JLabel empty = new JLabel(placeholder, SwingConstants.CENTER);
            empty.setName("card.placeholder");
            empty.setForeground(AmadeUI.theme().textInactive());
            empty.setAlignmentX(LEFT_ALIGNMENT);
            add(Box.createVerticalStrut(40));
            add(empty);
        } else {
            build(stored.get().contact());
        }
        revalidate();
        repaint();
    }

    private void build(Contact c) {
        DesktopTheme t = AmadeUI.theme();
        JPanel head = new JPanel(new BorderLayout(12, 0));
        head.setOpaque(false);
        head.setAlignmentX(LEFT_ALIGNMENT);
        head.add(new JLabel(new Avatar(c, Math.round(72 * AmadeUI.fontScale()))), BorderLayout.WEST);
        JPanel names = new JPanel();
        names.setLayout(new BoxLayout(names, BoxLayout.Y_AXIS));
        names.setOpaque(false);
        JLabel name = new JLabel(c.label());
        name.setName("card.name");
        name.setFont(name.getFont().deriveFont(Font.BOLD, name.getFont().getSize2D() * 1.6f));
        names.add(Box.createVerticalGlue());
        names.add(name);
        String company = String.join(", ", List.of(c.jobTitle(), c.label().equals(c.organization()) ? "" : c.organization())
                .stream().filter(s -> !s.isEmpty()).toList());
        if (!c.nickname().isEmpty()) names.add(dim("„" + c.nickname() + "“"));
        if (!company.isEmpty()) names.add(new JLabel(company));
        if (!c.groups().isEmpty()) names.add(dim(msg.format("card.groups", String.join(", ", c.groups()))));
        names.add(Box.createVerticalGlue());
        head.add(names, BorderLayout.CENTER);
        head.setMaximumSize(new Dimension(Integer.MAX_VALUE, head.getPreferredSize().height));
        add(head);
        add(Box.createVerticalStrut(12));

        if (!c.phones().isEmpty()) {
            section("card.phone");
            for (Entry p : c.phones()) row(msg.get(p.kind().key()), p.value(), false, "card.call", () -> presenter.call(p.value()));
        }
        if (!c.emails().isEmpty()) {
            section("card.email");
            for (Entry e : c.emails()) {
                row(msg.get(e.kind().key()), e.value(), true, "card.write", () -> presenter.writeMail(e.value()));
            }
        }
        if (!c.addresses().isEmpty()) {
            section("card.address");
            for (PostalAddress a : c.addresses()) {
                List<String> lines = a.lines();
                for (int i = 0; i < lines.size(); i++) {
                    row(i == 0 ? msg.get(a.kind().key()) : "", lines.get(i), false, i == 0 ? "card.map" : null,
                            () -> presenter.showOnMap(a));
                }
            }
        }
        if (c.birthday() != null) {
            section("card.birthday");
            row("", presenter.birthdayText(c.birthday()), false, null, null);
        }
        if (!c.websites().isEmpty()) {
            section("card.website");
            for (String w : c.websites()) row("", w, true, "card.open", () -> presenter.openWebsite(w));
        }
        if (!c.note().isEmpty()) {
            section("card.note");
            JTextArea note = new JTextArea(c.note());
            note.setName("card.note.text");
            note.setEditable(false);
            note.setLineWrap(true);
            note.setWrapStyleWord(true);
            note.setOpaque(false);
            note.setFont(new JLabel().getFont());
            note.setBorder(BorderFactory.createEmptyBorder(0, kindWidth() + 8, 0, 0));
            note.setAlignmentX(LEFT_ALIGNMENT);
            add(note);
        }
        add(Box.createVerticalGlue());
    }

    private void section(String key) {
        JLabel h = new JLabel(msg.get(key).toUpperCase(msg.locale()));
        h.setFont(h.getFont().deriveFont(Font.BOLD, h.getFont().getSize2D() * 0.8f));
        h.setForeground(AmadeUI.theme().textInactive());
        h.setAlignmentX(LEFT_ALIGNMENT);
        h.setBorder(BorderFactory.createEmptyBorder(8, 0, 2, 0));
        add(h);
    }

    private void row(String kind, String value, boolean link, String button, Runnable action) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.setAlignmentX(LEFT_ALIGNMENT);
        JLabel k = dim(kind);
        k.setPreferredSize(new Dimension(kindWidth(), k.getPreferredSize().height));
        row.add(k, BorderLayout.WEST);
        JLabel v = new JLabel(value);
        v.setName("card.value");
        if (link) v.setForeground(linkColour());
        row.add(v, BorderLayout.CENTER);
        if (button != null) {
            JButton b = new JButton(msg.get(button));
            b.setName(button);
            b.setMargin(new Insets(0, 6, 0, 6));
            b.setFont(b.getFont().deriveFont(b.getFont().getSize2D() * 0.9f));
            b.setFocusable(false);
            b.addActionListener(e -> action.run());
            row.add(b, BorderLayout.EAST);
        }
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height + 2));
        add(row);
    }

    private int kindWidth() {
        return Math.round(70 * AmadeUI.fontScale());
    }

    /** A blue that is readable on the field colour of light and dark themes. */
    private static Color linkColour() {
        return AmadeUI.theme().isDark() ? new Color(0x8AB4F8) : new Color(0x1F4F8F);
    }

    private static JLabel dim(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(AmadeUI.theme().textInactive());
        return l;
    }

    /** The texts shown, for tests. */
    List<String> values() {
        List<String> result = new ArrayList<>();
        collect(this, result);
        return result;
    }

    private static void collect(Component c, List<String> into) {
        if (c instanceof JLabel l && l.getText() != null && !l.getText().isEmpty()) into.add(l.getText());
        if (c instanceof JTextArea a) into.add(a.getText());
        if (c instanceof JComponent j) {
            for (Component child : j.getComponents()) collect(child, into);
        }
    }

    // ---------------------------------------------------------------- Scrollable: as wide as the viewport

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) {
        return 16;
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
        return visible.height - 16;
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return true;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return getParent() != null && getParent().getHeight() > getPreferredSize().height;
    }
}
