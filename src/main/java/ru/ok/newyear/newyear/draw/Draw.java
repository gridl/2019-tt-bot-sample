package ru.ok.newyear.newyear.draw;

import org.springframework.lang.NonNull;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public enum Draw {

    SNOW(new File("foreground/snow.png"), Position.CENTER_CROP),
    OLIVIE(new File("foreground/olivie.png"), Position.BOTTOM),
    TABLE(new File("foreground/table.png"), Position.BOTTOM),
    SLIDE(new File("foreground/slide.png"), Position.BOTTOM);

    public final File foreground;
    public final Position position;

    Draw(@NonNull File foreground, @NonNull Position position) {
        this.foreground = foreground;
        this.position = position;
    }

    private static final List<Draw> VALUES = Collections.unmodifiableList(Arrays.asList(values()));
    private static final int SIZE = VALUES.size();
    private static final Random RANDOM = new Random();

    public static Draw random() {
        return VALUES.get(RANDOM.nextInt(SIZE));
    }

}
