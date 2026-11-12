package net.nextfur.fwb.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

public record CityPlot(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    public static final Codec<CityPlot> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("minX").forGetter(CityPlot::minX),
            Codec.INT.fieldOf("minY").forGetter(CityPlot::minY),
            Codec.INT.fieldOf("minZ").forGetter(CityPlot::minZ),
            Codec.INT.fieldOf("maxX").forGetter(CityPlot::maxX),
            Codec.INT.fieldOf("maxY").forGetter(CityPlot::maxY),
            Codec.INT.fieldOf("maxZ").forGetter(CityPlot::maxZ)
    ).apply(instance, CityPlot::new));

    public static final StreamCodec<ByteBuf, CityPlot> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, CityPlot::minX,
            ByteBufCodecs.VAR_INT, CityPlot::minY,
            ByteBufCodecs.VAR_INT, CityPlot::minZ,
            ByteBufCodecs.VAR_INT, CityPlot::maxX,
            ByteBufCodecs.VAR_INT, CityPlot::maxY,
            ByteBufCodecs.VAR_INT, CityPlot::maxZ,
            CityPlot::new
    );

    public BoundingBox toBoundingBox() {
        return new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
    }

    public static CityPlot fromBoundingBox(BoundingBox box) {
        return new CityPlot(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ());
    }

    public BlockPos minPos() {
        return new BlockPos(minX, minY, minZ);
    }

    public BlockPos maxPos() {
        return new BlockPos(maxX, maxY, maxZ);
    }

    public int widthX() {
        return maxX - minX + 1;
    }

    public int heightY() {
        return maxY - minY + 1;
    }

    public int lengthZ() {
        return maxZ - minZ + 1;
    }
}
