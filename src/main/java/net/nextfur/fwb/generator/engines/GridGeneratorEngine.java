package net.nextfur.fwb.generator.engines;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.nextfur.fwb.data.CityPlot;
import net.nextfur.fwb.data.PlayerSelectionData;
import net.nextfur.fwb.generator.BlockPlacementQueue;
import net.nextfur.fwb.generator.GenerationFlags;
import net.nextfur.fwb.generator.GenerationType;
import net.nextfur.fwb.generator.TemplatePlacementHelper;
import net.nextfur.fwb.style.BlockPalette;
import net.nextfur.fwb.style.ModuleCategory;
import net.nextfur.fwb.style.StyleDefinition;
import net.nextfur.fwb.style.StyleManager;
import net.nextfur.fwb.style.StyleModuleEntry;
import net.nextfur.fwb.util.SelectionService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class GridGeneratorEngine {

    private static final BlockPalette DEFAULT_ROAD_PALETTE = new BlockPalette(
            "path_blocks",
            Map.of(
                    ResourceLocation.withDefaultNamespace("stone_bricks"), 0.55,
                    ResourceLocation.withDefaultNamespace("cobblestone"), 0.25,
                    ResourceLocation.withDefaultNamespace("andesite"), 0.20
            )
    );

    public enum RoadTier {
        SMALL,
        BIG
    }

    private record Rect2D(int minX, int minZ, int maxX, int maxZ) {
        int width() {
            return maxX - minX + 1;
        }

        int length() {
            return maxZ - minZ + 1;
        }
    }

    private record StreetSeam(
            int minX, int minZ, int maxX, int maxZ,
            boolean horizontal,
            RoadTier tier,
            int buildingDistance
    ) {
    }

    private record IntersectionBox(
            int minX, int minZ, int maxX, int maxZ,
            RoadTier tier,
            int setback
    ) {
        boolean contains(int x, int z) {
            return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
        }
    }

    private record RoadSpec(
            int smallWidth,
            int bigWidth,
            int smallSetback,
            int bigSetback,
            Optional<StyleModuleEntry> smallAsset,
            Optional<StyleModuleEntry> bigAsset
    ) {
        int widthFor(RoadTier tier) {
            return tier == RoadTier.BIG ? bigWidth : smallWidth;
        }

        int setbackFor(RoadTier tier) {
            return tier == RoadTier.BIG ? bigSetback : smallSetback;
        }

        Optional<StyleModuleEntry> assetFor(RoadTier tier) {
            return tier == RoadTier.BIG ? bigAsset : smallAsset;
        }
    }

    public static List<BlockPlacementQueue.PlannedBlock> generate(
            ServerPlayer player,
            ServerLevel level,
            PlayerSelectionData selection,
            StyleDefinition style,
            GenerationFlags flags,
            RandomSource random
    ) {
        Optional<BoundingBox> boxOpt = selection.getBoundingBox();
        if (boxOpt.isEmpty()) {
            return List.of();
        }

        BoundingBox box = boxOpt.get();
        int grid = Math.max(3, style.gridSize());

        // 1. Inspect road assets first to determine road widths and corresponding building setbacks
        RoadSpec spec = resolveRoadSpec(style, flags, random);
        int minPlotSize = Math.max(grid * 2, 10);

        List<Rect2D> rawPlots = new ArrayList<>();
        List<StreetSeam> seams = new ArrayList<>();

        // 2. First consider road assets for outer grid borders
        RoadTier borderTier = flags.roadMode() == GenerationFlags.RoadMode.SMALL ? RoadTier.SMALL : RoadTier.BIG;
        int borderRoadWidth = spec.widthFor(borderTier);
        int borderSetback = spec.setbackFor(borderTier);
        int borderMargin = borderRoadWidth + borderSetback;

        Rect2D innerArea;
        if (box.getXSpan() >= minPlotSize + borderMargin * 2 && box.getZSpan() >= minPlotSize + borderMargin * 2) {
            // North border road
            seams.add(new StreetSeam(
                    box.minX(), box.minZ(),
                    box.maxX(), box.minZ() + borderRoadWidth - 1,
                    true, borderTier, borderSetback
            ));
            // South border road
            seams.add(new StreetSeam(
                    box.minX(), box.maxZ() - borderRoadWidth + 1,
                    box.maxX(), box.maxZ(),
                    true, borderTier, borderSetback
            ));
            // West border road
            seams.add(new StreetSeam(
                    box.minX(), box.minZ(),
                    box.minX() + borderRoadWidth - 1, box.maxZ(),
                    false, borderTier, borderSetback
            ));
            // East border road
            seams.add(new StreetSeam(
                    box.maxX() - borderRoadWidth + 1, box.minZ(),
                    box.maxX(), box.maxZ(),
                    false, borderTier, borderSetback
            ));

            innerArea = new Rect2D(
                    box.minX() + borderMargin,
                    box.minZ() + borderMargin,
                    box.maxX() - borderMargin,
                    box.maxZ() - borderMargin
            );
        } else {
            innerArea = new Rect2D(box.minX(), box.minZ(), box.maxX(), box.maxZ());
        }

        // 3. BSP subdivision for internal avenues (BIG) and local streets (SMALL) with setback spacing
        subdivideBsp(innerArea, 0, minPlotSize, grid, spec, flags, random, rawPlots, seams);

        BlockPalette roadPalette = StyleManager.loadPalette(level.getServer(), style.styleName(), "path_blocks")
                .or(() -> StyleManager.loadPalette(level.getServer(), style.styleName(), "painting"))
                .orElse(DEFAULT_ROAD_PALETTE);
        Optional<BlockPalette> paintingPalette = StyleManager.loadPalette(level.getServer(), style.styleName(), "painting");

        boolean snapToGround = flags.resolveSnapToGround(GenerationType.GRID);
        int baseY = box.minY();

        BlockPlacementQueue.Builder builder = new BlockPlacementQueue.Builder();

        // 4. Compute all intersection rectangles where horizontal and vertical seams overlap
        List<IntersectionBox> intersections = new ArrayList<>();
        for (int i = 0; i < seams.size(); i++) {
            StreetSeam a = seams.get(i);
            for (int j = i + 1; j < seams.size(); j++) {
                StreetSeam b = seams.get(j);
                if (a.horizontal == b.horizontal) continue;

                int ixMin = Math.max(a.minX, b.minX);
                int ixMax = Math.min(a.maxX, b.maxX);
                int izMin = Math.max(a.minZ, b.minZ);
                int izMax = Math.min(a.maxZ, b.maxZ);

                if (ixMin <= ixMax && izMin <= izMax) {
                    RoadTier intTier = (a.tier == RoadTier.BIG || b.tier == RoadTier.BIG) ? RoadTier.BIG : RoadTier.SMALL;
                    int intSetback = Math.max(a.buildingDistance, b.buildingDistance);
                    intersections.add(new IntersectionBox(ixMin, izMin, ixMax, izMax, intTier, intSetback));
                }
            }
        }

        // 5. Place streets along all seams (including building-distance sidewalk borders), skipping intersections
        for (StreetSeam seam : seams) {
            placeStreetSeam(
                    level, seam, intersections, box, baseY, snapToGround,
                    style, spec, roadPalette, paintingPalette, flags, random, builder
            );
        }

        // 6. Place intersections and corner signals
        for (IntersectionBox intersection : intersections) {
            placeIntersection(
                    level, intersection, box, baseY, snapToGround,
                    style, roadPalette, paintingPalette, flags, random, builder
            );
        }

        // 7. Save leftover City Plots (already spaced away from roads by road-type buildingDistance)
        int plotHeight = Math.max(box.getYSpan(), style.moduleHeight() * 3);
        List<CityPlot> savedPlots = new ArrayList<>();
        for (Rect2D rect : rawPlots) {
            if (rect.width() >= grid && rect.length() >= grid) {
                int plotMinY = snapToGround
                        ? level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (rect.minX + rect.maxX) / 2, (rect.minZ + rect.maxZ) / 2)
                        : baseY;
                savedPlots.add(new CityPlot(
                        rect.minX,
                        plotMinY,
                        rect.minZ,
                        rect.maxX,
                        plotMinY + plotHeight - 1,
                        rect.maxZ
                ));
            }
        }

        SelectionService.setSelection(player, selection.withSavedCityPlots(savedPlots));
        player.sendSystemMessage(
                Component.translatable("message.furbuild.grid.plots_saved",
                        savedPlots.size(),
                        Component.literal("/generate building " + style.styleName()).withStyle(ChatFormatting.AQUA)
                ).withStyle(ChatFormatting.GOLD)
        );

        return builder.buildList();
    }

    private static RoadSpec resolveRoadSpec(StyleDefinition style, GenerationFlags flags, RandomSource random) {
        Optional<StyleModuleEntry> smallAsset = flags.isExcluded(ModuleCategory.ROAD_SMALL)
                ? Optional.empty()
                : style.pickWeightedModuleWithFallback(random, ModuleCategory.ROAD_SMALL, ModuleCategory.ROAD_SEGMENT);
        Optional<StyleModuleEntry> bigAsset = flags.isExcluded(ModuleCategory.ROAD_BIG)
                ? Optional.empty()
                : style.pickWeightedModuleWithFallback(random, ModuleCategory.ROAD_BIG, ModuleCategory.ROAD_SEGMENT);

        int defaultSmallWidth = smallAsset.map(m -> Math.max(3, m.size().getX())).orElse(3);
        int defaultBigWidth = bigAsset.map(m -> Math.max(3, m.size().getX())).orElse(7);

        int smallWidth = flags.width().isPresent() ? Math.max(3, flags.width().getAsInt()) : defaultSmallWidth;
        int bigWidth = flags.width().isPresent()
                ? Math.max(smallWidth, flags.width().getAsInt() + (flags.roadMode() == GenerationFlags.RoadMode.AUTO ? 2 : 0))
                : Math.max(defaultBigWidth, smallWidth + 2);

        if (flags.roadMode() == GenerationFlags.RoadMode.SMALL && flags.width().isEmpty()) {
            bigWidth = smallWidth;
        } else if (flags.roadMode() == GenerationFlags.RoadMode.BIG && flags.width().isEmpty()) {
            smallWidth = bigWidth;
        }

        int smallSetback = flags.buildingDistance().isPresent()
                ? flags.buildingDistance().getAsInt()
                : Math.max(1, style.smallRoadDistance());
        int bigSetback = flags.buildingDistance().isPresent()
                ? flags.buildingDistance().getAsInt()
                : Math.max(smallSetback, style.bigRoadDistance());

        return new RoadSpec(smallWidth, bigWidth, smallSetback, bigSetback, smallAsset, bigAsset);
    }

    private static RoadTier chooseSplitTier(Rect2D rect, int depth, int minPlotSize, GenerationFlags flags) {
        if (flags.roadMode() == GenerationFlags.RoadMode.SMALL) return RoadTier.SMALL;
        if (flags.roadMode() == GenerationFlags.RoadMode.BIG) return RoadTier.BIG;
        if (depth == 0) return RoadTier.BIG;
        if (depth == 1 && rect.width() >= minPlotSize * 4 && rect.length() >= minPlotSize * 4) {
            return RoadTier.BIG;
        }
        return RoadTier.SMALL;
    }

    private static void subdivideBsp(
            Rect2D rect,
            int depth,
            int minPlotSize,
            int gridSize,
            RoadSpec spec,
            GenerationFlags flags,
            RandomSource random,
            List<Rect2D> outPlots,
            List<StreetSeam> outSeams
    ) {
        RoadTier tier = chooseSplitTier(rect, depth, minPlotSize, flags);
        int streetWidth = spec.widthFor(tier);
        int setback = spec.setbackFor(tier);
        int totalSeamBand = streetWidth + setback * 2;

        boolean canSplitX = rect.width() >= (minPlotSize * 2 + totalSeamBand);
        boolean canSplitZ = rect.length() >= (minPlotSize * 2 + totalSeamBand);

        if (!canSplitX && !canSplitZ && tier == RoadTier.BIG && flags.roadMode() == GenerationFlags.RoadMode.AUTO) {
            tier = RoadTier.SMALL;
            streetWidth = spec.widthFor(tier);
            setback = spec.setbackFor(tier);
            totalSeamBand = streetWidth + setback * 2;
            canSplitX = rect.width() >= (minPlotSize * 2 + totalSeamBand);
            canSplitZ = rect.length() >= (minPlotSize * 2 + totalSeamBand);
        }

        if (!canSplitX && !canSplitZ) {
            outPlots.add(rect);
            return;
        }

        boolean splitX;
        if (canSplitX && canSplitZ) {
            if (rect.width() > rect.length() * 1.35) {
                splitX = true;
            } else if (rect.length() > rect.width() * 1.35) {
                splitX = false;
            } else {
                splitX = random.nextBoolean();
            }
        } else {
            splitX = canSplitX;
        }

        if (splitX) {
            int minSplit = rect.minX + minPlotSize + setback;
            int maxSplit = rect.maxX - minPlotSize - streetWidth - setback + 1;
            int splitXPos = minSplit + (maxSplit > minSplit ? random.nextInt(maxSplit - minSplit + 1) : 0);
            int snappedOffset = Math.max(minPlotSize + setback, ((splitXPos - rect.minX) / gridSize) * gridSize);
            if (rect.minX + snappedOffset + streetWidth + setback + minPlotSize - 1 <= rect.maxX) {
                splitXPos = rect.minX + snappedOffset;
            }

            Rect2D left = new Rect2D(rect.minX, rect.minZ, splitXPos - 1 - setback, rect.maxZ);
            Rect2D right = new Rect2D(splitXPos + streetWidth + setback, rect.minZ, rect.maxX, rect.maxZ);
            outSeams.add(new StreetSeam(splitXPos, rect.minZ, splitXPos + streetWidth - 1, rect.maxZ, false, tier, setback));

            subdivideBsp(left, depth + 1, minPlotSize, gridSize, spec, flags, random, outPlots, outSeams);
            subdivideBsp(right, depth + 1, minPlotSize, gridSize, spec, flags, random, outPlots, outSeams);
        } else {
            int minSplit = rect.minZ + minPlotSize + setback;
            int maxSplit = rect.maxZ - minPlotSize - streetWidth - setback + 1;
            int splitZPos = minSplit + (maxSplit > minSplit ? random.nextInt(maxSplit - minSplit + 1) : 0);
            int snappedOffset = Math.max(minPlotSize + setback, ((splitZPos - rect.minZ) / gridSize) * gridSize);
            if (rect.minZ + snappedOffset + streetWidth + setback + minPlotSize - 1 <= rect.maxZ) {
                splitZPos = rect.minZ + snappedOffset;
            }

            Rect2D top = new Rect2D(rect.minX, rect.minZ, rect.maxX, splitZPos - 1 - setback);
            Rect2D bottom = new Rect2D(rect.minX, splitZPos + streetWidth + setback, rect.maxX, rect.maxZ);
            outSeams.add(new StreetSeam(rect.minX, splitZPos, rect.maxX, splitZPos + streetWidth - 1, true, tier, setback));

            subdivideBsp(top, depth + 1, minPlotSize, gridSize, spec, flags, random, outPlots, outSeams);
            subdivideBsp(bottom, depth + 1, minPlotSize, gridSize, spec, flags, random, outPlots, outSeams);
        }
    }

    private static void placeStreetSeam(
            ServerLevel level,
            StreetSeam seam,
            List<IntersectionBox> intersections,
            BoundingBox outerBox,
            int baseY,
            boolean snapToGround,
            StyleDefinition style,
            RoadSpec spec,
            BlockPalette roadPalette,
            Optional<BlockPalette> paintingPalette,
            GenerationFlags flags,
            RandomSource random,
            BlockPlacementQueue.Builder builder
    ) {
        // 1. Pave sidewalk / building-distance border strips on both sides of the road
        if (seam.buildingDistance > 0) {
            paveRoadBorder(level, seam, outerBox, baseY, snapToGround, paintingPalette, roadPalette, random, builder);
        }

        // 2. Place road module asset if available for this road tier
        Optional<StyleModuleEntry> roadSegmentOpt = spec.assetFor(seam.tier);
        if (roadSegmentOpt.isPresent()) {
            StyleModuleEntry seg = roadSegmentOpt.get();
            int modLength = Math.max(1, seg.size().getZ());
            int modWidth = Math.max(1, seg.size().getX());

            if (seam.horizontal) {
                int seamWidth = seam.maxZ - seam.minZ + 1;
                int zOffset = Math.max(0, (seamWidth - modWidth) / 2);
                for (int x = seam.minX; x <= seam.maxX; x += modLength) {
                    int midX = Math.min(seam.maxX, x + modLength / 2);
                    int midZ = seam.minZ + seamWidth / 2;
                    if (isInsideAnyIntersection(intersections, midX, midZ)) {
                        continue;
                    }
                    int y = snapToGround
                            ? level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, midZ) - 1
                            : baseY;
                    // For CLOCKWISE_90, local +Z maps to -X, so shift origin X by +(modLength - 1)
                    BlockPos origin = new BlockPos(x + modLength - 1, y, seam.minZ + zOffset);
                    TemplatePlacementHelper.placeModuleIntoBuilder(
                            level, style.styleName(), seg,
                            origin, Rotation.CLOCKWISE_90, Mirror.NONE, paintingPalette, random, builder
                    );
                }
            } else {
                int seamWidth = seam.maxX - seam.minX + 1;
                int xOffset = Math.max(0, (seamWidth - modWidth) / 2);
                for (int z = seam.minZ; z <= seam.maxZ; z += modLength) {
                    int midX = seam.minX + seamWidth / 2;
                    int midZ = Math.min(seam.maxZ, z + modLength / 2);
                    if (isInsideAnyIntersection(intersections, midX, midZ)) {
                        continue;
                    }
                    int y = snapToGround
                            ? level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, midX, z) - 1
                            : baseY;
                    BlockPos origin = new BlockPos(seam.minX + xOffset, y, z);
                    TemplatePlacementHelper.placeModuleIntoBuilder(
                            level, style.styleName(), seg,
                            origin, Rotation.NONE, Mirror.NONE, paintingPalette, random, builder
                    );
                }
            }
        } else {
            // Procedural road pavement tailored to RoadTier (SMALL vs BIG)
            for (int x = seam.minX; x <= seam.maxX; x++) {
                for (int z = seam.minZ; z <= seam.maxZ; z++) {
                    if (isInsideAnyIntersection(intersections, x, z)) {
                        continue;
                    }
                    int y = snapToGround
                            ? level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1
                            : baseY;
                    boolean isCurb = seam.horizontal
                            ? (z == seam.minZ || z == seam.maxZ)
                            : (x == seam.minX || x == seam.maxX);
                    int coordAlong = seam.horizontal ? x : z;
                    int coordAcross = seam.horizontal ? (z - seam.minZ) : (x - seam.minX);
                    int seamSpan = seam.horizontal ? (seam.maxZ - seam.minZ + 1) : (seam.maxX - seam.minX + 1);
                    boolean isCenterLine = seam.tier == RoadTier.BIG && seamSpan >= 5
                            && coordAcross == seamSpan / 2 && (Math.floorMod(coordAlong, 4) < 2);

                    BlockState state;
                    if (isCurb) {
                        state = Blocks.STONE_BRICKS.defaultBlockState();
                    } else if (isCenterLine) {
                        state = Blocks.YELLOW_CONCRETE.defaultBlockState();
                    } else {
                        state = roadPalette.sample(random, Blocks.COBBLESTONE.defaultBlockState());
                    }
                    builder.setBlock(new BlockPos(x, y, z), state);
                }
            }
        }

        // 3. Place roadside ornaments & props in the border zone at regular intervals
        placeRoadsideDetails(level, seam, intersections, outerBox, baseY, snapToGround, style, paintingPalette, flags, random, builder);
    }

    private static void paveRoadBorder(
            ServerLevel level,
            StreetSeam seam,
            BoundingBox outerBox,
            int baseY,
            boolean snapToGround,
            Optional<BlockPalette> paintingPalette,
            BlockPalette roadPalette,
            RandomSource random,
            BlockPlacementQueue.Builder builder
    ) {
        int dist = seam.buildingDistance;
        BlockPalette sidewalkPalette = paintingPalette.orElse(roadPalette);
        if (seam.horizontal) {
            for (int x = seam.minX; x <= seam.maxX; x++) {
                for (int d = 1; d <= dist; d++) {
                    paveSingleBorderTile(level, x, seam.minZ - d, outerBox, baseY, snapToGround, d == 1, sidewalkPalette, random, builder);
                    paveSingleBorderTile(level, x, seam.maxZ + d, outerBox, baseY, snapToGround, d == 1, sidewalkPalette, random, builder);
                }
            }
        } else {
            for (int z = seam.minZ; z <= seam.maxZ; z++) {
                for (int d = 1; d <= dist; d++) {
                    paveSingleBorderTile(level, seam.minX - d, z, outerBox, baseY, snapToGround, d == 1, sidewalkPalette, random, builder);
                    paveSingleBorderTile(level, seam.maxX + d, z, outerBox, baseY, snapToGround, d == 1, sidewalkPalette, random, builder);
                }
            }
        }
    }

    private static void paveSingleBorderTile(
            ServerLevel level,
            int x, int z,
            BoundingBox outerBox,
            int baseY,
            boolean snapToGround,
            boolean innerEdge,
            BlockPalette sidewalkPalette,
            RandomSource random,
            BlockPlacementQueue.Builder builder
    ) {
        if (x < outerBox.minX() || x > outerBox.maxX() || z < outerBox.minZ() || z > outerBox.maxZ()) {
            return;
        }
        int y = snapToGround
                ? level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1
                : baseY;
        BlockState state = innerEdge
                ? Blocks.SMOOTH_STONE_SLAB.defaultBlockState()
                : sidewalkPalette.sample(random, Blocks.SMOOTH_STONE.defaultBlockState());
        if (innerEdge) {
            state = Blocks.SMOOTH_STONE.defaultBlockState();
        }
        builder.setBlock(new BlockPos(x, y, z), state);
    }

    private static void placeRoadsideDetails(
            ServerLevel level,
            StreetSeam seam,
            List<IntersectionBox> intersections,
            BoundingBox outerBox,
            int baseY,
            boolean snapToGround,
            StyleDefinition style,
            Optional<BlockPalette> paintingPalette,
            GenerationFlags flags,
            RandomSource random,
            BlockPlacementQueue.Builder builder
    ) {
        boolean allowOrnaments = !flags.isExcluded(ModuleCategory.ROAD_ORNAMENT) && !flags.isExcluded(ModuleCategory.ORNAMENT);
        boolean allowProps = !flags.isExcluded(ModuleCategory.ROAD_PROP);
        if (!allowOrnaments && !allowProps) {
            return;
        }

        int spacing = Math.max(8, style.gridSize() * 2);
        int start = seam.horizontal ? seam.minX + 3 : seam.minZ + 3;
        int end = seam.horizontal ? seam.maxX - 3 : seam.maxZ - 3;

        for (int coord = start; coord <= end; coord += spacing) {
            boolean placeOnPositiveSide = ((coord / spacing) % 2 == 0);
            int ox;
            int oz;
            Rotation rot;
            if (seam.horizontal) {
                ox = coord;
                oz = placeOnPositiveSide ? seam.maxZ + 1 : seam.minZ - 1;
                rot = placeOnPositiveSide ? Rotation.NONE : Rotation.CLOCKWISE_180;
            } else {
                ox = placeOnPositiveSide ? seam.maxX + 1 : seam.minX - 1;
                oz = coord;
                rot = placeOnPositiveSide ? Rotation.COUNTERCLOCKWISE_90 : Rotation.CLOCKWISE_90;
            }

            if (ox < outerBox.minX() || ox > outerBox.maxX() || oz < outerBox.minZ() || oz > outerBox.maxZ()) {
                continue;
            }
            if (isNearAnyIntersection(intersections, ox, oz, 2)) {
                continue;
            }

            int oy = snapToGround
                    ? level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ox, oz)
                    : baseY + 1;
            BlockPos detailPos = new BlockPos(ox, oy, oz);

            boolean placeProp = allowProps && (!allowOrnaments || random.nextDouble() < 0.45);
            if (placeProp) {
                Optional<StyleModuleEntry> propMod = style.pickWeightedModule(ModuleCategory.ROAD_PROP, random);
                if (propMod.isPresent()) {
                    TemplatePlacementHelper.placeModuleIntoBuilder(
                            level, style.styleName(), propMod.get(),
                            detailPos, rot, Mirror.NONE, paintingPalette, random, builder
                    );
                } else {
                    // Procedural roadside bench/barrel prop
                    builder.setBlock(detailPos, Blocks.OAK_STAIRS.defaultBlockState());
                }
            } else if (allowOrnaments) {
                Optional<StyleModuleEntry> ornMod = style.pickWeightedModuleWithFallback(
                        random, ModuleCategory.ROAD_ORNAMENT, ModuleCategory.ORNAMENT
                );
                if (ornMod.isPresent()) {
                    TemplatePlacementHelper.placeModuleIntoBuilder(
                            level, style.styleName(), ornMod.get(),
                            detailPos, rot, Mirror.NONE, paintingPalette, random, builder
                    );
                } else {
                    // Procedural street lamppost
                    builder.setBlock(detailPos, Blocks.STONE_BRICK_WALL.defaultBlockState());
                    builder.setBlock(detailPos.above(1), Blocks.OAK_FENCE.defaultBlockState());
                    builder.setBlock(detailPos.above(2), Blocks.OAK_FENCE.defaultBlockState());
                    builder.setBlock(detailPos.above(3), Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false));
                }
            }
        }
    }

    private static void placeIntersection(
            ServerLevel level,
            IntersectionBox box,
            BoundingBox outerBox,
            int baseY,
            boolean snapToGround,
            StyleDefinition style,
            BlockPalette roadPalette,
            Optional<BlockPalette> paintingPalette,
            GenerationFlags flags,
            RandomSource random,
            BlockPlacementQueue.Builder builder
    ) {
        Optional<StyleModuleEntry> intersectionOpt;
        if (box.tier == RoadTier.BIG && !flags.isExcluded(ModuleCategory.INTERSECTION_BIG)) {
            intersectionOpt = style.pickWeightedModuleWithFallback(
                    random, ModuleCategory.INTERSECTION_BIG, ModuleCategory.INTERSECTION
            );
        } else if (!flags.isExcluded(ModuleCategory.INTERSECTION)) {
            intersectionOpt = style.pickWeightedModule(ModuleCategory.INTERSECTION, random);
        } else {
            intersectionOpt = Optional.empty();
        }

        int midX = (box.minX + box.maxX) / 2;
        int midZ = (box.minZ + box.maxZ) / 2;
        int y = snapToGround
                ? level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, midX, midZ) - 1
                : baseY;

        if (intersectionOpt.isPresent()) {
            StyleModuleEntry entry = intersectionOpt.get();
            int modW = Math.max(1, entry.size().getX());
            int modL = Math.max(1, entry.size().getZ());
            int boxW = box.maxX - box.minX + 1;
            int boxL = box.maxZ - box.minZ + 1;
            // Base fill if intersection is larger than module
            if (boxW > modW || boxL > modL) {
                fillProceduralIntersection(level, box, baseY, snapToGround, roadPalette, random, builder);
            }
            int originX = box.minX + Math.max(0, (boxW - modW) / 2);
            int originZ = box.minZ + Math.max(0, (boxL - modL) / 2);
            TemplatePlacementHelper.placeModuleIntoBuilder(
                    level, style.styleName(), entry,
                    new BlockPos(originX, y, originZ),
                    Rotation.NONE, Mirror.NONE, paintingPalette, random, builder
            );
        } else {
            fillProceduralIntersection(level, box, baseY, snapToGround, roadPalette, random, builder);
        }

        // Place road signals at intersection corners inside the setback margin
        if (!flags.isExcluded(ModuleCategory.ROAD_SIGNAL)) {
            int[][] corners = {
                    {box.minX - 1, box.minZ - 1, 0},
                    {box.maxX + 1, box.minZ - 1, 1},
                    {box.maxX + 1, box.maxZ + 1, 2},
                    {box.minX - 1, box.maxZ + 1, 3}
            };
            Optional<StyleModuleEntry> signalMod = style.pickWeightedModule(ModuleCategory.ROAD_SIGNAL, random);
            for (int[] c : corners) {
                int sx = c[0];
                int sz = c[1];
                if (sx < outerBox.minX() || sx > outerBox.maxX() || sz < outerBox.minZ() || sz > outerBox.maxZ()) {
                    continue;
                }
                int sy = snapToGround
                        ? level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, sx, sz)
                        : baseY + 1;
                BlockPos sigPos = new BlockPos(sx, sy, sz);
                if (signalMod.isPresent()) {
                    Rotation rot = Rotation.values()[c[2] % Rotation.values().length];
                    TemplatePlacementHelper.placeModuleIntoBuilder(
                            level, style.styleName(), signalMod.get(),
                            sigPos, rot, Mirror.NONE, paintingPalette, random, builder
                    );
                } else {
                    // Procedural road signal pole
                    builder.setBlock(sigPos, Blocks.IRON_BARS.defaultBlockState());
                    builder.setBlock(sigPos.above(1), Blocks.IRON_BARS.defaultBlockState());
                    builder.setBlock(sigPos.above(2), Blocks.IRON_BARS.defaultBlockState());
                    builder.setBlock(sigPos.above(3), box.tier == RoadTier.BIG
                            ? Blocks.REDSTONE_LAMP.defaultBlockState()
                            : Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false));
                }
            }
        }
    }

    private static void fillProceduralIntersection(
            ServerLevel level,
            IntersectionBox box,
            int baseY,
            boolean snapToGround,
            BlockPalette roadPalette,
            RandomSource random,
            BlockPlacementQueue.Builder builder
    ) {
        int midX = (box.minX + box.maxX) / 2;
        int midZ = (box.minZ + box.maxZ) / 2;
        for (int x = box.minX; x <= box.maxX; x++) {
            for (int z = box.minZ; z <= box.maxZ; z++) {
                int py = snapToGround
                        ? level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1
                        : baseY;
                boolean isCrosswalk = (x == box.minX || x == box.maxX) && (Math.abs(z - midZ) % 2 == 0)
                        || (z == box.minZ || z == box.maxZ) && (Math.abs(x - midX) % 2 == 0);
                BlockState state;
                if (x == midX && z == midZ) {
                    state = Blocks.CHISELED_STONE_BRICKS.defaultBlockState();
                } else if (box.tier == RoadTier.BIG && isCrosswalk) {
                    state = Blocks.WHITE_CONCRETE.defaultBlockState();
                } else {
                    state = roadPalette.sample(random, Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
                }
                builder.setBlock(new BlockPos(x, py, z), state);
            }
        }
    }

    private static boolean isInsideAnyIntersection(List<IntersectionBox> intersections, int x, int z) {
        for (IntersectionBox box : intersections) {
            if (box.contains(x, z)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isNearAnyIntersection(List<IntersectionBox> intersections, int x, int z, int pad) {
        for (IntersectionBox box : intersections) {
            if (x >= box.minX - pad && x <= box.maxX + pad && z >= box.minZ - pad && z <= box.maxZ + pad) {
                return true;
            }
        }
        return false;
    }
}
