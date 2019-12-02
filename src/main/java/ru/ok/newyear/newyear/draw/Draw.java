package ru.ok.newyear.newyear.draw;

import org.springframework.lang.NonNull;

import java.io.File;

public enum Draw {

    SNOW(new File("foreground/snow.png"), Position.CENTER_CROP);

    public final File foreground;
    public final Position position;

    Draw(@NonNull File foreground, @NonNull Position position) {
        this.foreground = foreground;
        this.position = position;
    }

}
