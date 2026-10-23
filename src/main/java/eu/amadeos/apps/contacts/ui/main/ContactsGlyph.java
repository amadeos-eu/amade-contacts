package eu.amadeos.apps.contacts.ui.main;

import eu.amadeos.commons.swing.ToolIcon;

import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;

/** The address book's own toolbar icons, next to the {@link eu.amadeos.commons.swing.StandardGlyph}s. */
public enum ContactsGlyph implements ToolIcon.Glyph {
    NEW_CONTACT, CALL;

    // --8<-- [start:paint]
    @Override
    public void paint(ToolIcon.Pen pen) {
        switch (this) {
            case NEW_CONTACT -> {
                person(pen);
                pen.fill(new Rectangle2D.Double(11, 9, 5, 7), pen.paper());
                pen.bold(1.8f);
                pen.line(13.5, 10, 13.5, 15);
                pen.line(11, 12.5, 16, 12.5);
                pen.thin();
            }
            case CALL -> handset(pen);
        }
    }
    // --8<-- [end:paint]

    /** Head and shoulders. */
    private static void person(ToolIcon.Pen pen) {
        pen.fillAndDraw(new Ellipse2D.Double(4, 1.5, 6, 6), pen.paper());
        Path2D body = new Path2D.Double();
        body.moveTo(1.5, 14.5);
        body.curveTo(1.5, 10, 4, 8.5, 7, 8.5);
        body.curveTo(10, 8.5, 12.5, 10, 12.5, 14.5);
        body.closePath();
        pen.fillAndDraw(body, pen.light());
    }

    /** An old telephone receiver, held at an angle. */
    private static void handset(ToolIcon.Pen pen) {
        Path2D receiver = new Path2D.Double();
        receiver.moveTo(2.5, 4);
        receiver.lineTo(5, 1.5);
        receiver.lineTo(7.5, 4.5);
        receiver.lineTo(6, 6.5);
        receiver.curveTo(7, 8.5, 7.5, 9, 9.5, 10);
        receiver.lineTo(11.5, 8.5);
        receiver.lineTo(14.5, 11);
        receiver.lineTo(12, 13.5);
        receiver.curveTo(7, 13.5, 2.5, 9, 2.5, 4);
        receiver.closePath();
        pen.fillAndDraw(receiver, pen.ink());
    }
}
