package net.nextfur.fwb.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public record PlayerSelectionData(
        SelectionMode mode,
        Optional<BlockPos> posA,
        Optional<BlockPos> posB,
        List<BlockPos> points,
        List<CityPlot> savedCityPlots
) {
    public static final PlayerSelectionData DEFAULT = new PlayerSelectionData(
            SelectionMode.CUBOID,
            Optional.empty(),
            Optional.empty(),
            List.of(),
            List.of()
    );

    public static final Codec<PlayerSelectionData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            SelectionMode.CODEC.optionalFieldOf("mode", SelectionMode.CUBOID).forGetter(PlayerSelectionData::mode),
            BlockPos.CODEC.optionalFieldOf("posA").forGetter(PlayerSelectionData::posA),
            BlockPos.CODEC.optionalFieldOf("posB").forGetter(PlayerSelectionData::posB),
            BlockPos.CODEC.listOf().optionalFieldOf("points", List.of()).forGetter(PlayerSelectionData::points),
            CityPlot.CODEC.listOf().optionalFieldOf("savedCityPlots", List.of()).forGetter(PlayerSelectionData::savedCityPlots)
    ).apply(instance, PlayerSelectionData::new));

    public static final StreamCodec<ByteBuf, PlayerSelectionData> STREAM_CODEC = StreamCodec.composite(
            SelectionMode.STREAM_CODEC, PlayerSelectionData::mode,
            ByteBufCodecs.optional(BlockPos.STREAM_CODEC), PlayerSelectionData::posA,
            ByteBufCodecs.optional(BlockPos.STREAM_CODEC), PlayerSelectionData::posB,
            BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list()), PlayerSelectionData::points,
            CityPlot.STREAM_CODEC.apply(ByteBufCodecs.list()), PlayerSelectionData::savedCityPlots,
            PlayerSelectionData::new
    );

    public PlayerSelectionData withMode(SelectionMode newMode) {
        return new PlayerSelectionData(newMode, posA, posB, points, savedCityPlots);
    }

    public PlayerSelectionData withPosA(BlockPos pos) {
        return new PlayerSelectionData(mode, Optional.ofNullable(pos), posB, points, savedCityPlots);
    }

    public PlayerSelectionData withPosB(BlockPos pos) {
        return new PlayerSelectionData(mode, posA, Optional.ofNullable(pos), points, savedCityPlots);
    }

    public PlayerSelectionData withResetPoint1(BlockPos pos) {
        List<BlockPos> newPoints = new ArrayList<>();
        if (pos != null) {
            newPoints.add(pos);
        }
        return new PlayerSelectionData(mode, Optional.ofNullable(pos), Optional.empty(), Collections.unmodifiableList(newPoints), savedCityPlots);
    }

    public PlayerSelectionData withAppendedPoint(BlockPos pos) {
        List<BlockPos> newPoints = new ArrayList<>(points);
        newPoints.add(pos);
        Optional<BlockPos> first = newPoints.isEmpty() ? posA : Optional.of(newPoints.get(0));
        return new PlayerSelectionData(mode, first, Optional.of(pos), Collections.unmodifiableList(newPoints), savedCityPlots);
    }

    public PlayerSelectionData withSavedCityPlots(List<CityPlot> plots) {
        return new PlayerSelectionData(mode, posA, posB, points, List.copyOf(plots));
    }

    public PlayerSelectionData clearSelection() {
        return new PlayerSelectionData(mode, Optional.empty(), Optional.empty(), List.of(), List.of());
    }

    public boolean hasCuboidSelection() {
        return posA.isPresent() && posB.isPresent();
    }

    public Optional<BoundingBox> getBoundingBox() {
        if (mode == SelectionMode.POLYGON || mode == SelectionMode.SPLINE) {
            if (points.size() >= 2) {
                int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
                int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
                for (BlockPos p : points) {
                    minX = Math.min(minX, p.getX());
                    minY = Math.min(minY, p.getY());
                    minZ = Math.min(minZ, p.getZ());
                    maxX = Math.max(maxX, p.getX());
                    maxY = Math.max(maxY, p.getY());
                    maxZ = Math.max(maxZ, p.getZ());
                }
                return Optional.of(new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ));
            }
        }
        if (posA.isPresent() && posB.isPresent()) {
            BlockPos a = posA.get();
            BlockPos b = posB.get();
            if (mode == SelectionMode.CYLINDER) {
                int dx = b.getX() - a.getX();
                int dz = b.getZ() - a.getZ();
                int radius = Math.max(1, (int) Math.round(Math.sqrt(dx * dx + dz * dz)));
                int minY = Math.min(a.getY(), b.getY());
                int maxY = Math.max(a.getY(), b.getY());
                return Optional.of(new BoundingBox(
                        a.getX() - radius, minY, a.getZ() - radius,
                        a.getX() + radius, maxY, a.getZ() + radius
                ));
            }
            return Optional.of(BoundingBox.fromCorners(a, b));
        }
        return Optional.empty();
    }
}
