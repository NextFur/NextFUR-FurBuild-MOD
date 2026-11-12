package net.nextfur.fwb.generator.engines;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.nextfur.fwb.data.PlayerSelectionData;
import net.nextfur.fwb.generator.BlockPlacementQueue;
import net.nextfur.fwb.generator.GenerationFlags;
import net.nextfur.fwb.generator.TemplatePlacementHelper;
import net.nextfur.fwb.style.BlockPalette;
import net.nextfur.fwb.style.ModuleCategory;
import net.nextfur.fwb.style.StyleDefinition;
import net.nextfur.fwb.style.StyleManager;
import net.nextfur.fwb.style.StyleModuleEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class InteriorGeneratorEngine {

    private static final BlockPalette DEFAULT_FLOOR_PALETTE = new BlockPalette(
            "floor",
            Map.of(
                    ResourceLocation.withDefaultNamespace("oak_planks"), 0.75,
                    ResourceLocation.withDefaultNamespace("spruce_planks"), 0.25
            )
    );

    private static final BlockPalette DEFAULT_WALL_PALETTE = new BlockPalette(
            "painting",
            Map.of(
                    ResourceLocation.withDefaultNamespace("stone_bricks"), 0.70,
                    ResourceLocation.withDefaultNamespace("cracked_stone_bricks"), 0.15,
                    ResourceLocation.withDefaultNamespace("mossy_stone_bricks"), 0.15
            )
    );

    private record Room2D(int minX, int minZ, int maxX, int maxZ) {
        int width() {
            return maxX - minX + 1;
        }

        int length() {
            return maxZ - minZ + 1;
        }

        int centerX() {
            return (minX + maxX) / 2;
        }

        int centerZ() {
            return (minZ + maxZ) / 2;
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
        BlockPlacementQueue.Builder builder = new BlockPlacementQueue.Builder();
        generateIntoBuilder(level, boxOpt.get(), style, flags, random, builder);
        return builder.buildList();
    }

    public static void generateIntoBuilder(
            ServerLevel level,
            BoundingBox outerBox,
            StyleDefinition style,
            GenerationFlags flags,
            RandomSource random,
            BlockPlacementQueue.Builder builder
    ) {
        // Inset by 1 block if the box is wide enough so we stay inside the exterior walls
        int minX = outerBox.getXSpan() > 6 ? outerBox.minX() + 1 : outerBox.minX();
        int maxX = outerBox.getXSpan() > 6 ? outerBox.maxX() - 1 : outerBox.maxX();
        int minZ = outerBox.getZSpan() > 6 ? outerBox.minZ() + 1 : outerBox.minZ();
        int maxZ = outerBox.getZSpan() > 6 ? outerBox.maxZ() - 1 : outerBox.maxZ();
        int minY = outerBox.minY();
        int maxY = outerBox.maxY();

        if (maxX - minX < 3 || maxZ - minZ < 3) {
            return;
        }

        int moduleHeight = Math.max(4, style.moduleHeight());
        int totalFloors = flags.floors().orElseGet(() -> Math.max(1, (maxY - minY + 1) / moduleHeight));

        BlockPalette floorPalette = StyleManager.loadPalette(level.getServer(), style.styleName(), "floor")
                .orElse(DEFAULT_FLOOR_PALETTE);
        BlockPalette wallPalette = StyleManager.loadPalette(level.getServer(), style.styleName(), "painting")
                .orElse(DEFAULT_WALL_PALETTE);
        Optional<BlockPalette> paintingOpt = StyleManager.loadPalette(level.getServer(), style.styleName(), "painting");

        // Consistent stairwell X/Z coordinate across all floors (4x2 bay near a corner)
        int stairMinX = minX + 1;
        int stairMaxX = Math.min(maxX - 1, stairMinX + Math.min(3, moduleHeight - 1));
        int stairZ = minZ + 1;

        for (int floorIdx = 0; floorIdx < totalFloors; floorIdx++) {
            int floorY = minY + floorIdx * moduleHeight;
            int ceilingY = floorY + moduleHeight - 1;

            // 1. Floor Slicing: Place horizontal floor slab/planks
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    // Cut stairwell hole on floors > 0
                    if (floorIdx > 0 && x >= stairMinX && x <= stairMaxX && z >= stairZ && z <= stairZ + 1) {
                        builder.setBlock(new BlockPos(x, floorY, z), Blocks.AIR.defaultBlockState());
                        continue;
                    }
                    builder.setBlock(
                            new BlockPos(x, floorY, z),
                            floorPalette.sample(random, Blocks.OAK_PLANKS.defaultBlockState())
                    );
                }
            }

            // Clear interior air volume for this floor
            for (int y = floorY + 1; y <= ceilingY; y++) {
                for (int x = minX; x <= maxX; x++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        builder.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
                    }
                }
            }

            // 2. Room Subdivision: 2D BSP subdivision with doorways
            List<Room2D> rooms = new ArrayList<>();
            Room2D floorFootprint = new Room2D(minX, minZ, maxX, maxZ);
            subdivideRooms(
                    floorFootprint,
                    floorY + 1,
                    ceilingY,
                    stairMinX,
                    stairMaxX,
                    stairZ,
                    wallPalette,
                    flags,
                    random,
                    rooms,
                    builder
            );

            // 3. Stairwell Placement connecting floorIdx to floorIdx + 1
            if (floorIdx < totalFloors - 1 && (maxX - minX >= 5)) {
                for (int step = 0; step < moduleHeight; step++) {
                    int sx = stairMinX + Math.min(step, stairMaxX - stairMinX);
                    int sy = floorY + 1 + step;
                    BlockState stairState = Blocks.OAK_STAIRS.defaultBlockState()
                            .setValue(StairBlock.FACING, Direction.EAST)
                            .setValue(StairBlock.HALF, Half.BOTTOM);
                    builder.setBlock(new BlockPos(sx, sy, stairZ), stairState);
                    // Clear headroom above stairs
                    for (int clear = 1; clear <= 3; clear++) {
                        builder.setBlock(new BlockPos(sx, sy + clear, stairZ), Blocks.AIR.defaultBlockState());
                    }
                }
            }

            // 4. Prop Population: Wall-hugging, Center, and Ceiling props
            if (!flags.isExcluded(ModuleCategory.INTERIOR_PROP)) {
                for (Room2D room : rooms) {
                    populateRoomProps(
                            level,
                            room,
                            floorY + 1,
                            ceilingY,
                            stairMinX,
                            stairMaxX,
                            stairZ,
                            style,
                            paintingOpt,
                            random,
                            builder
                    );
                }
            }
        }
    }

    private static void subdivideRooms(
            Room2D room,
            int wallStartY,
            int wallEndY,
            int stairMinX,
            int stairMaxX,
            int stairZ,
            BlockPalette wallPalette,
            GenerationFlags flags,
            RandomSource random,
            List<Room2D> outRooms,
            BlockPlacementQueue.Builder builder
    ) {
        int minRoomDim = 6;
        boolean canSplitX = room.width() >= minRoomDim * 2 + 1;
        boolean canSplitZ = room.length() >= minRoomDim * 2 + 1;

        if ((!canSplitX && !canSplitZ) || flags.isExcluded(ModuleCategory.WALL)) {
            outRooms.add(room);
            return;
        }

        boolean splitX = canSplitX && (!canSplitZ || room.width() >= room.length());
        if (splitX) {
            int splitXPos = room.minX + minRoomDim + random.nextInt(Math.max(1, room.width() - minRoomDim * 2));
            // Avoid splitting directly through the stairwell
            if (splitXPos >= stairMinX - 1 && splitXPos <= stairMaxX + 1) {
                outRooms.add(room);
                return;
            }

            int doorZ = (room.minZ + room.maxZ) / 2;
            for (int z = room.minZ; z <= room.maxZ; z++) {
                for (int y = wallStartY; y <= wallEndY; y++) {
                    if (Math.abs(z - doorZ) <= 0 && y <= wallStartY + 1) {
                        // Doorway opening (1x2)
                        continue;
                    }
                    builder.setBlock(
                            new BlockPos(splitXPos, y, z),
                            wallPalette.sample(random, Blocks.OAK_PLANKS.defaultBlockState())
                    );
                }
            }

            subdivideRooms(new Room2D(room.minX, room.minZ, splitXPos - 1, room.maxZ),
                    wallStartY, wallEndY, stairMinX, stairMaxX, stairZ, wallPalette, flags, random, outRooms, builder);
            subdivideRooms(new Room2D(splitXPos + 1, room.minZ, room.maxX, room.maxZ),
                    wallStartY, wallEndY, stairMinX, stairMaxX, stairZ, wallPalette, flags, random, outRooms, builder);
        } else {
            int splitZPos = room.minZ + minRoomDim + random.nextInt(Math.max(1, room.length() - minRoomDim * 2));
            if (Math.abs(splitZPos - stairZ) <= 2) {
                outRooms.add(room);
                return;
            }

            int doorX = (room.minX + room.maxX) / 2;
            for (int x = room.minX; x <= room.maxX; x++) {
                for (int y = wallStartY; y <= wallEndY; y++) {
                    if (Math.abs(x - doorX) <= 0 && y <= wallStartY + 1) {
                        continue;
                    }
                    builder.setBlock(
                            new BlockPos(x, y, splitZPos),
                            wallPalette.sample(random, Blocks.OAK_PLANKS.defaultBlockState())
                    );
                }
            }

            subdivideRooms(new Room2D(room.minX, room.minZ, room.maxX, splitZPos - 1),
                    wallStartY, wallEndY, stairMinX, stairMaxX, stairZ, wallPalette, flags, random, outRooms, builder);
            subdivideRooms(new Room2D(room.minX, splitZPos + 1, room.maxX, room.maxZ),
                    wallStartY, wallEndY, stairMinX, stairMaxX, stairZ, wallPalette, flags, random, outRooms, builder);
        }
    }

    private static void populateRoomProps(
            ServerLevel level,
            Room2D room,
            int propY,
            int ceilingY,
            int stairMinX,
            int stairMaxX,
            int stairZ,
            StyleDefinition style,
            Optional<BlockPalette> paintingOpt,
            RandomSource random,
            BlockPlacementQueue.Builder builder
    ) {
        if (room.width() < 4 || room.length() < 4) {
            return;
        }

        int cx = room.centerX();
        int cz = room.centerZ();

        // Ceiling prop (hanging lantern chandelier at room center)
        if (ceilingY > propY + 1) {
            BlockState hangingLantern = Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);
            builder.setBlock(new BlockPos(cx, ceilingY, cz), hangingLantern);
        }

        // If the style has custom interior_prop schematics, place one near the room center or corner
        Optional<StyleModuleEntry> propEntry = style.pickWeightedModule(ModuleCategory.INTERIOR_PROP, random);
        if (propEntry.isPresent()) {
            BlockPos propOrigin = new BlockPos(room.minX + 1, propY, room.minZ + 1);
            if (!(propOrigin.getX() >= stairMinX - 1 && propOrigin.getX() <= stairMaxX + 1 && Math.abs(propOrigin.getZ() - stairZ) <= 2)) {
                Rotation rot = Rotation.values()[random.nextInt(Rotation.values().length)];
                TemplatePlacementHelper.placeModuleIntoBuilder(
                        level, style.styleName(), propEntry.get(),
                        propOrigin, rot, Mirror.NONE, paintingOpt, random, builder
                );
                return;
            }
        }

        // Procedural fallback props:
        // 1. Center prop: carpet or table in larger rooms
        if (!(cx >= stairMinX - 1 && cx <= stairMaxX + 1 && Math.abs(cz - stairZ) <= 2)) {
            builder.setBlock(new BlockPos(cx, propY, cz), Blocks.RED_CARPET.defaultBlockState());
        }

        // 2. Wall-hugging props in corners (avoiding mid-wall doorways and stairwells)
        BlockPos cornerPropPos = new BlockPos(room.maxX - 1, propY, room.maxZ - 1);
        if (!(cornerPropPos.getX() >= stairMinX - 1 && cornerPropPos.getX() <= stairMaxX + 1 && Math.abs(cornerPropPos.getZ() - stairZ) <= 2)) {
            BlockState propBlock = switch (random.nextInt(4)) {
                case 0 -> Blocks.BOOKSHELF.defaultBlockState();
                case 1 -> Blocks.CRAFTING_TABLE.defaultBlockState();
                case 2 -> Blocks.BARREL.defaultBlockState();
                default -> Blocks.CHEST.defaultBlockState();
            };
            builder.setBlock(cornerPropPos, propBlock);
        }
    }
}
