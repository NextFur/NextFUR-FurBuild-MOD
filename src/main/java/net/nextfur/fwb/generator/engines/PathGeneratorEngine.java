package net.nextfur.fwb.generator.engines;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
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
import net.nextfur.fwb.util.SplineMath;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class PathGeneratorEngine {

    private static final BlockPalette DEFAULT_PATH_PALETTE = new BlockPalette(
            "path_blocks",
            Map.of(
                    ResourceLocation.withDefaultNamespace("cobblestone"), 0.45,
                    ResourceLocation.withDefaultNamespace("gravel"), 0.30,
                    ResourceLocation.withDefaultNamespace("coarse_dirt"), 0.15,
                    ResourceLocation.withDefaultNamespace("andesite"), 0.10
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
        List<BlockPos> waypoints = new ArrayList<>(selection.points());
        if (waypoints.size() < 2) {
            waypoints.clear();
            selection.posA().ifPresent(waypoints::add);
            selection.posB().ifPresent(waypoints::add);
        }

        if (waypoints.size() < 2) {
            return List.of();
        }

        BlockPalette pathPalette = StyleManager.loadPalette(level.getServer(), style.styleName(), "path_blocks")
                .or(() -> StyleManager.loadPalette(level.getServer(), style.styleName(), "painting"))
                .orElse(DEFAULT_PATH_PALETTE);

        Optional<BlockPalette> paintingPalette = StyleManager.loadPalette(level.getServer(), style.styleName(), "painting");
        boolean snapToGround = flags.resolveSnapToGround(GenerationType.PATH);
        int defaultWidth = switch (flags.roadMode()) {
            case SMALL -> 3;
            case BIG -> 7;
            case AUTO -> 4;
        };
        int resolvedWidth = flags.widthOrDefault(defaultWidth);
        double radius = Math.max(1.0, resolvedWidth / 2.0);
        int ceilRadius = (int) Math.ceil(radius + 1.0);

        List<Vec3> curve = SplineMath.sampleCatmullRom(waypoints, 0.45);
        BlockPlacementQueue.Builder builder = new BlockPlacementQueue.Builder();
        Set<Long> placedColumns = new HashSet<>();

        int stepCounter = 0;
        int ornamentInterval = Math.max(8, style.gridSize() * 3);

        for (int idx = 0; idx < curve.size(); idx++) {
            Vec3 center = curve.get(idx);
            int cx = (int) Math.floor(center.x);
            int cz = (int) Math.floor(center.z);

            for (int dx = -ceilRadius; dx <= ceilRadius; dx++) {
                for (int dz = -ceilRadius; dz <= ceilRadius; dz++) {
                    double dist = Math.sqrt(dx * dx + dz * dz);
                    if (dist > radius + 0.65) {
                        continue;
                    }

                    // Organic noise blending along the outer edge of the path brush
                    if (dist > radius - 0.65) {
                        double edgeFactor = (radius + 0.65 - dist) / 1.3;
                        double noise = ((random.nextDouble() * 0.7) + 0.15);
                        if (noise > edgeFactor) {
                            continue;
                        }
                    }

                    int wx = cx + dx;
                    int wz = cz + dz;
                    long colKey = BlockPos.asLong(wx, 0, wz);
                    if (!placedColumns.add(colKey)) {
                        continue;
                    }

                    int wy;
                    if (snapToGround) {
                        int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, wx, wz) - 1;
                        wy = Math.max(level.getMinBuildHeight(), surfaceY);
                    } else {
                        wy = (int) Math.round(center.y);
                    }

                    BlockPos targetPos = new BlockPos(wx, wy, wz);
                    BlockState chosen = pathPalette.sample(random, Blocks.COBBLESTONE.defaultBlockState());
                    builder.setBlock(targetPos, chosen);

                    // Clear 2 blocks of tall grass/plants above the road surface
                    for (int clearY = 1; clearY <= 2; clearY++) {
                        BlockPos above = targetPos.above(clearY);
                        BlockState aboveState = level.getBlockState(above);
                        if (!aboveState.isAir() && aboveState.getCollisionShape(level, above).isEmpty()) {
                            builder.setBlock(above, Blocks.AIR.defaultBlockState());
                        }
                    }
                }
            }

            // Occasionally place road ornaments, road props, or signals along the outer edge of the path
            stepCounter++;
            boolean allowRoadOrnaments = !flags.isExcluded(ModuleCategory.ROAD_ORNAMENT) && !flags.isExcluded(ModuleCategory.ORNAMENT);
            boolean allowRoadProps = !flags.isExcluded(ModuleCategory.ROAD_PROP);
            boolean allowRoadSignals = !flags.isExcluded(ModuleCategory.ROAD_SIGNAL);
            if ((allowRoadOrnaments || allowRoadProps || allowRoadSignals) && stepCounter % ornamentInterval == 0 && idx + 1 < curve.size()) {
                Vec3 next = curve.get(idx + 1);
                double tangentX = next.x - center.x;
                double tangentZ = next.z - center.z;
                double len = Math.sqrt(tangentX * tangentX + tangentZ * tangentZ);
                if (len > 1.0e-3) {
                    tangentX /= len;
                    tangentZ /= len;
                    // Perpendicular normal vector
                    double sideSign = (random.nextBoolean() ? 1.0 : -1.0);
                    double normalX = -tangentZ * sideSign;
                    double normalZ = tangentX * sideSign;

                    int ox = (int) Math.round(center.x + normalX * (radius + 1.8));
                    int oz = (int) Math.round(center.z + normalZ * (radius + 1.8));
                    int oy = snapToGround
                            ? level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ox, oz)
                            : (int) Math.round(center.y) + 1;

                    BlockPos ornamentOrigin = new BlockPos(ox, oy, oz);
                    Optional<StyleModuleEntry> chosenMod = Optional.empty();
                    double roll = random.nextDouble();
                    if (allowRoadSignals && roll < 0.20) {
                        chosenMod = style.pickWeightedModule(ModuleCategory.ROAD_SIGNAL, random);
                    }
                    if (chosenMod.isEmpty() && allowRoadProps && roll < 0.55) {
                        chosenMod = style.pickWeightedModule(ModuleCategory.ROAD_PROP, random);
                    }
                    if (chosenMod.isEmpty() && allowRoadOrnaments) {
                        chosenMod = style.pickWeightedModuleWithFallback(random, ModuleCategory.ROAD_ORNAMENT, ModuleCategory.ORNAMENT);
                    }
                    if (chosenMod.isPresent()) {
                        Rotation rot = rotationFromNormal(-normalX, -normalZ);
                        TemplatePlacementHelper.placeModuleIntoBuilder(
                                level,
                                style.styleName(),
                                chosenMod.get(),
                                ornamentOrigin,
                                rot,
                                Mirror.NONE,
                                paintingPalette,
                                random,
                                builder
                        );
                    }
                }
            }
        }

        return builder.buildList();
    }

    private static Rotation rotationFromNormal(double nx, double nz) {
        if (Math.abs(nx) > Math.abs(nz)) {
            return nx > 0 ? Rotation.CLOCKWISE_90 : Rotation.COUNTERCLOCKWISE_90;
        } else {
            return nz > 0 ? Rotation.CLOCKWISE_180 : Rotation.NONE;
        }
    }
}
