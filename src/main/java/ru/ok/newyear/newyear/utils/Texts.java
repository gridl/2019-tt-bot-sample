package ru.ok.newyear.newyear.utils;

import org.springframework.lang.Nullable;

public class Texts {

    public static boolean isEmpty(@Nullable String text) {
        return text == null || text.isEmpty();
    }
}
