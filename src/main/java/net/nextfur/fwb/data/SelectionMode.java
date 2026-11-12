package net.nextfur.fwb.data;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;

public enum SelectionMode implements StringRepresentable {
    CUBOID("cuboid"),
    POLYGON("polygon"),
    CYLINDER("cylinder"),
    SPLINE("spline");

    public static final Codec<SelectionMode> CODEC = StringRepresentable.fromEnum(SelectionMode::values);

    public static final StreamCodec<ByteBuf, SelectionMode> STREAM_CODEC = ByteBufCodecs.INT.map(
            ord -> {
                SelectionMode[] values = values();
                return values[Math.floorMod(ord, values.length)];
            },
            Enum::ordinal
    );

    private final String serializedName;

    SelectionMode(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }

    public SelectionMode next(int delta) {
        SelectionMode[] values = values();
        int step = delta >= 0 ? 1 : -1;
        int nextIndex = Math.floorMod(this.ordinal() + step, values.length);
        return values[nextIndex];
    }

    public static SelectionMode byName(String name) {
        if (name == null) return null;
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.equals("circle")) return CYLINDER;
        if (lower.equals("polyline")) return SPLINE;
        for (SelectionMode mode : values()) {
            if (mode.serializedName.equals(lower) || mode.name().toLowerCase(Locale.ROOT).equals(lower)) {
                return mode;
            }
        }
        return null;
    }
}
