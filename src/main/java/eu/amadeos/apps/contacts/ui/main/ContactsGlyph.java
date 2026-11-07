package eu.amadeos.apps.contacts.ui.main;

import eu.amadeos.commons.swing.ToolIcon;

import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;

/** The address book's own toolbar icons, next to the {@link eu.amadeos.commons.swing.StandardGlyph}s. */
public enum ContactsGlyph implements ToolIcon.Glyph {
    NEW_CONTACT, CALL, VIDEO_CALL, CHAT;

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
            case VIDEO_CALL -> {
                pen.fillAndDraw(new RoundRectangle2D.Double(1.5, 4.5, 9, 7, 2, 2), pen.light());
                Path2D lens = new Path2D.Double();
                lens.moveTo(10.5, 7);
                lens.lineTo(14.5, 4.5);
                lens.lineTo(14.5, 11.5);
                lens.lineTo(10.5, 9);
                lens.closePath();
                pen.fillAndDraw(lens, pen.light());
            }
            case CHAT -> {
                Area bubble = new Area(new RoundRectangle2D.Double(1.5, 2.5, 13, 8.5, 5, 5));
                Path2D tail = new Path2D.Double();
                tail.moveTo(4, 10);
                tail.lineTo(3.5, 14.5);
                tail.lineTo(8, 10);
                tail.closePath();
                bubble.add(new Area(tail));
                pen.fillAndDraw(bubble, pen.paper());
                for (double x : new double[] {5, 8, 11}) pen.fill(new Ellipse2D.Double(x - 1, 5.75, 2, 2), pen.ink());
            }
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
