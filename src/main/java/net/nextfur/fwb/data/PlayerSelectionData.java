package net.nextfur.fwb.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.nextfur.fwb.style.ModuleCategory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public record PlayerSelectionData(
        SelectionMode mode,
        Optional<BlockPos> posA,
        Optional<BlockPos> posB,
        List<BlockPos> points,
        List<CityPlot> savedCityPlots,
        Optional<BlockPos> selectPosA,
        Optional<BlockPos> selectPosB,
        ModuleCategory activeSelectCategory,
        String activeMappingStyle,
        String activePlaceType,
        List<CityPlot> mappedObjects
) {
    public static final PlayerSelectionData DEFAULT = new PlayerSelectionData(
            SelectionMode.CUBOID,
            Optional.empty(),
            Optional.empty(),
            List.of(),
            List.of(),
            Optional.empty(),
            Optional.empty(),
            ModuleCategory.WALL,
            "",
            "",
            List.of()
    );

    private static final StreamCodec<ByteBuf, Optional<BlockPos>> OPTIONAL_POS_CODEC =
            ByteBufCodecs.optional(BlockPos.STREAM_CODEC);
    private static final StreamCodec<ByteBuf, List<BlockPos>> POS_LIST_CODEC =
            BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list());
    private static final StreamCodec<ByteBuf, List<CityPlot>> PLOT_LIST_CODEC =
            CityPlot.STREAM_CODEC.apply(ByteBufCodecs.list());

    public static final Codec<PlayerSelectionData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            SelectionMode.CODEC.optionalFieldOf("mode", SelectionMode.CUBOID).forGetter(PlayerSelectionData::mode),
            BlockPos.CODEC.optionalFieldOf("posA").forGetter(PlayerSelectionData::posA),
            BlockPos.CODEC.optionalFieldOf("posB").forGetter(PlayerSelectionData::posB),
            BlockPos.CODEC.listOf().optionalFieldOf("points", List.of()).forGetter(PlayerSelectionData::points),
            CityPlot.CODEC.listOf().optionalFieldOf("savedCityPlots", List.of()).forGetter(PlayerSelectionData::savedCityPlots),
            BlockPos.CODEC.optionalFieldOf("selectPosA").forGetter(PlayerSelectionData::selectPosA),
            BlockPos.CODEC.optionalFieldOf("selectPosB").forGetter(PlayerSelectionData::selectPosB),
            ModuleCategory.CODEC.optionalFieldOf("activeSelectCategory", ModuleCategory.WALL).forGetter(PlayerSelectionData::activeSelectCategory),
            Codec.STRING.optionalFieldOf("activeMappingStyle", "").forGetter(PlayerSelectionData::activeMappingStyle),
            Codec.STRING.optionalFieldOf("activePlaceType", "").forGetter(PlayerSelectionData::activePlaceType),
            CityPlot.CODEC.listOf().optionalFieldOf("mappedObjects", List.of()).forGetter(PlayerSelectionData::mappedObjects)
    ).apply(instance, PlayerSelectionData::new));

    public static final StreamCodec<ByteBuf, PlayerSelectionData> STREAM_CODEC = StreamCodec.of(
            PlayerSelectionData::encode,
            PlayerSelectionData::decode
    );

    private static void encode(ByteBuf buf, PlayerSelectionData data) {
        SelectionMode.STREAM_CODEC.encode(buf, data.mode());
        OPTIONAL_POS_CODEC.encode(buf, data.posA());
        OPTIONAL_POS_CODEC.encode(buf, data.posB());
        POS_LIST_CODEC.encode(buf, data.points());
        PLOT_LIST_CODEC.encode(buf, data.savedCityPlots());
        OPTIONAL_POS_CODEC.encode(buf, data.selectPosA());
        OPTIONAL_POS_CODEC.encode(buf, data.selectPosB());
        ModuleCategory.STREAM_CODEC.encode(buf, data.activeSelectCategory());
        ByteBufCodecs.STRING_UTF8.encode(buf, data.activeMappingStyle());
        ByteBufCodecs.STRING_UTF8.encode(buf, data.activePlaceType());
        PLOT_LIST_CODEC.encode(buf, data.mappedObjects());
    }

    private static PlayerSelectionData decode(ByteBuf buf) {
        SelectionMode mode = SelectionMode.STREAM_CODEC.decode(buf);
        Optional<BlockPos> posA = OPTIONAL_POS_CODEC.decode(buf);
        Optional<BlockPos> posB = OPTIONAL_POS_CODEC.decode(buf);
        List<BlockPos> points = POS_LIST_CODEC.decode(buf);
        List<CityPlot> savedPlots = PLOT_LIST_CODEC.decode(buf);
        Optional<BlockPos> selectPosA = OPTIONAL_POS_CODEC.decode(buf);
        Optional<BlockPos> selectPosB = OPTIONAL_POS_CODEC.decode(buf);
        ModuleCategory activeCategory = ModuleCategory.STREAM_CODEC.decode(buf);
        String activeStyle = ByteBufCodecs.STRING_UTF8.decode(buf);
        String activePlaceType = ByteBufCodecs.STRING_UTF8.decode(buf);
        List<CityPlot> mappedObjects = PLOT_LIST_CODEC.decode(buf);
        return new PlayerSelectionData(
                mode, posA, posB, points, savedPlots,
                selectPosA, selectPosB, activeCategory, activeStyle, activePlaceType, mappedObjects
        );
    }

    public PlayerSelectionData withMode(SelectionMode newMode) {
        return new PlayerSelectionData(
                newMode, posA, posB, points, savedCityPlots,
                selectPosA, selectPosB, activeSelectCategory, activeMappingStyle, activePlaceType, mappedObjects
        );
    }

    public PlayerSelectionData withPosA(BlockPos pos) {
        return new PlayerSelectionData(
                mode, Optional.ofNullable(pos), posB, points, savedCityPlots,
                selectPosA, selectPosB, activeSelectCategory, activeMappingStyle, activePlaceType, mappedObjects
        );
    }

    public PlayerSelectionData withPosB(BlockPos pos) {
        return new PlayerSelectionData(
                mode, posA, Optional.ofNullable(pos), points, savedCityPlots,
                selectPosA, selectPosB, activeSelectCategory, activeMappingStyle, activePlaceType, mappedObjects
        );
    }

    public PlayerSelectionData withResetPoint1(BlockPos pos) {
        List<BlockPos> newPoints = new ArrayList<>();
        if (pos != null) {
            newPoints.add(pos);
        }
        return new PlayerSelectionData(
                mode, Optional.ofNullable(pos), Optional.empty(), Collections.unmodifiableList(newPoints), savedCityPlots,
                selectPosA, selectPosB, activeSelectCategory, activeMappingStyle, activePlaceType, mappedObjects
        );
    }

    public PlayerSelectionData withAppendedPoint(BlockPos pos) {
        List<BlockPos> newPoints = new ArrayList<>(points);
        newPoints.add(pos);
        Optional<BlockPos> first = newPoints.isEmpty() ? posA : Optional.of(newPoints.get(0));
        return new PlayerSelectionData(
                mode, first, Optional.of(pos), Collections.unmodifiableList(newPoints), savedCityPlots,
                selectPosA, selectPosB, activeSelectCategory, activeMappingStyle, activePlaceType, mappedObjects
        );
    }

    public PlayerSelectionData withSavedCityPlots(List<CityPlot> plots) {
        return new PlayerSelectionData(
                mode, posA, posB, points, List.copyOf(plots),
                selectPosA, selectPosB, activeSelectCategory, activeMappingStyle, activePlaceType, mappedObjects
        );
    }

    public PlayerSelectionData withSelectPosA(BlockPos pos) {
        return new PlayerSelectionData(
                mode, posA, posB, points, savedCityPlots,
                Optional.ofNullable(pos), selectPosB, activeSelectCategory, activeMappingStyle, activePlaceType, mappedObjects
        );
    }

    public PlayerSelectionData withSelectPosB(BlockPos pos) {
        return new PlayerSelectionData(
                mode, posA, posB, points, savedCityPlots,
                selectPosA, Optional.ofNullable(pos), activeSelectCategory, activeMappingStyle, activePlaceType, mappedObjects
        );
    }

    public PlayerSelectionData withActiveSelectCategory(ModuleCategory category) {
        return new PlayerSelectionData(
                mode, posA, posB, points, savedCityPlots,
                selectPosA, selectPosB, category != null ? category : ModuleCategory.WALL,
                activeMappingStyle, activePlaceType, mappedObjects
        );
    }

    public PlayerSelectionData withMappingSession(String styleName, String placeType) {
        return new PlayerSelectionData(
                mode, posA, posB, points, savedCityPlots,
                selectPosA, selectPosB, activeSelectCategory,
                styleName != null ? styleName : "",
                placeType != null ? placeType : "",
                List.of()
        );
    }

    public PlayerSelectionData withAddedMappedObject(BoundingBox objectBox) {
        List<CityPlot> updated = new ArrayList<>(mappedObjects);
        updated.add(CityPlot.fromBoundingBox(objectBox));
        return new PlayerSelectionData(
                mode, posA, posB, points, savedCityPlots,
                selectPosA, selectPosB, activeSelectCategory, activeMappingStyle, activePlaceType,
                Collections.unmodifiableList(updated)
        );
    }

    public PlayerSelectionData clearMappingSelection() {
        return new PlayerSelectionData(
                mode, posA, posB, points, savedCityPlots,
                Optional.empty(), Optional.empty(), activeSelectCategory, "", "", List.of()
        );
    }

    public PlayerSelectionData clearSelection() {
        return new PlayerSelectionData(
                mode, Optional.empty(), Optional.empty(), List.of(), List.of(),
                Optional.empty(), Optional.empty(), activeSelectCategory, "", "", List.of()
        );
    }

    public boolean hasCuboidSelection() {
        return posA.isPresent() && posB.isPresent();
    }

    public boolean hasSelectObjectSelection() {
        return selectPosA.isPresent() && selectPosB.isPresent();
    }

    public Optional<BoundingBox> getSelectObjectBoundingBox() {
        if (selectPosA.isPresent() && selectPosB.isPresent()) {
            return Optional.of(BoundingBox.fromCorners(selectPosA.get(), selectPosB.get()));
        }
        return Optional.empty();
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
