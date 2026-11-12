package net.nextfur.fwb.generator.engines;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
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

    private record Rect2D(int minX, int minZ, int maxX, int maxZ) {
        int width() {
            return maxX - minX + 1;
        }

        int length() {
            return maxZ - minZ + 1;
        }
    }

    private record StreetSeam(int minX, int minZ, int maxX, int maxZ, boolean horizontal) {
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
        int streetWidth = Math.max(3, flags.width() > 0 ? flags.width() : grid);
        int minPlotSize = Math.max(grid * 2, 10);

        List<Rect2D> rawPlots = new ArrayList<>();
        List<StreetSeam> seams = new ArrayList<>();

        Rect2D root = new Rect2D(box.minX(), box.minZ(), box.maxX(), box.maxZ());
        subdivideBsp(root, minPlotSize, streetWidth, grid, random, rawPlots, seams);

        BlockPalette roadPalette = StyleManager.loadPalette(level.getServer(), style.styleName(), "path_blocks")
                .or(() -> StyleManager.loadPalette(level.getServer(), style.styleName(), "painting"))
                .orElse(DEFAULT_ROAD_PALETTE);
        Optional<BlockPalette> paintingPalette = StyleManager.loadPalette(level.getServer(), style.styleName(), "painting");

        boolean snapToGround = flags.resolveSnapToGround(GenerationType.GRID);
        int baseY = box.minY();

        BlockPlacementQueue.Builder builder = new BlockPlacementQueue.Builder();

        // 1. Place streets along all BSP seams
        for (StreetSeam seam : seams) {
            placeStreetSeam(level, seam, baseY, snapToGround, style, roadPalette, paintingPalette, flags, random, builder);
        }

        // 2. Place intersections where horizontal and vertical seams overlap
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
                    placeIntersection(level, ixMin, izMin, ixMax, izMax, baseY, snapToGround, style, roadPalette, paintingPalette, flags, random, builder);
                }
            }
        }

        // 3. Save leftover City Plots into PlayerSelectionData for instant `/generate building <style>` synergy!
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

    private static void subdivideBsp(
            Rect2D rect,
            int minPlotSize,
            int streetWidth,
            int gridSize,
            RandomSource random,
            List<Rect2D> outPlots,
            List<StreetSeam> outSeams
    ) {
        boolean canSplitX = rect.width() >= (minPlotSize * 2 + streetWidth);
        boolean canSplitZ = rect.length() >= (minPlotSize * 2 + streetWidth);

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
            int minSplit = rect.minX + minPlotSize;
            int maxSplit = rect.maxX - minPlotSize - streetWidth + 1;
            int splitXPos = minSplit + (maxSplit > minSplit ? random.nextInt(maxSplit - minSplit + 1) : 0);
            // Snap split position to style grid_size relative to rect.minX
            int snappedOffset = Math.max(minPlotSize, ((splitXPos - rect.minX) / gridSize) * gridSize);
            if (rect.minX + snappedOffset + streetWidth + minPlotSize - 1 <= rect.maxX) {
                splitXPos = rect.minX + snappedOffset;
            }

            Rect2D left = new Rect2D(rect.minX, rect.minZ, splitXPos - 1, rect.maxZ);
            Rect2D right = new Rect2D(splitXPos + streetWidth, rect.minZ, rect.maxX, rect.maxZ);
            outSeams.add(new StreetSeam(splitXPos, rect.minZ, splitXPos + streetWidth - 1, rect.maxZ, false));

            subdivideBsp(left, minPlotSize, streetWidth, gridSize, random, outPlots, outSeams);
            subdivideBsp(right, minPlotSize, streetWidth, gridSize, random, outPlots, outSeams);
        } else {
            int minSplit = rect.minZ + minPlotSize;
            int maxSplit = rect.maxZ - minPlotSize - streetWidth + 1;
            int splitZPos = minSplit + (maxSplit > minSplit ? random.nextInt(maxSplit - minSplit + 1) : 0);
            int snappedOffset = Math.max(minPlotSize, ((splitZPos - rect.minZ) / gridSize) * gridSize);
            if (rect.minZ + snappedOffset + streetWidth + minPlotSize - 1 <= rect.maxZ) {
                splitZPos = rect.minZ + snappedOffset;
            }

            Rect2D top = new Rect2D(rect.minX, rect.minZ, rect.maxX, splitZPos - 1);
            Rect2D bottom = new Rect2D(rect.minX, splitZPos + streetWidth, rect.maxX, rect.maxZ);
            outSeams.add(new StreetSeam(rect.minX, splitZPos, rect.maxX, splitZPos + streetWidth - 1, true));

            subdivideBsp(top, minPlotSize, streetWidth, gridSize, random, outPlots, outSeams);
            subdivideBsp(bottom, minPlotSize, streetWidth, gridSize, random, outPlots, outSeams);
        }
    }

    private static void placeStreetSeam(
            ServerLevel level,
            StreetSeam seam,
            int baseY,
            boolean snapToGround,
            StyleDefinition style,
            BlockPalette roadPalette,
            Optional<BlockPalette> paintingPalette,
            GenerationFlags flags,
            RandomSource random,
            BlockPlacementQueue.Builder builder
    ) {
        Optional<StyleModuleEntry> roadSegmentOpt = flags.isExcluded(ModuleCategory.ROAD_SEGMENT)
                ? Optional.empty()
                : style.pickWeightedModule(ModuleCategory.ROAD_SEGMENT, random);

        if (roadSegmentOpt.isPresent()) {
            StyleModuleEntry seg = roadSegmentOpt.get();
            int step = Math.max(1, style.gridSize());
            if (seam.horizontal) {
                for (int x = seam.minX; x <= seam.maxX; x += step) {
                    int y = snapToGround
                            ? level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, seam.minZ) - 1
                            : baseY;
                    TemplatePlacementHelper.placeModuleIntoBuilder(
                            level, style.styleName(), seg,
                            new BlockPos(x, y, seam.minZ),
                            Rotation.CLOCKWISE_90, Mirror.NONE, paintingPalette, random, builder
                    );
                }
            } else {
                for (int z = seam.minZ; z <= seam.maxZ; z += step) {
                    int y = snapToGround
                            ? level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, seam.minX, z) - 1
                            : baseY;
                    TemplatePlacementHelper.placeModuleIntoBuilder(
                            level, style.styleName(), seg,
                            new BlockPos(seam.minX, y, z),
                            Rotation.NONE, Mirror.NONE, paintingPalette, random, builder
                    );
                }
            }
            return;
        }

        // Procedural road pavement using roadPalette with curbstones along borders
        for (int x = seam.minX; x <= seam.maxX; x++) {
            for (int z = seam.minZ; z <= seam.maxZ; z++) {
                int y = snapToGround
                        ? level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1
                        : baseY;
                boolean isCurb = seam.horizontal
                        ? (z == seam.minZ || z == seam.maxZ)
                        : (x == seam.minX || x == seam.maxX);
                BlockState state = isCurb
                        ? Blocks.STONE_BRICKS.defaultBlockState()
                        : roadPalette.sample(random, Blocks.COBBLESTONE.defaultBlockState());
                builder.setBlock(new BlockPos(x, y, z), state);
            }
        }
    }

    private static void placeIntersection(
            ServerLevel level,
            int minX, int minZ, int maxX, int maxZ,
            int baseY,
            boolean snapToGround,
            StyleDefinition style,
            BlockPalette roadPalette,
            Optional<BlockPalette> paintingPalette,
            GenerationFlags flags,
            RandomSource random,
            BlockPlacementQueue.Builder builder
    ) {
        Optional<StyleModuleEntry> intersectionOpt = flags.isExcluded(ModuleCategory.INTERSECTION)
                ? Optional.empty()
                : style.pickWeightedModule(ModuleCategory.INTERSECTION, random);

        int y = snapToGround
                ? level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (minX + maxX) / 2, (minZ + maxZ) / 2) - 1
                : baseY;

        if (intersectionOpt.isPresent()) {
            TemplatePlacementHelper.placeModuleIntoBuilder(
                    level, style.styleName(), intersectionOpt.get(),
                    new BlockPos(minX, y, minZ),
                    Rotation.NONE, Mirror.NONE, paintingPalette, random, builder
            );
            return;
        }

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                int py = snapToGround
                        ? level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1
                        : baseY;
                BlockState state = roadPalette.sample(random, Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
                builder.setBlock(new BlockPos(x, py, z), state);
            }
        }
    }
}
