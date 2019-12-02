package ru.ok.newyear.newyear.draw;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import ru.ok.newyear.newyear.utils.Files;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class Drawer {

    private static final Logger logger = LoggerFactory.getLogger(Drawer.class);

    private static final String READY = "ready";

    @Nullable
    public static File drawOverImage(@NonNull File background, @NonNull Draw draw) {
        BufferedImage backgroundImage;
        try {
            backgroundImage = ImageIO.read(background);
        } catch (IOException e) {
            logger.error(String.format("Can't read file %s", background), e);
            return null;
        }
        BufferedImage foregroundImage;
        try {
            foregroundImage = ImageIO.read(draw.foreground);
        } catch (IOException e) {
            logger.error(String.format("Can't read file %s", draw.foreground.getPath()), e);
            return null;
        }
        Graphics graphics = backgroundImage.getGraphics();
        switch (draw.position) {
            case CENTER_CROP:
                drawCenterCrop(graphics, backgroundImage.getWidth(), backgroundImage.getHeight(), foregroundImage);
                break;
            case BOTTOM:
                drawBottom(graphics, backgroundImage.getWidth(), backgroundImage.getHeight(), foregroundImage);
                break;
            default:
                logger.error("Unknown draw type {}", draw.position);
                graphics.dispose();
                return null;
        }
        Files.createDirectory(new File(READY));
        File file = new File(READY, String.format("%d.jpg", System.currentTimeMillis()));
        try {
            ImageIO.write(backgroundImage, "jpg", file);
        } catch (IOException e) {
            logger.error(String.format("Can't save image to %s", file.getPath()), e);
        }
        graphics.dispose();
        return file;
    }

    private static void drawCenterCrop(@NonNull Graphics graphics, int width, int height, @NonNull BufferedImage foregroundImage) {
        float xScale = (float) width / foregroundImage.getWidth();
        float yScale = (float) height / foregroundImage.getHeight();
        float scale = Math.max(xScale, yScale);

        float scaledWidth = scale * foregroundImage.getWidth();
        float scaledHeight = scale * foregroundImage.getHeight();

        float left = (width - scaledWidth) / 2;
        float top = (height - scaledHeight) / 2;

        graphics.drawImage(foregroundImage, (int) left, (int) top, (int) scaledWidth, (int) scaledHeight, null);
    }

    private static void drawBottom(@NonNull Graphics graphics, int width, int height, @NonNull BufferedImage foregroundImage) {
        float scale = (float) width / foregroundImage.getWidth();

        float scaledWidth = scale * foregroundImage.getWidth();
        float scaledHeight = scale * foregroundImage.getHeight();

        float left = (width - scaledWidth) / 2;
        float top = height - scaledHeight;

        graphics.drawImage(foregroundImage, (int) left, (int) top, (int) scaledWidth, (int) scaledHeight, null);
    }

}
