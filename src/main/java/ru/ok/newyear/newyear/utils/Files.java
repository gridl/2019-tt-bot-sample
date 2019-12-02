package ru.ok.newyear.newyear.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;

import java.io.File;

public class Files {

    private static final Logger logger = LoggerFactory.getLogger(Files.class);

    public static void createDirectory(@NonNull File directory) {
        if (directory.exists()) {
            return;
        }
        try {
            boolean result = directory.mkdir();
            if (result) {
                logger.info("Directory created {}", directory.getPath());
            }
        } catch (SecurityException e) {
            logger.error(String.format("Can't create directory %s", directory.getPath()), e);
        }
    }
}
