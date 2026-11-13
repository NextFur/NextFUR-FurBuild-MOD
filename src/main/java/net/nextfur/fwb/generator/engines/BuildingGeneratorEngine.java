package net.nextfur.fwb.generator.engines;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.nextfur.fwb.data.CityPlot;
import net.nextfur.fwb.data.PlayerSelectionData;
import net.nextfur.fwb.data.SelectionMode;
import net.nextfur.fwb.generator.BlockPlacementQueue;
import net.nextfur.fwb.generator.BuildingType;
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
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public class BuildingGeneratorEngine {

    private static final BlockPalette DEFAULT_PAINTING_PALETTE = new BlockPalette(
            "painting",
            Map.of(
                    ResourceLocation.withDefaultNamespace("stone_bricks"), 0.60,
                    ResourceLocation.withDefaultNamespace("mossy_stone_bricks"), 0.20,
                    ResourceLocation.withDefaultNamespace("cracked_stone_bricks"), 0.20
            )
    );

    private enum StyleTheme {
        MEDIEVAL,
        CYBERPUNK,
        NORDIC,
        DESERT;

        static StyleTheme fromStyleName(String styleName) {
            String lower = styleName == null ? "" : styleName.toLowerCase(Locale.ROOT);
            if (lower.contains("desert") || lower.contains("sand") || lower.contains("adobe")) return DESERT;
            if (lower.contains("cyber") || lower.contains("modern") || lower.contains("urban") || lower.contains("city")) return CYBERPUNK;
            if (lower.contains("nordic") || lower.contains("viking") || lower.contains("pine")) return NORDIC;
            return MEDIEVAL;
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
        BlockPlacementQueue.Builder builder = new BlockPlacementQueue.Builder();
        Optional<BlockPalette> loadedPainting = StyleManager.loadPalette(level.getServer(), style.styleName(), "painting");
        BlockPalette paintingPalette = loadedPainting.orElse(DEFAULT_PAINTING_PALETTE);
        boolean snapToGround = flags.resolveSnapToGround(GenerationType.BUILDING);

        // Bonus Synergy with `/generate grid`: if savedCityPlots exist, populate all saved plots!
        if (!selection.savedCityPlots().isEmpty()) {
            for (CityPlot plot : selection.savedCityPlots()) {
                BoundingBox plotBox = plot.toBoundingBox();
                BuildingType plotType = resolveBuildingType(flags.buildingType(), plotBox, style, random);
                int plotFloors = flags.floors().orElseGet(() -> defaultFloorsForType(plotType, plotBox, style, random));
                generateBuildingForBox(
                        level, plotBox, plotFloors, plotType, snapToGround,
                        style, Optional.of(paintingPalette), paintingPalette, flags, random, builder
                );
                if (flags.includeInterior() && plotType != BuildingType.TEMPLATE) {
                    int totalHeight = plotFloors * Math.max(4, style.moduleHeight());
                    BoundingBox interiorBox = new BoundingBox(
                            plotBox.minX(), plotBox.minY(), plotBox.minZ(),
                            plotBox.maxX(), plotBox.minY() + totalHeight - 1, plotBox.maxZ()
                    );
                    InteriorGeneratorEngine.generateIntoBuilder(level, interiorBox, style, flags, random, builder);
                }
            }
            SelectionService.setSelection(player, selection.withSavedCityPlots(List.of()));
            return builder.buildList();
        }

        if (selection.mode() == SelectionMode.CYLINDER && selection.posA().isPresent() && selection.posB().isPresent()) {
            generateCylinderTower(
                    level,
                    selection.posA().get(),
                    selection.posB().get(),
                    snapToGround,
                    style,
                    Optional.of(paintingPalette),
                    paintingPalette,
                    flags,
                    random,
                    builder
            );
            return builder.buildList();
        }

        if (selection.mode() == SelectionMode.POLYGON && selection.points().size() >= 3) {
            generatePolygonBuilding(
                    level,
                    selection.points(),
                    snapToGround,
                    style,
                    Optional.of(paintingPalette),
                    paintingPalette,
                    flags,
                    random,
                    builder
            );
            if (flags.includeInterior() && selection.getBoundingBox().isPresent()) {
                InteriorGeneratorEngine.generateIntoBuilder(level, selection.getBoundingBox().get(), style, flags, random, builder);
            }
            return builder.buildList();
        }

        Optional<BoundingBox> boxOpt = selection.getBoundingBox();
        if (boxOpt.isPresent()) {
            BoundingBox box = boxOpt.get();
            BuildingType chosenType = resolveBuildingType(flags.buildingType(), box, style, random);
            int floors = flags.floors().orElseGet(() -> {
                int fromBox = Math.max(1, box.getYSpan() / Math.max(4, style.moduleHeight()));
                if (fromBox > 1) return fromBox;
                return defaultFloorsForType(chosenType, box, style, random);
            });
            generateBuildingForBox(
                    level, box, floors, chosenType, snapToGround,
                    style, Optional.of(paintingPalette), paintingPalette, flags, random, builder
            );
            if (flags.includeInterior() && chosenType != BuildingType.TEMPLATE) {
                int totalHeight = floors * Math.max(4, style.moduleHeight());
                BoundingBox interiorBox = new BoundingBox(
                        box.minX(), box.minY(), box.minZ(),
                        box.maxX(), box.minY() + totalHeight - 1, box.maxZ()
                );
                InteriorGeneratorEngine.generateIntoBuilder(level, interiorBox, style, flags, random, builder);
            }
        }

        return builder.buildList();
    }

    private static BuildingType resolveBuildingType(
            BuildingType requested,
            BoundingBox box,
            StyleDefinition style,
            RandomSource random
    ) {
        if (requested != BuildingType.AUTO && requested != BuildingType.STANDARD) {
            return requested;
        }
        int spanX = box.getXSpan();
        int spanZ = box.getZSpan();
        int minSpan = Math.min(spanX, spanZ);
        StyleTheme theme = StyleTheme.fromStyleName(style.styleName());

        List<BuildingType> pool = new ArrayList<>();
        switch (theme) {
            case DESERT -> {
                if (minSpan >= 9) {
                    pool.add(BuildingType.CLUSTER);
                    pool.add(BuildingType.CLUSTER);
                }
                pool.add(BuildingType.HOUSE);
                pool.add(BuildingType.SHOP);
                if (minSpan >= 15) pool.add(BuildingType.COURTYARD);
            }
            case CYBERPUNK -> {
                if (minSpan >= 8) {
                    pool.add(BuildingType.APARTMENT);
                    pool.add(BuildingType.APARTMENT);
                }
                pool.add(BuildingType.SHOP);
                if (minSpan >= 7 && minSpan <= 13) pool.add(BuildingType.TOWER);
            }
            case MEDIEVAL, NORDIC -> {
                if (minSpan >= 9) {
                    pool.add(BuildingType.MANOR);
                    pool.add(BuildingType.MANOR);
                    pool.add(BuildingType.L_SHAPE);
                    pool.add(BuildingType.HALL);
                }
                pool.add(BuildingType.HOUSE);
            }
        }

        if (pool.isEmpty()) {
            pool.add(BuildingType.HOUSE);
        }
        if (!style.getModulesByCategory(ModuleCategory.BUILDING).isEmpty() && random.nextDouble() < 0.15) {
            pool.add(BuildingType.TEMPLATE);
        }
        return pool.get(random.nextInt(pool.size()));
    }

    private static int defaultFloorsForType(
            BuildingType type,
            BoundingBox box,
            StyleDefinition style,
            RandomSource random
    ) {
        return switch (type) {
            case HOUSE -> 2;
            case SHOP -> 2;
            case TOWER -> 4 + random.nextInt(3);
            case HALL -> 2 + random.nextInt(2);
            case COURTYARD -> 2;
            case L_SHAPE -> 2 + random.nextInt(2);
            case MANOR -> 2 + random.nextInt(2);
            case APARTMENT -> 4 + random.nextInt(2);
            case CLUSTER -> 3;
            default -> 2 + random.nextInt(2);
        };
    }

    private static void generateBuildingForBox(
            ServerLevel level,
            BoundingBox box,
            int floors,
            BuildingType type,
            boolean snapToGround,
            StyleDefinition style,
            Optional<BlockPalette> paintingOpt,
            BlockPalette paintingPalette,
            GenerationFlags flags,
            RandomSource random,
            BlockPlacementQueue.Builder builder
    ) {
        int baseY = snapToGround
                ? level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (box.minX() + box.maxX()) / 2, (box.minZ() + box.maxZ()) / 2)
                : box.minY();

        if (type == BuildingType.TEMPLATE && !flags.isExcluded(ModuleCategory.BUILDING)) {
            Optional<StyleModuleEntry> bldMod = style.pickWeightedModule(ModuleCategory.BUILDING, random);
            if (bldMod.isPresent()) {
                Rotation rot = Rotation.values()[random.nextInt(Rotation.values().length)];
                TemplatePlacementHelper.placeModuleIntoBuilder(
                        level, style.styleName(), bldMod.get(),
                        new BlockPos(box.minX(), baseY, box.minZ()),
                        rot, Mirror.NONE, paintingOpt, random, builder
                );
                return;
            }
        }

        if (type == BuildingType.CLUSTER && box.getXSpan() >= 9 && box.getZSpan() >= 9) {
            generateClusterBuilding(level, box, baseY, floors, style, paintingOpt, paintingPalette, flags, random, builder);
            return;
        }

        if (type == BuildingType.MANOR && box.getXSpan() >= 9 && box.getZSpan() >= 9) {
            generateManorBuilding(level, box, baseY, floors, style, paintingOpt, paintingPalette, flags, random, builder);
            return;
        }

        if (type == BuildingType.APARTMENT && box.getXSpan() >= 8 && box.getZSpan() >= 8) {
            generateApartmentBuilding(level, box, baseY, Math.max(3, floors), style, paintingOpt, paintingPalette, flags, random, builder);
            return;
        }

        if (type == BuildingType.L_SHAPE && box.getXSpan() >= 9 && box.getZSpan() >= 9) {
            generateLShapeBuilding(level, box, baseY, floors, style, paintingOpt, paintingPalette, flags, random, builder);
            return;
        }

        if (type == BuildingType.COURTYARD && box.getXSpan() >= 15 && box.getZSpan() >= 15) {
            generateCourtyardBuilding(level, box, baseY, floors, style, paintingOpt, paintingPalette, flags, random, builder);
            return;
        }

        if (type == BuildingType.TOWER) {
            generateSquareTowerBuilding(level, box, baseY, Math.max(3, floors), style, paintingOpt, paintingPalette, flags, random, builder);
            return;
        }

        generateCuboidBuilding(
                level, box, baseY, floors, type,
                style, paintingOpt, paintingPalette, flags, random, builder
        );
    }

    /**
     * Image 4 concept: Multi-volume stepped adobe compound with interlocking wings of varying heights,
     * flat parapet roofs with protruding wooden beam ends, cantilevered mashrabiya bays, cloth awnings, and exterior stairs.
     */
    private static void generateClusterBuilding(
            ServerLevel level,
            BoundingBox box,
            int baseY,
            int floors,
            StyleDefinition style,
            Optional<BlockPalette> paintingOpt,
            BlockPalette paintingPalette,
            GenerationFlags flags,
            RandomSource random,
            BlockPlacementQueue.Builder builder
    ) {
        int grid = Math.max(3, style.gridSize());
        int modHeight = Math.max(4, style.moduleHeight());
        StyleTheme theme = StyleTheme.fromStyleName(style.styleName());

        int splitX = box.minX() + Math.max(5, (box.getXSpan() * 58) / 100);
        int splitZ = box.minZ() + Math.max(5, (box.getZSpan() * 55) / 100);

        // 1. Main central/NW tall keep
        BoundingBox mainKeep = new BoundingBox(box.minX(), baseY, box.minZ(), splitX, baseY, splitZ);
        int mainFloors = Math.max(2, floors);
        buildVolumeBox(level, mainKeep, baseY, mainFloors, grid, modHeight, BuildingType.CLUSTER, style, paintingOpt, paintingPalette, flags, random, builder);
        if (!flags.isExcluded(ModuleCategory.ROOF)) {
            placeAdobeParapetRoof(mainKeep, baseY + mainFloors * modHeight, theme, paintingPalette, random, builder);
        }

        // 2. Medium East wing (stepped down 1 story, overlapping 1 block for clean seam)
        if (box.maxX() - splitX >= 4) {
            BoundingBox eastWing = new BoundingBox(splitX, baseY, box.minZ() + 1, box.maxX(), baseY, Math.min(box.maxZ(), splitZ + 2));
            int wingFloors = Math.max(1, mainFloors - 1);
            buildVolumeBox(level, eastWing, baseY, wingFloors, grid, modHeight, BuildingType.CLUSTER, style, paintingOpt, paintingPalette, flags, random, builder);
            if (!flags.isExcluded(ModuleCategory.ROOF)) {
                placeAdobeParapetRoof(eastWing, baseY + wingFloors * modHeight, theme, paintingPalette, random, builder);
            }
        }

        // 3. Low 1-story South/Front market annex with awning + exterior staircase
        if (box.maxZ() - splitZ >= 4) {
            BoundingBox southAnnex = new BoundingBox(box.minX() + 1, baseY, splitZ, Math.min(box.maxX(), splitX + 1), baseY, box.maxZ());
            buildVolumeBox(level, southAnnex, baseY, 1, grid, modHeight, BuildingType.SHOP, style, paintingOpt, paintingPalette, flags, random, builder);
            if (!flags.isExcluded(ModuleCategory.ROOF)) {
                placeAdobeParapetRoof(southAnnex, baseY + modHeight, theme, paintingPalette, random, builder);
            }
            // Exterior stepped staircase along the east face of the 1-story annex climbing onto its roof terrace
            buildExteriorStaircase(southAnnex.maxX() + 1, baseY, southAnnex.minZ(), modHeight, Direction.SOUTH, theme, builder);
        }

        // Street-level crates, pots & awnings at compound base
        if (!flags.isExcluded(ModuleCategory.ROAD_PROP) && !flags.isExcluded(ModuleCategory.ORNAMENT)) {
            builder.setBlock(new BlockPos(box.minX() + 2, baseY + 1, box.maxZ() + 1), Blocks.DECORATED_POT.defaultBlockState());
            builder.setBlock(new BlockPos(box.minX() + 3, baseY + 1, box.maxZ() + 1), Blocks.BARREL.defaultBlockState());
        }
    }

    /**
     * Image 2 concept: Multi-wing medieval/nordic manor with overhanging gabled pitched roofs,
     * timber-framed gables, dormer accents, and an attached corner tower with a tall spire & red flag mast.
     */
    private static void generateManorBuilding(
            ServerLevel level,
            BoundingBox box,
            int baseY,
            int floors,
            StyleDefinition style,
            Optional<BlockPalette> paintingOpt,
            BlockPalette paintingPalette,
            GenerationFlags flags,
            RandomSource random,
            BlockPlacementQueue.Builder builder
    ) {
        int grid = Math.max(3, style.gridSize());
        int modHeight = Math.max(4, style.moduleHeight());
        StyleTheme theme = StyleTheme.fromStyleName(style.styleName());

        int towerSpan = Math.min(6, Math.max(5, Math.min(box.getXSpan(), box.getZSpan()) / 2));
        int wingSplitX = Math.max(box.minX() + 5, box.maxX() - towerSpan + 1);
        int wingSplitZ = Math.max(box.minZ() + 5, box.maxZ() - 3);

        // 1. Main Hall with full-span overhanging gabled roof
        BoundingBox mainHall = new BoundingBox(box.minX(), baseY, box.minZ(), wingSplitX, baseY, wingSplitZ);
        int hallFloors = Math.max(2, floors);
        buildVolumeBox(level, mainHall, baseY, hallFloors, grid, modHeight, BuildingType.MANOR, style, paintingOpt, paintingPalette, flags, random, builder);
        if (!flags.isExcluded(ModuleCategory.ROOF)) {
            placeGabledRoof(mainHall, baseY + hallFloors * modHeight, theme, paintingPalette, random, builder);
        }

        // 2. Lower front/south entry porch wing (if room)
        if (box.maxZ() - wingSplitZ >= 3) {
            BoundingBox frontWing = new BoundingBox(box.minX() + 1, baseY, wingSplitZ, Math.max(box.minX() + 5, wingSplitX - 1), baseY, box.maxZ());
            int wingFloors = Math.max(1, hallFloors - 1);
            buildVolumeBox(level, frontWing, baseY, wingFloors, grid, modHeight, BuildingType.HOUSE, style, paintingOpt, paintingPalette, flags, random, builder);
            if (!flags.isExcluded(ModuleCategory.ROOF)) {
                placeGabledRoof(frontWing, baseY + wingFloors * modHeight, theme, paintingPalette, random, builder);
            }
        }

        // 3. Attached Corner Spire Tower with red banner/flag mast (Image 2 signature silhouette)
        BoundingBox cornerTower = new BoundingBox(
                Math.max(box.minX(), box.maxX() - towerSpan + 1), baseY, box.minZ(),
                box.maxX(), baseY, Math.min(box.maxZ(), box.minZ() + towerSpan - 1)
        );
        int towerFloors = hallFloors + 1;
        buildVolumeBox(level, cornerTower, baseY, towerFloors, grid, modHeight, BuildingType.TOWER, style, paintingOpt, paintingPalette, flags, random, builder);
        if (!flags.isExcluded(ModuleCategory.ROOF)) {
            placeTowerSpireWithFlag(cornerTower, baseY + towerFloors * modHeight, theme, builder);
        }

        if (!flags.isExcluded(ModuleCategory.CHIMNEY)) {
            placeChimney(level, mainHall.minX() + 2, baseY + hallFloors * modHeight + 2, mainHall.minZ() + 2, style, paintingOpt, random, builder);
        }
    }

    /**
     * Image 3 concept: Notched/recessed-corner multi-story urban block with distinct ground-floor podium,
     * belt-course ledge, paired windows, exterior AC units & vertical neon blade signs, overhanging cornice, and rooftop radio tower.
     */
    private static void generateApartmentBuilding(
            ServerLevel level,
            BoundingBox box,
            int baseY,
            int floors,
            StyleDefinition style,
            Optional<BlockPalette> paintingOpt,
            BlockPalette paintingPalette,
            GenerationFlags flags,
            RandomSource random,
            BlockPlacementQueue.Builder builder
    ) {
        int grid = Math.max(3, style.gridSize());
        int modHeight = Math.max(4, style.moduleHeight());
        StyleTheme theme = StyleTheme.fromStyleName(style.styleName());

        int notchX = box.minX() + Math.max(4, (box.getXSpan() * 55) / 100);
        int notchZ = box.minZ() + Math.max(4, (box.getZSpan() * 55) / 100);

        // Notched L-shaped urban massing (6 vertices) when wide enough, else rectangular
        List<BlockPos> footprint;
        boolean notched = (box.getXSpan() >= 10 && box.getZSpan() >= 10);
        if (notched) {
            footprint = List.of(
                    new BlockPos(box.minX(), baseY, box.minZ()),
                    new BlockPos(box.maxX(), baseY, box.minZ()),
                    new BlockPos(box.maxX(), baseY, notchZ),
                    new BlockPos(notchX, baseY, notchZ),
                    new BlockPos(notchX, baseY, box.maxZ()),
                    new BlockPos(box.minX(), baseY, box.maxZ())
            );
        } else {
            footprint = List.of(
                    new BlockPos(box.minX(), baseY, box.minZ()),
                    new BlockPos(box.maxX(), baseY, box.minZ()),
                    new BlockPos(box.maxX(), baseY, box.maxZ()),
                    new BlockPos(box.minX(), baseY, box.maxZ())
            );
        }

        // Fill ground & floor slabs inside footprint
        for (int f = 0; f < floors; f++) {
            int fy = baseY + f * modHeight;
            for (int x = box.minX(); x <= box.maxX(); x++) {
                for (int z = box.minZ(); z <= box.maxZ(); z++) {
                    if (notched && x > notchX && z > notchZ) continue;
                    BlockState slab = (f == 0)
                            ? Blocks.POLISHED_DEEPSLATE.defaultBlockState()
                            : Blocks.SMOOTH_STONE.defaultBlockState();
                    builder.setBlock(new BlockPos(x, fy, z), slab);
                }
            }
        }

        tracePolygonPerimeter(
                level, footprint, floors, grid, modHeight, baseY, BuildingType.APARTMENT,
                style, paintingOpt, paintingPalette, flags, random, builder
        );

        // Distinct Ground-Floor Belt Course Cornice separating Floor 0 from Upper Floors
        int beltY = baseY + modHeight;
        for (int x = box.minX() - 1; x <= box.maxX() + 1; x++) {
            for (int z = box.minZ() - 1; z <= box.maxZ() + 1; z++) {
                if (notched && x > notchX + 1 && z > notchZ + 1) continue;
                boolean onBeltRim = (x == box.minX() - 1 || x == box.maxX() + 1 || z == box.minZ() - 1 || z == box.maxZ() + 1)
                        || (notched && ((x == notchX + 1 && z >= notchZ + 1) || (z == notchZ + 1 && x >= notchX + 1)));
                if (onBeltRim) {
                    builder.setBlock(new BlockPos(x, beltY, z), Blocks.SMOOTH_STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP));
                }
            }
        }

        // Vertical Neon Blade Signs on exterior corners (Image 3 signature detail)
        if (!flags.isExcluded(ModuleCategory.WINDOW_ORNAMENT) && !flags.isExcluded(ModuleCategory.ORNAMENT)) {
            placeVerticalNeonSign(new BlockPos(box.minX(), baseY + modHeight, box.minZ() - 1), true, builder);
            placeVerticalNeonSign(new BlockPos(box.maxX() + 1, baseY + modHeight * 2, notchZ - 1), false, builder);
        }

        // Overhanging Roof Cornice + Flat Parapet + Tall Lattice Radio Tower Mast (Image 3)
        int roofY = baseY + floors * modHeight;
        if (!flags.isExcluded(ModuleCategory.ROOF)) {
            if (notched) {
                BoundingBox wingA = new BoundingBox(box.minX(), baseY, box.minZ(), box.maxX(), baseY, notchZ);
                BoundingBox wingB = new BoundingBox(box.minX(), baseY, notchZ, notchX, baseY, box.maxZ());
                placeUrbanCorniceAndTowerRoof(wingA, roofY, theme, true, builder);
                placeUrbanCorniceAndTowerRoof(wingB, roofY, theme, false, builder);
            } else {
                placeUrbanCorniceAndTowerRoof(box, roofY, theme, true, builder);
            }
        }
    }

    private static void placeVerticalNeonSign(BlockPos basePos, boolean magenta, BlockPlacementQueue.Builder builder) {
        BlockState pane = magenta
                ? Blocks.MAGENTA_STAINED_GLASS_PANE.defaultBlockState()
                : Blocks.CYAN_STAINED_GLASS_PANE.defaultBlockState();
        BlockState core = magenta
                ? Blocks.PEARLESCENT_FROGLIGHT.defaultBlockState()
                : Blocks.SEA_LANTERN.defaultBlockState();
        for (int dy = 0; dy < 5; dy++) {
            builder.setBlock(basePos.above(dy), (dy % 2 == 0) ? core : pane);
        }
        builder.setBlock(basePos.below(), Blocks.IRON_BARS.defaultBlockState());
        builder.setBlock(basePos.above(5), Blocks.IRON_BARS.defaultBlockState());
    }

    private static void buildVolumeBox(
            ServerLevel level,
            BoundingBox box,
            int baseY,
            int floors,
            int grid,
            int modHeight,
            BuildingType type,
            StyleDefinition style,
            Optional<BlockPalette> paintingOpt,
            BlockPalette paintingPalette,
            GenerationFlags flags,
            RandomSource random,
            BlockPlacementQueue.Builder builder
    ) {
        // Clear interior & build floor slabs for each story
        for (int f = 0; f < floors; f++) {
            int fy = baseY + f * modHeight;
            for (int x = box.minX(); x <= box.maxX(); x++) {
                for (int z = box.minZ(); z <= box.maxZ(); z++) {
                    BlockState floorBlock = (f == 0)
                            ? paintingPalette.sample(random, Blocks.STONE_BRICKS.defaultBlockState())
                            : Blocks.SPRUCE_PLANKS.defaultBlockState();
                    builder.setBlock(new BlockPos(x, fy, z), floorBlock);
                }
            }
        }

        List<BlockPos> corners = List.of(
                new BlockPos(box.minX(), baseY, box.minZ()),
                new BlockPos(box.maxX(), baseY, box.minZ()),
                new BlockPos(box.maxX(), baseY, box.maxZ()),
                new BlockPos(box.minX(), baseY, box.maxZ())
        );

        tracePolygonPerimeter(
                level, corners, floors, grid, modHeight, baseY, type,
                style, paintingOpt, paintingPalette, flags, random, builder
        );
    }

    private static void generateCuboidBuilding(
            ServerLevel level,
            BoundingBox box,
            int baseY,
            int floors,
            BuildingType type,
            StyleDefinition style,
            Optional<BlockPalette> paintingOpt,
            BlockPalette paintingPalette,
            GenerationFlags flags,
            RandomSource random,
            BlockPlacementQueue.Builder builder
    ) {
        int grid = Math.max(3, style.gridSize());
        int modHeight = Math.max(4, style.moduleHeight());

        buildVolumeBox(
                level, box, baseY, floors, grid, modHeight, type,
                style, paintingOpt, paintingPalette, flags, random, builder
        );

        // Full-span architectural roof (never corrugated tiled 5x3x5)
        int roofBaseY = baseY + floors * modHeight;
        if (!flags.isExcluded(ModuleCategory.ROOF)) {
            placeArchitecturalRoof(box, roofBaseY, type, style, paintingPalette, random, builder);
        }

        if ((type == BuildingType.HOUSE || type == BuildingType.STANDARD) && !flags.isExcluded(ModuleCategory.CHIMNEY)) {
            placeChimney(level, box.minX() + 1, roofBaseY + 2, box.minZ() + 1, style, paintingOpt, random, builder);
        }
    }

    private static void generateLShapeBuilding(
            ServerLevel level,
            BoundingBox box,
            int baseY,
            int floors,
            StyleDefinition style,
            Optional<BlockPalette> paintingOpt,
            BlockPalette paintingPalette,
            GenerationFlags flags,
            RandomSource random,
            BlockPlacementQueue.Builder builder
    ) {
        int grid = Math.max(3, style.gridSize());
        int modHeight = Math.max(4, style.moduleHeight());
        int midX = box.minX() + Math.max(grid, (box.getXSpan() / 2));
        int midZ = box.minZ() + Math.max(grid, (box.getZSpan() / 2));

        List<BlockPos> lVertices = List.of(
                new BlockPos(box.minX(), baseY, box.minZ()),
                new BlockPos(box.maxX(), baseY, box.minZ()),
                new BlockPos(box.maxX(), baseY, midZ),
                new BlockPos(midX, baseY, midZ),
                new BlockPos(midX, baseY, box.maxZ()),
                new BlockPos(box.minX(), baseY, box.maxZ())
        );

        for (int f = 0; f < floors; f++) {
            int fy = baseY + f * modHeight;
            for (int x = box.minX(); x <= box.maxX(); x++) {
                for (int z = box.minZ(); z <= box.maxZ(); z++) {
                    if (x > midX && z > midZ) continue;
                    builder.setBlock(new BlockPos(x, fy, z),
                            f == 0 ? paintingPalette.sample(random, Blocks.STONE_BRICKS.defaultBlockState()) : Blocks.SPRUCE_PLANKS.defaultBlockState());
                }
            }
        }

        tracePolygonPerimeter(
                level, lVertices, floors, grid, modHeight, baseY, BuildingType.L_SHAPE,
                style, paintingOpt, paintingPalette, flags, random, builder
        );

        int roofBaseY = baseY + floors * modHeight;
        if (!flags.isExcluded(ModuleCategory.ROOF)) {
            placeArchitecturalRoof(new BoundingBox(box.minX(), baseY, box.minZ(), box.maxX(), baseY, midZ), roofBaseY, BuildingType.L_SHAPE, style, paintingPalette, random, builder);
            placeArchitecturalRoof(new BoundingBox(box.minX(), baseY, midZ, midX, baseY, box.maxZ()), roofBaseY, BuildingType.L_SHAPE, style, paintingPalette, random, builder);
        }

        if (!flags.isExcluded(ModuleCategory.CHIMNEY)) {
            placeChimney(level, box.minX() + 1, roofBaseY + 2, box.minZ() + 1, style, paintingOpt, random, builder);
        }

        if (!flags.isExcluded(ModuleCategory.ORNAMENT) && !flags.isExcluded(ModuleCategory.ROAD_PROP)) {
            int px = (midX + box.maxX()) / 2;
            int pz = (midZ + box.maxZ()) / 2;
            Optional<StyleModuleEntry> patioMod = style.pickWeightedModuleWithFallback(
                    random, ModuleCategory.ROAD_PROP, ModuleCategory.ORNAMENT
            );
            if (patioMod.isPresent()) {
                TemplatePlacementHelper.placeModuleIntoBuilder(
                        level, style.styleName(), patioMod.get(),
                        new BlockPos(px, baseY + 1, pz), Rotation.NONE, Mirror.NONE, paintingOpt, random, builder
                );
            } else {
                builder.setBlock(new BlockPos(px, baseY + 1, pz), Blocks.OAK_FENCE.defaultBlockState());
                builder.setBlock(new BlockPos(px, baseY + 2, pz), Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false));
            }
        }
    }

    private static void generateCourtyardBuilding(
            ServerLevel level,
            BoundingBox box,
            int baseY,
            int floors,
            StyleDefinition style,
            Optional<BlockPalette> paintingOpt,
            BlockPalette paintingPalette,
            GenerationFlags flags,
            RandomSource random,
            BlockPlacementQueue.Builder builder
    ) {
        int grid = Math.max(3, style.gridSize());
        int modHeight = Math.max(4, style.moduleHeight());
        StyleTheme theme = StyleTheme.fromStyleName(style.styleName());
        int inset = Math.max(4, Math.min(box.getXSpan(), box.getZSpan()) / 3);

        BoundingBox innerCourt = new BoundingBox(
                box.minX() + inset, baseY, box.minZ() + inset,
                box.maxX() - inset, baseY, box.maxZ() - inset
        );

        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                boolean insideCourt = (x > innerCourt.minX() && x < innerCourt.maxX() && z > innerCourt.minZ() && z < innerCourt.maxZ());
                if (!insideCourt) {
                    builder.setBlock(new BlockPos(x, baseY, z), paintingPalette.sample(random, Blocks.STONE_BRICKS.defaultBlockState()));
                } else {
                    builder.setBlock(new BlockPos(x, baseY, z), theme == StyleTheme.DESERT
                            ? Blocks.SMOOTH_SANDSTONE.defaultBlockState()
                            : Blocks.GRASS_BLOCK.defaultBlockState());
                }
            }
        }

        List<BlockPos> outerCorners = List.of(
                new BlockPos(box.minX(), baseY, box.minZ()),
                new BlockPos(box.maxX(), baseY, box.minZ()),
                new BlockPos(box.maxX(), baseY, box.maxZ()),
                new BlockPos(box.minX(), baseY, box.maxZ())
        );
        tracePolygonPerimeter(
                level, outerCorners, floors, grid, modHeight, baseY, BuildingType.COURTYARD,
                style, paintingOpt, paintingPalette, flags, random, builder
        );

        List<BlockPos> innerCorners = List.of(
                new BlockPos(innerCourt.minX(), baseY, innerCourt.minZ()),
                new BlockPos(innerCourt.minX(), baseY, innerCourt.maxZ()),
                new BlockPos(innerCourt.maxX(), baseY, innerCourt.maxZ()),
                new BlockPos(innerCourt.maxX(), baseY, innerCourt.minZ())
        );
        tracePolygonPerimeter(
                level, innerCorners, floors, grid, modHeight, baseY, BuildingType.COURTYARD,
                style, paintingOpt, paintingPalette, flags, random, builder
        );

        int cx = (innerCourt.minX() + innerCourt.maxX()) / 2;
        int cz = (innerCourt.minZ() + innerCourt.maxZ()) / 2;
        if (!flags.isExcluded(ModuleCategory.ORNAMENT)) {
            Optional<StyleModuleEntry> centerMod = style.pickWeightedModuleWithFallback(
                    random, ModuleCategory.ORNAMENT, ModuleCategory.ROAD_ORNAMENT
            );
            if (centerMod.isPresent()) {
                TemplatePlacementHelper.placeModuleIntoBuilder(
                        level, style.styleName(), centerMod.get(),
                        new BlockPos(cx, baseY + 1, cz), Rotation.NONE, Mirror.NONE, paintingOpt, random, builder
                );
            } else {
                builder.setBlock(new BlockPos(cx, baseY + 1, cz), Blocks.MOSSY_STONE_BRICKS.defaultBlockState());
                builder.setBlock(new BlockPos(cx, baseY + 2, cz), Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false));
            }
        }

        int roofBaseY = baseY + floors * modHeight;
        if (!flags.isExcluded(ModuleCategory.ROOF)) {
            BlockState roofDeck = theme == StyleTheme.DESERT
                    ? Blocks.SMOOTH_SANDSTONE.defaultBlockState()
                    : Blocks.DARK_OAK_PLANKS.defaultBlockState();
            for (int x = box.minX(); x <= box.maxX(); x++) {
                for (int z = box.minZ(); z <= box.maxZ(); z++) {
                    boolean insideCourt = (x >= innerCourt.minX() && x <= innerCourt.maxX() && z >= innerCourt.minZ() && z <= innerCourt.maxZ());
                    if (!insideCourt) {
                        builder.setBlock(new BlockPos(x, roofBaseY, z), roofDeck);
                        boolean outerRim = (x == box.minX() || x == box.maxX() || z == box.minZ() || z == box.maxZ());
                        if (outerRim) {
                            builder.setBlock(new BlockPos(x, roofBaseY + 1, z),
                                    theme == StyleTheme.DESERT ? Blocks.CUT_SANDSTONE_SLAB.defaultBlockState() : Blocks.STONE_BRICK_WALL.defaultBlockState());
                        }
                    }
                }
            }
        }
    }

    private static void generateSquareTowerBuilding(
            ServerLevel level,
            BoundingBox box,
            int baseY,
            int floors,
            StyleDefinition style,
            Optional<BlockPalette> paintingOpt,
            BlockPalette paintingPalette,
            GenerationFlags flags,
            RandomSource random,
            BlockPlacementQueue.Builder builder
    ) {
        int grid = Math.max(3, style.gridSize());
        int modHeight = Math.max(4, style.moduleHeight());
        StyleTheme theme = StyleTheme.fromStyleName(style.styleName());
        int lowerFloors = Math.max(2, floors - 1);

        buildVolumeBox(level, box, baseY, lowerFloors, grid, modHeight, BuildingType.TOWER, style, paintingOpt, paintingPalette, flags, random, builder);

        BoundingBox topTierBox = (box.getXSpan() >= 7 && box.getZSpan() >= 7)
                ? new BoundingBox(box.minX() + 1, baseY, box.minZ() + 1, box.maxX() - 1, baseY, box.maxZ() - 1)
                : box;
        int topBaseY = baseY + lowerFloors * modHeight;
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                builder.setBlock(new BlockPos(x, topBaseY, z), theme == StyleTheme.DESERT
                        ? Blocks.SANDSTONE_SLAB.defaultBlockState()
                        : Blocks.STONE_BRICK_SLAB.defaultBlockState());
            }
        }
        buildVolumeBox(level, topTierBox, topBaseY, 1, grid, modHeight, BuildingType.TOWER, style, paintingOpt, paintingPalette, flags, random, builder);

        int roofBaseY = topBaseY + modHeight;
        if (!flags.isExcluded(ModuleCategory.ROOF)) {
            if (theme == StyleTheme.DESERT) {
                placeAdobeParapetRoof(topTierBox, roofBaseY, theme, paintingPalette, random, builder);
            } else if (theme == StyleTheme.CYBERPUNK) {
                placeUrbanCorniceAndTowerRoof(topTierBox, roofBaseY, theme, true, builder);
            } else {
                placeTowerSpireWithFlag(topTierBox, roofBaseY, theme, builder);
            }
        }
    }

    // =========================================================================
    // Architectural Roof Systems (Replaces Tiled 5x3x5 Boxes)
    // =========================================================================

    private static void placeArchitecturalRoof(
            BoundingBox box,
            int roofBaseY,
            BuildingType type,
            StyleDefinition style,
            BlockPalette paintingPalette,
            RandomSource random,
            BlockPlacementQueue.Builder builder
    ) {
        StyleTheme theme = StyleTheme.fromStyleName(style.styleName());
        if (theme == StyleTheme.DESERT) {
            placeAdobeParapetRoof(box, roofBaseY, theme, paintingPalette, random, builder);
        } else if (theme == StyleTheme.CYBERPUNK || type == BuildingType.SHOP) {
            placeUrbanCorniceAndTowerRoof(box, roofBaseY, theme, type != BuildingType.SHOP, builder);
        } else {
            placeGabledRoof(box, roofBaseY, theme, paintingPalette, random, builder);
        }
    }

    /**
     * Image 4 Adobe Roof: Flat recessed terrace surrounded by a stepped sandstone parapet wall
     * with protruding dark timber roof beam ends (vigas) along the top exterior frieze.
     */
    private static void placeAdobeParapetRoof(
            BoundingBox box,
            int roofBaseY,
            StyleTheme theme,
            BlockPalette paintingPalette,
            RandomSource random,
            BlockPlacementQueue.Builder builder
    ) {
        BlockState deckBlock = Blocks.SMOOTH_SANDSTONE.defaultBlockState();
        BlockState parapetBlock = Blocks.CUT_SANDSTONE.defaultBlockState();
        BlockState parapetSlab = Blocks.SANDSTONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
        BlockState beamPeg = Blocks.SPRUCE_FENCE.defaultBlockState();

        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                builder.setBlock(new BlockPos(x, roofBaseY, z), deckBlock);
                boolean isBorder = (x == box.minX() || x == box.maxX() || z == box.minZ() || z == box.maxZ());
                boolean isCorner = (x == box.minX() || x == box.maxX()) && (z == box.minZ() || z == box.maxZ());
                if (isBorder) {
                    builder.setBlock(new BlockPos(x, roofBaseY + 1, z), isCorner ? parapetBlock : parapetBlock);
                    if (isCorner || ((x + z) % 2 == 0)) {
                        builder.setBlock(new BlockPos(x, roofBaseY + 2, z), parapetSlab);
                    }
                }
            }
        }

        // Protruding timber viga beam pegs just below the parapet roofline
        for (int x = box.minX() + 1; x <= box.maxX() - 1; x += 2) {
            builder.setBlock(new BlockPos(x, roofBaseY, box.minZ() - 1), beamPeg);
            builder.setBlock(new BlockPos(x, roofBaseY, box.maxZ() + 1), beamPeg);
        }
        for (int z = box.minZ() + 1; z <= box.maxZ() - 1; z += 2) {
            builder.setBlock(new BlockPos(box.minX() - 1, roofBaseY, z), beamPeg);
            builder.setBlock(new BlockPos(box.maxX() + 1, roofBaseY, z), beamPeg);
        }
    }

    /**
     * Image 2 Overhanging Gabled Pitched Roof: Full-span stairs with 1-block eave overhang,
     * framed triangular gable end walls with attic window, eave corbels, and ridge crest.
     */
    private static void placeGabledRoof(
            BoundingBox box,
            int roofBaseY,
            StyleTheme theme,
            BlockPalette paintingPalette,
            RandomSource random,
            BlockPlacementQueue.Builder builder
    ) {
        BlockState roofStair = (theme == StyleTheme.MEDIEVAL)
                ? Blocks.CRIMSON_STAIRS.defaultBlockState()
                : Blocks.DARK_OAK_STAIRS.defaultBlockState();
        BlockState roofSolid = (theme == StyleTheme.MEDIEVAL)
                ? Blocks.CRIMSON_PLANKS.defaultBlockState()
                : Blocks.DARK_OAK_PLANKS.defaultBlockState();
        BlockState trimBeam = Blocks.SPRUCE_LOG.defaultBlockState();
        BlockState gableWall = (theme == StyleTheme.NORDIC)
                ? Blocks.SPRUCE_PLANKS.defaultBlockState()
                : Blocks.WHITE_TERRACOTTA.defaultBlockState();

        // Seal attic floor / ceiling slab
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                builder.setBlock(new BlockPos(x, roofBaseY, z), Blocks.SPRUCE_PLANKS.defaultBlockState());
            }
        }

        // Pitch across the shorter span so gable peaks look balanced
        boolean ridgeAlongX = box.getXSpan() >= box.getZSpan();
        int halfSpan = (ridgeAlongX ? box.getZSpan() : box.getXSpan()) / 2;
        int maxRise = Math.min(7, Math.max(2, halfSpan + 1));

        if (ridgeAlongX) {
            int zMin = box.minZ() - 1;
            int zMax = box.maxZ() + 1;
            int xMin = box.minX() - 1;
            int xMax = box.maxX() + 1;
            for (int layer = 0; layer < maxRise; layer++) {
                int lz0 = zMin + layer;
                int lz1 = zMax - layer;
                if (lz0 > lz1) break;
                int ry = roofBaseY + layer;

                // Fill triangular gable walls at box.minX() and box.maxX()
                for (int z = Math.max(box.minZ(), lz0 + 1); z <= Math.min(box.maxZ(), lz1 - 1); z++) {
                    boolean isAtticWin = (layer == 1 && Math.abs(z - (box.minZ() + box.maxZ()) / 2) == 0);
                    BlockState infill = isAtticWin ? Blocks.GLASS_PANE.defaultBlockState() : gableWall;
                    builder.setBlock(new BlockPos(box.minX(), ry, z), infill);
                    builder.setBlock(new BlockPos(box.maxX(), ry, z), infill);
                }

                // Sloped roof slopes with 1-block overhang
                for (int x = xMin; x <= xMax; x++) {
                    boolean isEaveTrim = (x == xMin || x == xMax);
                    BlockState slopeBlockN = isEaveTrim ? trimBeam : roofStair.setValue(StairBlock.FACING, Direction.SOUTH).setValue(StairBlock.HALF, Half.BOTTOM);
                    BlockState slopeBlockS = isEaveTrim ? trimBeam : roofStair.setValue(StairBlock.FACING, Direction.NORTH).setValue(StairBlock.HALF, Half.BOTTOM);
                    if (lz0 == lz1) {
                        builder.setBlock(new BlockPos(x, ry, lz0), roofSolid);
                        builder.setBlock(new BlockPos(x, ry + 1, lz0), Blocks.DARK_OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
                    } else {
                        builder.setBlock(new BlockPos(x, ry, lz0), slopeBlockN);
                        builder.setBlock(new BlockPos(x, ry, lz1), slopeBlockS);
                        if (lz1 - lz0 == 1) {
                            builder.setBlock(new BlockPos(x, ry + 1, lz0), Blocks.DARK_OAK_SLAB.defaultBlockState());
                            builder.setBlock(new BlockPos(x, ry + 1, lz1), Blocks.DARK_OAK_SLAB.defaultBlockState());
                        }
                    }
                }
            }
        } else {
            int xMin = box.minX() - 1;
            int xMax = box.maxX() + 1;
            int zMin = box.minZ() - 1;
            int zMax = box.maxZ() + 1;
            for (int layer = 0; layer < maxRise; layer++) {
                int lx0 = xMin + layer;
                int lx1 = xMax - layer;
                if (lx0 > lx1) break;
                int ry = roofBaseY + layer;

                for (int x = Math.max(box.minX(), lx0 + 1); x <= Math.min(box.maxX(), lx1 - 1); x++) {
                    boolean isAtticWin = (layer == 1 && Math.abs(x - (box.minX() + box.maxX()) / 2) == 0);
                    BlockState infill = isAtticWin ? Blocks.GLASS_PANE.defaultBlockState() : gableWall;
                    builder.setBlock(new BlockPos(x, ry, box.minZ()), infill);
                    builder.setBlock(new BlockPos(x, ry, box.maxZ()), infill);
                }

                for (int z = zMin; z <= zMax; z++) {
                    boolean isEaveTrim = (z == zMin || z == zMax);
                    BlockState slopeBlockW = isEaveTrim ? trimBeam : roofStair.setValue(StairBlock.FACING, Direction.EAST).setValue(StairBlock.HALF, Half.BOTTOM);
                    BlockState slopeBlockE = isEaveTrim ? trimBeam : roofStair.setValue(StairBlock.FACING, Direction.WEST).setValue(StairBlock.HALF, Half.BOTTOM);
                    if (lx0 == lx1) {
                        builder.setBlock(new BlockPos(lx0, ry, z), roofSolid);
                        builder.setBlock(new BlockPos(lx0, ry + 1, z), Blocks.DARK_OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
                    } else {
                        builder.setBlock(new BlockPos(lx0, ry, z), slopeBlockW);
                        builder.setBlock(new BlockPos(lx1, ry, z), slopeBlockE);
                        if (lx1 - lx0 == 1) {
                            builder.setBlock(new BlockPos(lx0, ry + 1, z), Blocks.DARK_OAK_SLAB.defaultBlockState());
                            builder.setBlock(new BlockPos(lx1, ry + 1, z), Blocks.DARK_OAK_SLAB.defaultBlockState());
                        }
                    }
                }
            }
        }
    }

    /**
     * Image 2 Tower Spire with waving red flag/banner mast on top.
     */
    private static void placeTowerSpireWithFlag(
            BoundingBox box,
            int roofBaseY,
            StyleTheme theme,
            BlockPlacementQueue.Builder builder
    ) {
        BlockState spireBlock = (theme == StyleTheme.NORDIC)
                ? Blocks.DARK_OAK_PLANKS.defaultBlockState()
                : Blocks.CRIMSON_PLANKS.defaultBlockState();
        BlockState trimBlock = Blocks.SPRUCE_LOG.defaultBlockState();

        int maxLayers = Math.max(4, Math.min(box.getXSpan(), box.getZSpan()) + 1);
        int topY = roofBaseY;
        for (int layer = 0; layer < maxLayers; layer++) {
            int inset = layer / 2;
            int lx0 = box.minX() - 1 + inset;
            int lx1 = box.maxX() + 1 - inset;
            int lz0 = box.minZ() - 1 + inset;
            int lz1 = box.maxZ() + 1 - inset;
            if (lx0 > lx1 || lz0 > lz1) break;
            int ry = roofBaseY + layer;
            topY = ry;
            for (int x = lx0; x <= lx1; x++) {
                for (int z = lz0; z <= lz1; z++) {
                    if (layer == 0 || layer == maxLayers - 1 || x == lx0 || x == lx1 || z == lz0 || z == lz1) {
                        builder.setBlock(new BlockPos(x, ry, z), layer == 0 ? trimBlock : spireBlock);
                    }
                }
            }
        }

        // Tall flag mast + waving red banner wool pennant
        int cx = (box.minX() + box.maxX()) / 2;
        int cz = (box.minZ() + box.maxZ()) / 2;
        for (int fy = 1; fy <= 5; fy++) {
            builder.setBlock(new BlockPos(cx, topY + fy, cz), Blocks.SPRUCE_FENCE.defaultBlockState());
        }
        builder.setBlock(new BlockPos(cx + 1, topY + 5, cz), Blocks.RED_WOOL.defaultBlockState());
        builder.setBlock(new BlockPos(cx + 2, topY + 5, cz), Blocks.RED_WOOL.defaultBlockState());
        builder.setBlock(new BlockPos(cx + 1, topY + 4, cz), Blocks.RED_WOOL.defaultBlockState());
        builder.setBlock(new BlockPos(cx + 2, topY + 4, cz), Blocks.RED_CONCRETE.defaultBlockState());
        builder.setBlock(new BlockPos(cx + 3, topY + 4, cz), Blocks.RED_WOOL.defaultBlockState());
    }

    /**
     * Image 3 Urban Roof: Articulated overhanging dentil cornice, recessed flat roof deck,
     * parapet wall, rooftop HVAC unit, and a tall Iron-Bars lattice radio/cell tower mast.
     */
    private static void placeUrbanCorniceAndTowerRoof(
            BoundingBox box,
            int roofBaseY,
            StyleTheme theme,
            boolean includeRadioTower,
            BlockPlacementQueue.Builder builder
    ) {
        BlockState deck = (theme == StyleTheme.CYBERPUNK)
                ? Blocks.POLISHED_DEEPSLATE.defaultBlockState()
                : Blocks.SMOOTH_STONE.defaultBlockState();
        BlockState cornice = (theme == StyleTheme.CYBERPUNK)
                ? Blocks.DEEPSLATE_TILE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP)
                : Blocks.SMOOTH_STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP);
        BlockState parapet = (theme == StyleTheme.CYBERPUNK)
                ? Blocks.POLISHED_DEEPSLATE_WALL.defaultBlockState()
                : Blocks.STONE_BRICK_WALL.defaultBlockState();

        // Recessed roof deck
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                builder.setBlock(new BlockPos(x, roofBaseY, z), deck);
            }
        }

        // Overhanging 1-block cornice & dentils
        for (int x = box.minX() - 1; x <= box.maxX() + 1; x++) {
            for (int z = box.minZ() - 1; z <= box.maxZ() + 1; z++) {
                boolean onOuterRim = (x == box.minX() - 1 || x == box.maxX() + 1 || z == box.minZ() - 1 || z == box.maxZ() + 1);
                boolean onParapet = (x == box.minX() || x == box.maxX() || z == box.minZ() || z == box.maxZ());
                if (onOuterRim) {
                    builder.setBlock(new BlockPos(x, roofBaseY, z), cornice);
                    if ((x + z) % 2 == 0) {
                        builder.setBlock(new BlockPos(x, roofBaseY - 1, z), Blocks.POLISHED_BLACKSTONE_BUTTON.defaultBlockState()
                                .setValue(ButtonBlock.FACE, AttachFace.CEILING));
                    }
                }
                if (onParapet) {
                    builder.setBlock(new BlockPos(x, roofBaseY + 1, z), parapet);
                }
            }
        }

        // Rooftop HVAC mechanical box
        int hx = box.minX() + 2;
        int hz = box.minZ() + 2;
        if (hx + 1 < box.maxX() && hz + 1 < box.maxZ()) {
            builder.setBlock(new BlockPos(hx, roofBaseY + 1, hz), Blocks.IRON_BLOCK.defaultBlockState());
            builder.setBlock(new BlockPos(hx + 1, roofBaseY + 1, hz), Blocks.IRON_BLOCK.defaultBlockState());
            builder.setBlock(new BlockPos(hx, roofBaseY + 2, hz), Blocks.IRON_TRAPDOOR.defaultBlockState());
            builder.setBlock(new BlockPos(hx + 1, roofBaseY + 2, hz), Blocks.DAYLIGHT_DETECTOR.defaultBlockState());
        }

        // Tall Iron-Bars Lattice Radio Tower Mast (Image 3)
        if (includeRadioTower && box.getXSpan() >= 6 && box.getZSpan() >= 6) {
            int tx = (box.minX() + box.maxX()) / 2;
            int tz = (box.minZ() + box.maxZ()) / 2;
            for (int dx = 0; dx <= 1; dx++) {
                for (int dz = 0; dz <= 1; dz++) {
                    for (int ty = 1; ty <= 4; ty++) {
                        builder.setBlock(new BlockPos(tx + dx, roofBaseY + ty, tz + dz), Blocks.IRON_BARS.defaultBlockState());
                    }
                }
            }
            for (int ty = 5; ty <= 7; ty++) {
                builder.setBlock(new BlockPos(tx, roofBaseY + ty, tz), Blocks.IRON_BARS.defaultBlockState());
            }
            builder.setBlock(new BlockPos(tx, roofBaseY + 8, tz), Blocks.REDSTONE_LAMP.defaultBlockState());
        }
    }

    private static void buildExteriorStaircase(
            int startX, int baseY, int startZ, int height,
            Direction climbDir, StyleTheme theme, BlockPlacementQueue.Builder builder
    ) {
        BlockState stairBlock = (theme == StyleTheme.DESERT)
                ? Blocks.SANDSTONE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, climbDir).setValue(StairBlock.HALF, Half.BOTTOM)
                : Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, climbDir).setValue(StairBlock.HALF, Half.BOTTOM);
        BlockState supportBlock = (theme == StyleTheme.DESERT)
                ? Blocks.CUT_SANDSTONE.defaultBlockState()
                : Blocks.STONE_BRICKS.defaultBlockState();

        for (int step = 0; step < height; step++) {
            int sx = startX + climbDir.getStepX() * step;
            int sz = startZ + climbDir.getStepZ() * step;
            int sy = baseY + 1 + step;
            builder.setBlock(new BlockPos(sx, sy, sz), stairBlock);
            for (int under = baseY; under < sy; under++) {
                builder.setBlock(new BlockPos(sx, under, sz), supportBlock);
            }
        }
    }

    private static void placeChimney(
            ServerLevel level,
            int x, int y, int z,
            StyleDefinition style,
            Optional<BlockPalette> paintingOpt,
            RandomSource random,
            BlockPlacementQueue.Builder builder
    ) {
        Optional<StyleModuleEntry> chimneyMod = style.pickWeightedModule(ModuleCategory.CHIMNEY, random);
        if (chimneyMod.isPresent()) {
            TemplatePlacementHelper.placeModuleIntoBuilder(
                    level, style.styleName(), chimneyMod.get(),
                    new BlockPos(x, y, z), Rotation.NONE, Mirror.NONE, paintingOpt, random, builder
            );
        } else {
            BlockPos base = new BlockPos(x, y, z);
            builder.setBlock(base, Blocks.BRICKS.defaultBlockState());
            builder.setBlock(base.above(1), Blocks.BRICKS.defaultBlockState());
            builder.setBlock(base.above(2), Blocks.BRICK_WALL.defaultBlockState());
            builder.setBlock(base.above(3), Blocks.CAMPFIRE.defaultBlockState());
        }
    }

    private static void generatePolygonBuilding(
            ServerLevel level,
            List<BlockPos> polygonVertices,
            boolean snapToGround,
            StyleDefinition style,
            Optional<BlockPalette> paintingOpt,
            BlockPalette paintingPalette,
            GenerationFlags flags,
            RandomSource random,
            BlockPlacementQueue.Builder builder
    ) {
        int grid = Math.max(3, style.gridSize());
        int modHeight = Math.max(4, style.moduleHeight());
        int minY = polygonVertices.stream().mapToInt(BlockPos::getY).min().orElse(64);
        int maxY = polygonVertices.stream().mapToInt(BlockPos::getY).max().orElse(minY + modHeight * 2);

        if (snapToGround) {
            BlockPos first = polygonVertices.get(0);
            minY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, first.getX(), first.getZ());
        }

        int floors = flags.floors().orElse(Math.max(1, (maxY - minY + 1) / modHeight));

        tracePolygonPerimeter(
                level, polygonVertices, floors, grid, modHeight, minY, BuildingType.STANDARD,
                style, paintingOpt, paintingPalette, flags, random, builder
        );
    }

    private static int resolveEffectiveWindowFrequency(
            BuildingType buildingType,
            int floorIdx,
            StyleDefinition style,
            GenerationFlags flags,
            RandomSource random
    ) {
        int configured = flags.windowFrequency().isPresent()
                ? flags.windowFrequency().getAsInt()
                : style.windowFrequency();

        if ((buildingType == BuildingType.SHOP || buildingType == BuildingType.APARTMENT) && floorIdx >= 1) {
            return 1;
        }
        if (buildingType == BuildingType.SHOP && floorIdx == 0) {
            return 1;
        }
        if (configured <= 0) {
            int[] dynamicOptions = {1, 2, 3};
            return dynamicOptions[random.nextInt(dynamicOptions.length)];
        }
        return configured;
    }

    private static boolean shouldPlaceWindowAtSegment(int segIdx, int totalSegments, int floorIdx, int windowFreq, BuildingType type) {
        if (totalSegments == 1) {
            return floorIdx > 0 || type == BuildingType.SHOP;
        }
        if (floorIdx == 0 && (type == BuildingType.TOWER)) {
            return false;
        }
        if (windowFreq <= 1) {
            return true;
        }
        return (segIdx % windowFreq) == 0;
    }

    private static void tracePolygonPerimeter(
            ServerLevel level,
            List<BlockPos> vertices,
            int floors,
            int grid,
            int modHeight,
            int baseY,
            BuildingType buildingType,
            StyleDefinition style,
            Optional<BlockPalette> paintingOpt,
            BlockPalette paintingPalette,
            GenerationFlags flags,
            RandomSource random,
            BlockPlacementQueue.Builder builder
    ) {
        boolean doorPlaced = false;
        int n = vertices.size();

        for (int f = 0; f < floors; f++) {
            int floorY = baseY + f * modHeight;
            int floorWinFreq = resolveEffectiveWindowFrequency(buildingType, f, style, flags, random);

            for (int i = 0; i < n; i++) {
                BlockPos vStart = vertices.get(i);
                BlockPos vEnd = vertices.get((i + 1) % n);

                if (!flags.isExcluded(ModuleCategory.CORNER)) {
                    Optional<StyleModuleEntry> cornerOpt = style.pickWeightedModule(ModuleCategory.CORNER, random);
                    BlockPos cornerOrigin = new BlockPos(vStart.getX(), floorY, vStart.getZ());
                    if (cornerOpt.isPresent()) {
                        Rotation cornerRot = Rotation.values()[i % Rotation.values().length];
                        TemplatePlacementHelper.placeModuleIntoBuilder(
                                level, style.styleName(), cornerOpt.get(),
                                cornerOrigin, cornerRot, Mirror.NONE, paintingOpt, random, builder
                        );
                    } else {
                        for (int dy = 0; dy < modHeight; dy++) {
                            builder.setBlock(cornerOrigin.above(dy), Blocks.SPRUCE_LOG.defaultBlockState());
                        }
                    }
                }

                double dx = vEnd.getX() - vStart.getX();
                double dz = vEnd.getZ() - vStart.getZ();
                double edgeLen = Math.sqrt(dx * dx + dz * dz);
                if (edgeLen < 1.0) continue;

                Rotation edgeRotation = rotationForEdge(dx, dz);
                Direction outwardFacing = facingForEdge(dx, dz);

                int segments = Math.max(1, (int) Math.round(edgeLen / grid));
                for (int s = 0; s < segments; s++) {
                    double t0 = (double) s / segments;
                    double t1 = (double) (s + 1) / segments;
                    int segStartX = (int) Math.round(vStart.getX() + dx * t0);
                    int segStartZ = (int) Math.round(vStart.getZ() + dz * t0);
                    int segEndX = (int) Math.round(vStart.getX() + dx * t1);
                    int segEndZ = (int) Math.round(vStart.getZ() + dz * t1);

                    BlockPos moduleOrigin = new BlockPos(segStartX, floorY, segStartZ);

                    ModuleCategory chosenCategory = ModuleCategory.WALL;
                    if (f == 0 && !doorPlaced && !flags.isExcluded(ModuleCategory.DOOR) && (s == segments / 2)) {
                        chosenCategory = ModuleCategory.DOOR;
                        doorPlaced = true;
                    } else if ((buildingType == BuildingType.HALL || (buildingType == BuildingType.APARTMENT && f == 0))
                            && s % 2 == 1 && !flags.isExcluded(ModuleCategory.PILLAR)
                            && !style.getModulesByCategory(ModuleCategory.PILLAR).isEmpty()) {
                        chosenCategory = ModuleCategory.PILLAR;
                    } else if (!flags.isExcluded(ModuleCategory.WINDOW)
                            && shouldPlaceWindowAtSegment(s, segments, f, floorWinFreq, buildingType)) {
                        chosenCategory = ModuleCategory.WINDOW;
                    }

                    Optional<StyleModuleEntry> moduleOpt = flags.isExcluded(chosenCategory)
                            ? Optional.empty()
                            : style.pickWeightedModule(chosenCategory, random);

                    if (moduleOpt.isEmpty() && chosenCategory != ModuleCategory.WALL && !flags.isExcluded(ModuleCategory.WALL)) {
                        moduleOpt = style.pickWeightedModule(ModuleCategory.WALL, random);
                    }

                    if (moduleOpt.isPresent()) {
                        TemplatePlacementHelper.placeModuleIntoBuilder(
                                level, style.styleName(), moduleOpt.get(),
                                moduleOrigin, edgeRotation, Mirror.NONE, paintingOpt, random, builder
                        );
                    } else if (!flags.isExcluded(ModuleCategory.WALL)) {
                        buildProceduralWallSegment(
                                segStartX, segStartZ, segEndX, segEndZ,
                                floorY, modHeight, chosenCategory, floorWinFreq, outwardFacing,
                                paintingPalette, random, builder
                        );
                    }

                    // Window Ornaments on exterior of WINDOW segments
                    if (chosenCategory == ModuleCategory.WINDOW
                            && !flags.isExcluded(ModuleCategory.WINDOW_ORNAMENT)
                            && !flags.isExcluded(ModuleCategory.ORNAMENT)) {
                        placeWindowOrnament(
                                level, moduleOrigin, segStartX, segStartZ, segEndX, segEndZ,
                                floorY, edgeRotation, outwardFacing, style, paintingOpt, random, builder
                        );
                    }

                    // Occasional Balcony on upper floor windows (excluding urban apartments which use AC/blade signs)
                    if (f > 0 && buildingType != BuildingType.APARTMENT && chosenCategory == ModuleCategory.WINDOW && !flags.isExcluded(ModuleCategory.BALCONY)) {
                        Optional<StyleModuleEntry> balconyMod = style.pickWeightedModule(ModuleCategory.BALCONY, random);
                        if (balconyMod.isPresent() && random.nextDouble() < (buildingType == BuildingType.HALL ? 0.55 : 0.25)) {
                            int depth = Math.max(1, balconyMod.get().size().getZ());
                            TemplatePlacementHelper.placeModuleIntoBuilder(
                                    level, style.styleName(), balconyMod.get(),
                                    moduleOrigin.relative(outwardFacing, depth),
                                    edgeRotation, Mirror.NONE, paintingOpt, random, builder
                            );
                        }
                    }

                    // Inter-floor exterior Trim / Cornice on upper floors
                    if (f > 0 && !flags.isExcluded(ModuleCategory.TRIM)) {
                        Optional<StyleModuleEntry> trimMod = style.pickWeightedModule(ModuleCategory.TRIM, random);
                        if (trimMod.isPresent()) {
                            int depth = Math.max(1, trimMod.get().size().getZ());
                            TemplatePlacementHelper.placeModuleIntoBuilder(
                                    level, style.styleName(), trimMod.get(),
                                    moduleOrigin.relative(outwardFacing, depth),
                                    edgeRotation, Mirror.NONE, paintingOpt, random, builder
                            );
                        }
                    }

                    // Entrance Lanterns/Awnings flanking Doors
                    if (chosenCategory == ModuleCategory.DOOR && !flags.isExcluded(ModuleCategory.ORNAMENT)) {
                        int midX = (segStartX + segEndX) / 2;
                        int midZ = (segStartZ + segEndZ) / 2;
                        BlockPos porchPos = new BlockPos(midX, floorY + 2, midZ).relative(outwardFacing, 1);
                        builder.setBlock(porchPos, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false));
                    }
                }
            }
        }
    }

    private static void placeWindowOrnament(
            ServerLevel level,
            BlockPos moduleOrigin,
            int segStartX, int segStartZ,
            int segEndX, int segEndZ,
            int floorY,
            Rotation edgeRotation,
            Direction outwardFacing,
            StyleDefinition style,
            Optional<BlockPalette> paintingOpt,
            RandomSource random,
            BlockPlacementQueue.Builder builder
    ) {
        Optional<StyleModuleEntry> ornMod = style.pickWeightedModule(ModuleCategory.WINDOW_ORNAMENT, random);
        if (ornMod.isPresent()) {
            StyleModuleEntry entry = ornMod.get();
            int depth = Math.max(1, entry.size().getZ());
            BlockPos ornOrigin = moduleOrigin.relative(outwardFacing, depth);
            TemplatePlacementHelper.placeModuleIntoBuilder(
                    level, style.styleName(), entry,
                    ornOrigin, edgeRotation, Mirror.NONE, paintingOpt, random, builder
            );
            return;
        }

        int midX = (segStartX + segEndX) / 2;
        int midZ = (segStartZ + segEndZ) / 2;
        Direction sideLeft = outwardFacing.getCounterClockWise();
        Direction sideRight = outwardFacing.getClockWise();

        BlockPos windowOut = new BlockPos(midX, floorY + 1, midZ).relative(outwardFacing, 1);
        BlockState sillState = Blocks.SPRUCE_STAIRS.defaultBlockState()
                .setValue(StairBlock.FACING, outwardFacing.getOpposite())
                .setValue(StairBlock.HALF, Half.TOP);
        builder.setBlock(windowOut.below(), sillState);

        BlockState flowerPot = switch (random.nextInt(4)) {
            case 0 -> Blocks.POTTED_POPPY.defaultBlockState();
            case 1 -> Blocks.POTTED_OXEYE_DAISY.defaultBlockState();
            case 2 -> Blocks.POTTED_FERN.defaultBlockState();
            default -> Blocks.POTTED_BLUE_ORCHID.defaultBlockState();
        };
        builder.setBlock(windowOut, flowerPot);

        int span = Math.max(Math.abs(segEndX - segStartX), Math.abs(segEndZ - segStartZ));
        if (span >= 3) {
            BlockState shutter = Blocks.SPRUCE_TRAPDOOR.defaultBlockState()
                    .setValue(TrapDoorBlock.FACING, outwardFacing)
                    .setValue(TrapDoorBlock.OPEN, true);
            builder.setBlock(windowOut.relative(sideLeft, 1), shutter);
            builder.setBlock(windowOut.above().relative(sideLeft, 1), shutter);
            builder.setBlock(windowOut.relative(sideRight, 1), shutter);
            builder.setBlock(windowOut.above().relative(sideRight, 1), shutter);
        }

        BlockState hood = Blocks.SPRUCE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
        builder.setBlock(windowOut.above(2), hood);
    }

    private static void buildProceduralWallSegment(
            int x0, int z0, int x1, int z1,
            int floorY, int modHeight,
            ModuleCategory category,
            int windowFreq,
            Direction outwardFacing,
            BlockPalette paintingPalette,
            RandomSource random,
            BlockPlacementQueue.Builder builder
    ) {
        int steps = Math.max(1, Math.max(Math.abs(x1 - x0), Math.abs(z1 - z0)));
        int midStep = steps / 2;

        for (int step = 0; step <= steps; step++) {
            int wx = (int) Math.round(x0 + (x1 - x0) * ((double) step / steps));
            int wz = (int) Math.round(z0 + (z1 - z0) * ((double) step / steps));

            boolean isCornerEnd = (step == 0 || step == steps);
            boolean isWindowCol = category == ModuleCategory.WINDOW
                    && (step == midStep || (windowFreq == 1 && steps >= 4 && (step == midStep - 1 || step == midStep + 1)));

            for (int dy = 0; dy < modHeight; dy++) {
                BlockPos pos = new BlockPos(wx, floorY + dy, wz);
                if (isCornerEnd) {
                    builder.setBlock(pos, Blocks.SPRUCE_LOG.defaultBlockState());
                    continue;
                }

                if (step == midStep && category == ModuleCategory.DOOR && (dy == 1 || dy == 2)) {
                    BlockState doorState = Blocks.OAK_DOOR.defaultBlockState()
                            .setValue(DoorBlock.FACING, outwardFacing)
                            .setValue(DoorBlock.HALF, dy == 1 ? DoubleBlockHalf.LOWER : DoubleBlockHalf.UPPER);
                    builder.setBlock(pos, doorState);
                } else if (isWindowCol && (dy == 1 || dy == 2)) {
                    builder.setBlock(pos, Blocks.GLASS_PANE.defaultBlockState());
                } else if (dy == modHeight - 1) {
                    builder.setBlock(pos, Blocks.SPRUCE_LOG.defaultBlockState());
                } else {
                    builder.setBlock(pos, paintingPalette.sample(random, Blocks.STONE_BRICKS.defaultBlockState()));
                }
            }
        }
    }

    private static void generateCylinderTower(
            ServerLevel level,
            BlockPos centerPos,
            BlockPos edgePos,
            boolean snapToGround,
            StyleDefinition style,
            Optional<BlockPalette> paintingOpt,
            BlockPalette paintingPalette,
            GenerationFlags flags,
            RandomSource random,
            BlockPlacementQueue.Builder builder
    ) {
        int cx = centerPos.getX();
        int cz = centerPos.getZ();
        int dx = edgePos.getX() - cx;
        int dz = edgePos.getZ() - cz;
        int radius = Math.max(3, (int) Math.round(Math.sqrt(dx * dx + dz * dz)));

        int modHeight = Math.max(4, style.moduleHeight());
        int minY = snapToGround
                ? level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cx, cz)
                : Math.min(centerPos.getY(), edgePos.getY());
        int rawHeight = Math.abs(edgePos.getY() - centerPos.getY()) + 1;
        int floors = flags.floors().orElseGet(() -> Math.max(2, rawHeight / modHeight));
        int towerHeight = floors * modHeight;

        for (int f = 0; f < floors; f++) {
            int fy = minY + f * modHeight;
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    double dist = Math.sqrt(x * x + z * z);
                    if (dist <= radius + 0.45) {
                        builder.setBlock(
                                new BlockPos(cx + x, fy, cz + z),
                                f == 0
                                        ? paintingPalette.sample(random, Blocks.STONE_BRICKS.defaultBlockState())
                                        : Blocks.SPRUCE_PLANKS.defaultBlockState()
                        );
                    }
                    if (dist >= radius - 0.65 && dist <= radius + 0.45) {
                        for (int dy = 1; dy < modHeight; dy++) {
                            builder.setBlock(
                                    new BlockPos(cx + x, fy + dy, cz + z),
                                    paintingPalette.sample(random, Blocks.STONE_BRICKS.defaultBlockState())
                            );
                        }
                    }
                }
            }

            Direction[] cardinals = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
            int winFreq = resolveEffectiveWindowFrequency(BuildingType.TOWER, f, style, flags, random);
            for (int cIdx = 0; cIdx < cardinals.length; cIdx++) {
                Direction dir = cardinals[cIdx];
                BlockPos cardinalPos = new BlockPos(
                        cx + dir.getStepX() * radius,
                        fy + 1,
                        cz + dir.getStepZ() * radius
                );
                Rotation rot = switch (dir) {
                    case NORTH -> Rotation.NONE;
                    case EAST -> Rotation.CLOCKWISE_90;
                    case SOUTH -> Rotation.CLOCKWISE_180;
                    case WEST -> Rotation.COUNTERCLOCKWISE_90;
                    default -> Rotation.NONE;
                };

                if (f == 0 && cIdx == 0 && !flags.isExcluded(ModuleCategory.DOOR)) {
                    Optional<StyleModuleEntry> doorMod = style.pickWeightedModule(ModuleCategory.DOOR, random);
                    if (doorMod.isPresent()) {
                        TemplatePlacementHelper.placeModuleIntoBuilder(
                                level, style.styleName(), doorMod.get(),
                                cardinalPos, rot, Mirror.NONE, paintingOpt, random, builder
                        );
                    } else {
                        builder.setBlock(cardinalPos, Blocks.OAK_DOOR.defaultBlockState()
                                .setValue(DoorBlock.FACING, dir)
                                .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
                        builder.setBlock(cardinalPos.above(), Blocks.OAK_DOOR.defaultBlockState()
                                .setValue(DoorBlock.FACING, dir)
                                .setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
                    }
                } else if (f > 0 && !flags.isExcluded(ModuleCategory.WINDOW) && (winFreq <= 1 || (cIdx % winFreq == 0))) {
                    Optional<StyleModuleEntry> winMod = style.pickWeightedModule(ModuleCategory.WINDOW, random);
                    if (winMod.isPresent()) {
                        TemplatePlacementHelper.placeModuleIntoBuilder(
                                level, style.styleName(), winMod.get(),
                                cardinalPos, rot, Mirror.NONE, paintingOpt, random, builder
                        );
                    } else {
                        builder.setBlock(cardinalPos, Blocks.IRON_BARS.defaultBlockState());
                        builder.setBlock(cardinalPos.above(), Blocks.IRON_BARS.defaultBlockState());
                    }

                    if (!flags.isExcluded(ModuleCategory.WINDOW_ORNAMENT) && !flags.isExcluded(ModuleCategory.ORNAMENT)) {
                        placeWindowOrnament(
                                level, cardinalPos.below(),
                                cardinalPos.getX(), cardinalPos.getZ(),
                                cardinalPos.getX(), cardinalPos.getZ(),
                                fy, rot, dir, style, paintingOpt, random, builder
                        );
                    }
                }
            }
        }

        if (!flags.isExcluded(ModuleCategory.ROOF)) {
            int roofStartY = minY + towerHeight;
            int roofRadius = radius + 1;
            int coneHeight = roofRadius * 2;
            for (int ry = 0; ry <= coneHeight; ry++) {
                double currentR = roofRadius * (1.0 - ((double) ry / (coneHeight + 0.5)));
                int bound = (int) Math.ceil(currentR + 1);
                for (int x = -bound; x <= bound; x++) {
                    for (int z = -bound; z <= bound; z++) {
                        double dist = Math.sqrt(x * x + z * z);
                        if (dist <= currentR + 0.45 && (dist >= currentR - 0.85 || currentR <= 1.2)) {
                            builder.setBlock(
                                    new BlockPos(cx + x, roofStartY + ry, cz + z),
                                    Blocks.DARK_OAK_PLANKS.defaultBlockState()
                            );
                        }
                    }
                }
            }
        }
    }

    private static Rotation rotationForEdge(double dx, double dz) {
        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx >= 0 ? Rotation.NONE : Rotation.CLOCKWISE_180;
        } else {
            return dz >= 0 ? Rotation.CLOCKWISE_90 : Rotation.COUNTERCLOCKWISE_90;
        }
    }

    private static Direction facingForEdge(double dx, double dz) {
        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx >= 0 ? Direction.NORTH : Direction.SOUTH;
        } else {
            return dz >= 0 ? Direction.EAST : Direction.WEST;
        }
    }
}
