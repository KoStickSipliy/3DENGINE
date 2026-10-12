package com.cgengine.render_engine;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Locale;

/** Immutable local ARGB texture; ImageIO only decodes the image file. */
public final class Texture2D {
    private final int width, height;
    private final int[] pixels;

    public Texture2D(int width, int height, int[] pixels) {
        if (width < 1 || width > 4096 || height < 1 || height > 4096
                || pixels == null || pixels.length != width * height)
            throw new IllegalArgumentException("Texture dimensions must be in 1..4096 and match pixels");
        this.width = width; this.height = height; this.pixels = pixels.clone();
        // D-03 has no alpha channel: every sampled texel is opaque.
        for (int i = 0; i < this.pixels.length; i++) this.pixels[i] |= 0xFF000000;
    }

    public static Texture2D load(File file) throws IOException {
        if (file == null) throw new IOException("Texture file is not selected");
        String name = file.getName().toLowerCase(Locale.ROOT);
        if (!(name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg")))
            throw new IOException("Only PNG, JPG and JPEG textures are supported");
        try (ImageInputStream input = ImageIO.createImageInputStream(file)) {
            if (input == null) throw new IOException("Texture file cannot be read");
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IOException("The selected file is not a readable image");
            ImageReader reader = readers.next();
            try {
                String format = reader.getFormatName();
                if (!(format.equalsIgnoreCase("png") || format.equalsIgnoreCase("jpeg")))
                    throw new IOException("Only PNG, JPG and JPEG textures are supported");
                reader.setInput(input);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                // Reject dimensions before allocating/decoding an oversized bitmap.
                if (width < 1 || height < 1 || width > 4096 || height > 4096)
                    throw new IOException("Texture dimensions must be in 1..4096");
                BufferedImage image = reader.read(0);
                return new Texture2D(width, height, image.getRGB(0, 0, width, height, null, 0, width));
            } finally { reader.dispose(); }
        }
    }
    public int width() { return width; }
    public int height() { return height; }
    int pixel(int x, int y) { return pixels[y * width + x]; }
}
