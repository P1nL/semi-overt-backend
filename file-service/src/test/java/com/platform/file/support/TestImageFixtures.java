package com.platform.file.support;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

public final class TestImageFixtures {
    private TestImageFixtures() {
    }

    public static byte[] png(Color color) throws Exception {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < image.getWidth(); x++) {
            for (int y = 0; y < image.getHeight(); y++) {
                image.setRGB(x, y, color.getRGB());
            }
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    /** Real 2x3 lossless WebP payload, not a dimensions-only RIFF header. */
    public static byte[] webp() {
        return Base64.getDecoder().decode(
                "UklGRh4AAABXRUJQVlA4TBEAAAAvAYAAAAdQs840s/+BiOh/AAA=");
    }
}