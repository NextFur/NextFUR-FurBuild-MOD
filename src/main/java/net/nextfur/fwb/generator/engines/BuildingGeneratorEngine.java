package net.nextfur.fwb.generator.engines;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.nextfur.fwb.data.CityPlot;
import net.nextfur.fwb.data.PlayerSelectionData;
import net.nextfur.fwb.data.SelectionMode;
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

public class BuildingGeneratorEngine {

    private static final BlockPalette DEFAULT_PAINTING_PALETTE = new BlockPalette(
            "painting",
            Map.of(
                    ResourceLocation.withDefaultNamespace("stone_bricks"), 0.60,
                    ResourceLocation.withDefaultNamespace("mossy_stone_bricks"), 0.20,
                    ResourceLocation.withDefaultNamespace("cracked_stone_bricks"), 0.20
            )
    );

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
                int plotFloors = flags.floors().orElseGet(() -> 1 + random.nextInt(3));
                generateCuboidBuilding(
                        level, plotBox, plotFloors, snapToGround,
                        style, Optional.of(paintingPalette), paintingPalette, flags, random, builder
                );
                if (flags.includeInterior()) {
                    InteriorGeneratorEngine.generateIntoBuilder(level, plotBox, style, flags, random, builder);
                }
            }
            // Clear saved plots after filling the city
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
            int floors = flags.floors().orElseGet(() -> Math.max(1, box.getYSpan() / Math.max(3, style.moduleHeight())));
            generateCuboidBuilding(
                    level, box, floors, snapToGround,
                    style, Optional.of(paintingPalette), paintingPalette, flags, random, builder
            );
            if (flags.includeInterior()) {
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

    private static void generateCuboidBuilding(
            ServerLevel level,
            BoundingBox box,
            int floors,
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
        int baseY = snapToGround
                ? level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (box.minX() + box.maxX()) / 2, (box.minZ() + box.maxZ()) / 2)
                : box.minY();

        // Foundation & ground floor slab
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                builder.setBlock(new BlockPos(x, baseY, z), paintingPalette.sample(random, Blocks.STONE_BRICKS.defaultBlockState()));
            }
        }

        // Trace the 4 perimeter walls of the Cuboid using vertices:
        List<BlockPos> corners = List.of(
                new BlockPos(box.minX(), baseY, box.minZ()), // NW
                new BlockPos(box.maxX(), baseY, box.minZ()), // NE
                new BlockPos(box.maxX(), baseY, box.maxZ()), // SE
                new BlockPos(box.minX(), baseY, box.maxZ())  // SW
        );

        tracePolygonPerimeter(
                level, corners, floors, grid, modHeight, baseY,
                style, paintingOpt, paintingPalette, flags, random, builder
        );

        // Roof generation at the top floor
        int roofBaseY = baseY + floors * modHeight;
        if (!flags.isExcluded(ModuleCategory.ROOF)) {
            Optional<StyleModuleEntry> roofEntry = style.pickWeightedModule(ModuleCategory.ROOF, random);
            if (roofEntry.isPresent()) {
                for (int x = box.minX(); x <= box.maxX(); x += grid) {
                    for (int z = box.minZ(); z <= box.maxZ(); z += grid) {
                        TemplatePlacementHelper.placeModuleIntoBuilder(
                                level, style.styleName(), roofEntry.get(),
                                new BlockPos(x, roofBaseY, z),
                                Rotation.NONE, Mirror.NONE, paintingOpt, random, builder
                        );
                    }
                }
            } else {
                // Procedural hipped/stepped roof
                int span = Math.min(box.getXSpan(), box.getZSpan()) / 2 + 1;
                int maxLayers = Math.min(6, Math.max(2, span));
                for (int layer = 0; layer < maxLayers; layer++) {
                    int lx0 = box.minX() - 1 + layer;
                    int lx1 = box.maxX() + 1 - layer;
                    int lz0 = box.minZ() - 1 + layer;
                    int lz1 = box.maxZ() + 1 - layer;
                    if (lx0 > lx1 || lz0 > lz1) break;
                    int ry = roofBaseY + layer;
                    for (int x = lx0; x <= lx1; x++) {
                        for (int z = lz0; z <= lz1; z++) {
                            if (layer == maxLayers - 1 || x == lx0 || x == lx1 || z == lz0 || z == lz1) {
                                builder.setBlock(new BlockPos(x, ry, z), Blocks.DARK_OAK_PLANKS.defaultBlockState());
                            }
                        }
                    }
                }
            }
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
                level, polygonVertices, floors, grid, modHeight, minY,
                style, paintingOpt, paintingPalette, flags, random, builder
        );
    }

    private static void tracePolygonPerimeter(
            ServerLevel level,
            List<BlockPos> vertices,
            int floors,
            int grid,
            int modHeight,
            int baseY,
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

            for (int i = 0; i < n; i++) {
                BlockPos vStart = vertices.get(i);
                BlockPos vEnd = vertices.get((i + 1) % n);

                // Place Corner Pillar at vertex
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

                // Module orientation: modules are built facing NORTH (-Z).
                // Calculate rotation for this edge's outward normal:
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

                    // Decide whether to place a Door (Floor 0), Window (Floors >= 1), or Wall module
                    ModuleCategory chosenCategory = ModuleCategory.WALL;
                    if (f == 0 && !doorPlaced && !flags.isExcluded(ModuleCategory.DOOR) && (s == segments / 2)) {
                        chosenCategory = ModuleCategory.DOOR;
                        doorPlaced = true;
                    } else if (f > 0 && !flags.isExcluded(ModuleCategory.WINDOW) && (s % 2 == 0 || segments == 1)) {
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
                        // Procedural wall segment with door/window and Painting Pass
                        buildProceduralWallSegment(
                                segStartX, segStartZ, segEndX, segEndZ,
                                floorY, modHeight, chosenCategory, outwardFacing,
                                paintingPalette, random, builder
                        );
                    }
                }
            }
        }
    }

    private static void buildProceduralWallSegment(
            int x0, int z0, int x1, int z1,
            int floorY, int modHeight,
            ModuleCategory category,
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
                } else if (step == midStep && category == ModuleCategory.WINDOW && (dy == 1 || dy == 2)) {
                    builder.setBlock(pos, Blocks.GLASS_PANE.defaultBlockState());
                } else if (dy == modHeight - 1) {
                    builder.setBlock(pos, Blocks.SPRUCE_LOG.defaultBlockState());
                } else {
                    // Secondary Painting Pass applied per block
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

        // 1. Voxel Circle Walls & Floors using Painting Palette
        for (int f = 0; f < floors; f++) {
            int fy = minY + f * modHeight;
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    double dist = Math.sqrt(x * x + z * z);
                    if (dist <= radius + 0.45) {
                        // Floor platform
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

            // 2. Punch holes at cardinal directions (North, South, East, West) for Door (Floor 0) and Windows (Floors >= 1)
            Direction[] cardinals = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
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
                } else if (f > 0 && !flags.isExcluded(ModuleCategory.WINDOW)) {
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
                }
            }
        }

        // 3. Procedural Cone Roof topping the Cylinder Tower
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
