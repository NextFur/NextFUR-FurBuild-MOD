package net.nextfur.fwb.generator;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public enum GenerationType {
    PATH("path"),
    GRID("grid"),
    BUILDING("building"),
    INTERIOR("interior"),
    DUNGEON("dungeon");

    private final String id;

    GenerationType(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public static GenerationType byName(String name) {
        if (name == null) return null;
        String lower = name.toLowerCase(Locale.ROOT);
        for (GenerationType type : values()) {
            if (type.id.equals(lower) || type.name().toLowerCase(Locale.ROOT).equals(lower)) {
                return type;
            }
        }
        return null;
    }

    public static List<String> allNames() {
        return Arrays.stream(values()).map(GenerationType::getId).toList();
    }
}
