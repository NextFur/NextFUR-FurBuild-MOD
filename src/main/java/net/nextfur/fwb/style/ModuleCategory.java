package net.nextfur.fwb.style;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public enum ModuleCategory implements StringRepresentable {
    WALL("wall"),
    CORNER("corner"),
    WINDOW("window"),
    DOOR("door"),
    ROOF("roof"),
    ORNAMENT("ornament"),
    INTERIOR_PROP("interior_prop"),
    DUNGEON_ROOM("dungeon_room"),
    DUNGEON_CORRIDOR("dungeon_corridor"),
    ROAD_SEGMENT("road_segment"),
    INTERSECTION("intersection");

    public static final Codec<ModuleCategory> CODEC = StringRepresentable.fromEnum(ModuleCategory::values);

    private final String serializedName;

    ModuleCategory(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }

    public static ModuleCategory byName(String name) {
        if (name == null) return null;
        String lower = name.toLowerCase(Locale.ROOT);
        for (ModuleCategory category : values()) {
            if (category.serializedName.equals(lower) || category.name().toLowerCase(Locale.ROOT).equals(lower)) {
                return category;
            }
            // Support plural alias in -exclude flags (e.g. ornaments, windows, doors, walls)
            if (lower.endsWith("s") && category.serializedName.equals(lower.substring(0, lower.length() - 1))) {
                return category;
            }
        }
        return null;
    }

    public static List<String> allNames() {
        return Arrays.stream(values()).map(ModuleCategory::getSerializedName).toList();
    }
}
