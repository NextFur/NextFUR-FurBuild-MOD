package net.nextfur.fwb.style;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public enum ModuleCategory implements StringRepresentable {
    WALL("wall"),
    CORNER("corner"),
    WINDOW("window"),
    WINDOW_ORNAMENT("window_ornament"),
    DOOR("door"),
    ROOF("roof"),
    BALCONY("balcony"),
    PILLAR("pillar"),
    TRIM("trim"),
    CHIMNEY("chimney"),
    BUILDING("building"),
    ORNAMENT("ornament"),
    INTERIOR_PROP("interior_prop"),
    INTERIOR_WALL("interior_wall"),
    INTERIOR_CENTER("interior_center"),
    INTERIOR_CEILING("interior_ceiling"),
    INTERIOR_CORNER("interior_corner"),
    DUNGEON_ROOM("dungeon_room"),
    DUNGEON_CORRIDOR("dungeon_corridor"),
    ROAD_SEGMENT("road_segment"),
    ROAD_SMALL("road_small"),
    ROAD_BIG("road_big"),
    INTERSECTION("intersection"),
    INTERSECTION_BIG("intersection_big"),
    ROAD_SIGNAL("road_signal"),
    ROAD_ORNAMENT("road_ornament"),
    ROAD_PROP("road_prop");

    public static final Codec<ModuleCategory> CODEC = StringRepresentable.fromEnum(ModuleCategory::values);

    public static final StreamCodec<ByteBuf, ModuleCategory> STREAM_CODEC = ByteBufCodecs.INT.map(
            ord -> {
                ModuleCategory[] values = values();
                return values[Math.floorMod(ord, values.length)];
            },
            Enum::ordinal
    );

    private final String serializedName;

    ModuleCategory(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }

    public ModuleCategory next(int delta) {
        ModuleCategory[] values = values();
        int step = delta >= 0 ? 1 : -1;
        return values[Math.floorMod(this.ordinal() + step, values.length)];
    }

    public boolean isDirectionalFacade() {
        return this == WALL
                || this == WINDOW
                || this == WINDOW_ORNAMENT
                || this == DOOR
                || this == BALCONY
                || this == INTERIOR_WALL
                || this == PILLAR
                || this == TRIM;
    }

    public static ModuleCategory byName(String name) {
        if (name == null) return null;
        String lower = name.toLowerCase(Locale.ROOT);
        switch (lower) {
            case "small_road", "small_roads", "road_smalls" -> {
                return ROAD_SMALL;
            }
            case "big_road", "big_roads", "road_bigs", "large_road" -> {
                return ROAD_BIG;
            }
            case "big_intersection", "large_intersection" -> {
                return INTERSECTION_BIG;
            }
            case "signal", "signals", "road_signals", "traffic_signal" -> {
                return ROAD_SIGNAL;
            }
            case "window_ornaments", "window_deco" -> {
                return WINDOW_ORNAMENT;
            }
            case "road_ornaments", "street_ornament", "street_ornaments" -> {
                return ROAD_ORNAMENT;
            }
            case "road_props", "street_prop", "street_props" -> {
                return ROAD_PROP;
            }
            case "buildings" -> {
                return BUILDING;
            }
            case "interior_props", "prop", "props" -> {
                return INTERIOR_PROP;
            }
            case "interior_walls", "wall_prop" -> {
                return INTERIOR_WALL;
            }
            case "interior_centers", "center_prop" -> {
                return INTERIOR_CENTER;
            }
            case "interior_ceilings", "ceiling_prop" -> {
                return INTERIOR_CEILING;
            }
            case "interior_corners", "corner_prop" -> {
                return INTERIOR_CORNER;
            }
            default -> {
            }
        }
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
