package ru.ok.newyear.newyear.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;

import java.io.*;

public class Properties {

    private static final Logger logger = LoggerFactory.getLogger(Properties.class);

    private static final String UPDATE_MARKER = "updateMarker";
    private static final String BOT_PROPERTIES = "bot.properties";

    public static void setUpdateMarker(@Nullable Long marker) {
        logger.info("Set update marker {}", marker);
        java.util.Properties properties = new java.util.Properties();
        if (marker == null) {
            properties.setProperty(UPDATE_MARKER, null);
        } else {
            properties.setProperty(UPDATE_MARKER, String.valueOf(marker));
        }
        try (OutputStream output = new FileOutputStream(BOT_PROPERTIES)) {
            properties.store(output, null);
        } catch (IOException e) {
            logger.error("Can't write to file", e);
        }
    }

    @Nullable
    public static Long getUpdateMarker() {
        logger.info("Get update marker");
        File file = new File(BOT_PROPERTIES);
        if (!file.exists()) {
            return null;
        }
        String value = null;
        try (InputStream input = new FileInputStream(BOT_PROPERTIES)) {
            java.util.Properties properties = new java.util.Properties();
            properties.load(input);
            value = properties.getProperty(UPDATE_MARKER);
        } catch (IOException e) {
            logger.error("Can't read from file", e);
        }
        if (value == null) {
            logger.info("No value. Return 0");
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            logger.error(String.format("Can't parse %s to long", value), e);
        }
        return null;
    }
}
