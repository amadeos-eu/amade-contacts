package eu.amadeos.apps.contacts.ui.main;

import eu.amadeos.apps.contacts.domain.Contact;
import eu.amadeos.apps.contacts.domain.Photo;

import javax.imageio.ImageIO;
import javax.swing.Icon;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;

/**
 * A contact's photo in a circle, or its initials on a colour that stays the same for the same name. The colours
 * are muted, so they suit every desktop theme.
 */
public final class Avatar implements Icon {
    static final List<Color> COLOURS = List.of(new Color(0x5B7FA6), new Color(0x8C6B9E), new Color(0x6E9B62),
            new Color(0xB07A4A), new Color(0xA65B5B), new Color(0x4F8F8F), new Color(0x7A7F3E), new Color(0x9B6E8C));

    private final String initials;
    private final Color colour;
    private final BufferedImage photo;
    private final int size;

    public Avatar(Contact contact, int size) {
        this.initials = contact.initials();
        this.colour = colour(contact.uid());
        this.photo = image(contact.photo());
        this.size = size;
    }

    /** From the UID, so a contact keeps its colour when it is renamed. */
    static Color colour(String uid) {
        return COLOURS.get(Math.floorMod(uid.hashCode(), COLOURS.size()));
    }

    static BufferedImage image(Photo photo) {
        if (photo == null) return null;
        try {
            return ImageIO.read(new ByteArrayInputStream(photo.data()));
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    boolean hasPhoto() {
        return photo != null;
    }

    @Override
    public void paintIcon(Component c, Graphics g0, int x, int y) {
        Graphics2D g = (Graphics2D) g0.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            Ellipse2D circle = new Ellipse2D.Double(x, y, size, size);
            if (photo != null) {
                g.clip(circle);
                // fill the circle: the shorter side of the photo is the diameter
                double scale = (double) size / Math.min(photo.getWidth(), photo.getHeight());
                int w = (int) Math.round(photo.getWidth() * scale);
                int h = (int) Math.round(photo.getHeight() * scale);
                g.drawImage(photo, x + (size - w) / 2, y + (size - h) / 2, w, h, null);
                return;
            }
            g.setColor(colour);
            g.fill(circle);
            g.setColor(Color.WHITE);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.max(8, size * 2 / 5)));
            FontMetrics fm = g.getFontMetrics();
            g.drawString(initials, x + (size - fm.stringWidth(initials)) / 2f,
                    y + (size + fm.getAscent() - fm.getDescent()) / 2f);
        } finally {
            g.dispose();
        }
    }

    @Override
    public int getIconWidth() {
        return size;
    }

    @Override
    public int getIconHeight() {
        return size;
    }
}
