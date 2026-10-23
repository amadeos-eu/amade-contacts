package eu.amadeos.apps.contacts.ui.editor;

import eu.amadeos.apps.contacts.domain.Photo;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;

/** Pictures for contacts: cut to a square around the middle and made small, so the vCard stays small too. */
public final class Photos {
    /** Width and height of a contact photo in pixels. */
    public static final int SIZE = 256;

    private Photos() {
    }

    public static Photo read(Path file) throws IOException {
        BufferedImage image = ImageIO.read(file.toFile());
        if (image == null) throw new IOException("Not a picture: " + file.getFileName());
        return fromImage(image);
    }

    public static Photo fromImage(BufferedImage image) throws IOException {
        int side = Math.min(image.getWidth(), image.getHeight());
        int target = Math.min(SIZE, side);
        BufferedImage square = new BufferedImage(target, target, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = square.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        int x = (image.getWidth() - side) / 2;
        int y = (image.getHeight() - side) / 2;
        g.drawImage(image, 0, 0, target, target, x, y, x + side, y + side, null);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        try (ImageOutputStream stream = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(stream);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(0.85f);
            writer.write(null, new IIOImage(square, null, null), param);
        } finally {
            writer.dispose();
        }
        return new Photo(out.toByteArray(), "image/jpeg");
    }
}
