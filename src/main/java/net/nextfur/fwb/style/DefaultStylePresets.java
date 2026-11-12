package net.nextfur.fwb.style;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.nextfur.fwb.FwMain;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DefaultStylePresets {

    public static final List<String> PRESET_NAMES = List.of("medieval", "cyberpunk", "nordic", "desert");

    /**
     * Generates all built-in preset styles if they do not exist yet, or overwrites them when force=true.
     */
    public static int installPresets(MinecraftServer server, boolean force) {
        int installedCount = 0;
        for (String preset : PRESET_NAMES) {
            Path styleJson = StyleManager.getStyleDir(server, preset).resolve("style.json");
            if (!force && Files.exists(styleJson)) {
                continue;
            }
            try {
                switch (preset) {
                    case "medieval" -> createMedievalPreset(server);
                    case "cyberpunk" -> createCyberpunkPreset(server);
                    case "nordic" -> createNordicPreset(server);
                    case "desert" -> createDesertPreset(server);
                }
                installedCount++;
            } catch (Exception e) {
                FwMain.LOGGER.error("Failed to generate preset style '{}'", preset, e);
            }
        }
        return installedCount;
    }

    private static void createMedievalPreset(MinecraftServer server) throws IOException {
        Map<ResourceLocation, Double> paintingWeights = new LinkedHashMap<>();
        paintingWeights.put(ResourceLocation.withDefaultNamespace("stone_bricks"), 0.55);
        paintingWeights.put(ResourceLocation.withDefaultNamespace("mossy_stone_bricks"), 0.20);
        paintingWeights.put(ResourceLocation.withDefaultNamespace("cracked_stone_bricks"), 0.15);
        paintingWeights.put(ResourceLocation.withDefaultNamespace("cobblestone"), 0.10);

        Map<ResourceLocation, Double> pathWeights = new LinkedHashMap<>();
        pathWeights.put(ResourceLocation.withDefaultNamespace("cobblestone"), 0.50);
        pathWeights.put(ResourceLocation.withDefaultNamespace("gravel"), 0.25);
        pathWeights.put(ResourceLocation.withDefaultNamespace("coarse_dirt"), 0.15);
        pathWeights.put(ResourceLocation.withDefaultNamespace("mossy_cobblestone"), 0.10);

        Map<ResourceLocation, Double> floorWeights = new LinkedHashMap<>();
        floorWeights.put(ResourceLocation.withDefaultNamespace("spruce_planks"), 0.70);
        floorWeights.put(ResourceLocation.withDefaultNamespace("oak_planks"), 0.30);

        buildCompletePreset(
                server,
                "medieval",
                paintingWeights,
                pathWeights,
                floorWeights,
                Blocks.STONE_BRICKS.defaultBlockState(),
                Blocks.SPRUCE_LOG.defaultBlockState(),
                Blocks.WHITE_WOOL.defaultBlockState(), // Replaced during Painting Pass
                Blocks.GLASS_PANE.defaultBlockState(),
                Blocks.OAK_DOOR.defaultBlockState(),
                Blocks.DARK_OAK_STAIRS.defaultBlockState(),
                Blocks.DARK_OAK_PLANKS.defaultBlockState(),
                Blocks.COBBLESTONE_WALL.defaultBlockState(),
                Blocks.LANTERN.defaultBlockState(),
                Blocks.BOOKSHELF.defaultBlockState(),
                Blocks.MOSSY_STONE_BRICKS.defaultBlockState(),
                Blocks.COBBLESTONE.defaultBlockState(),
                Blocks.CHISELED_STONE_BRICKS.defaultBlockState()
        );
    }

    private static void createCyberpunkPreset(MinecraftServer server) throws IOException {
        Map<ResourceLocation, Double> paintingWeights = new LinkedHashMap<>();
        paintingWeights.put(ResourceLocation.withDefaultNamespace("polished_deepslate"), 0.55);
        paintingWeights.put(ResourceLocation.withDefaultNamespace("deepslate_tiles"), 0.25);
        paintingWeights.put(ResourceLocation.withDefaultNamespace("black_concrete"), 0.15);
        paintingWeights.put(ResourceLocation.withDefaultNamespace("cyan_terracotta"), 0.05);

        Map<ResourceLocation, Double> pathWeights = new LinkedHashMap<>();
        pathWeights.put(ResourceLocation.withDefaultNamespace("black_concrete"), 0.60);
        pathWeights.put(ResourceLocation.withDefaultNamespace("gray_concrete"), 0.25);
        pathWeights.put(ResourceLocation.withDefaultNamespace("polished_basalt"), 0.15);

        Map<ResourceLocation, Double> floorWeights = new LinkedHashMap<>();
        floorWeights.put(ResourceLocation.withDefaultNamespace("smooth_stone"), 0.70);
        floorWeights.put(ResourceLocation.withDefaultNamespace("polished_deepslate"), 0.30);

        buildCompletePreset(
                server,
                "cyberpunk",
                paintingWeights,
                pathWeights,
                floorWeights,
                Blocks.POLISHED_DEEPSLATE.defaultBlockState(),
                Blocks.DEEPSLATE_TILES.defaultBlockState(),
                Blocks.STONE_BRICKS.defaultBlockState(), // Replaced during Painting Pass
                Blocks.CYAN_STAINED_GLASS_PANE.defaultBlockState(),
                Blocks.WARPED_DOOR.defaultBlockState(),
                Blocks.POLISHED_DEEPSLATE_STAIRS.defaultBlockState(),
                Blocks.BLACK_CONCRETE.defaultBlockState(),
                Blocks.DEEPSLATE_TILE_WALL.defaultBlockState(),
                Blocks.SOUL_LANTERN.defaultBlockState(),
                Blocks.LODESTONE.defaultBlockState(),
                Blocks.DEEPSLATE_BRICKS.defaultBlockState(),
                Blocks.BLACK_CONCRETE.defaultBlockState(),
                Blocks.SEA_LANTERN.defaultBlockState()
        );
    }

    private static void createNordicPreset(MinecraftServer server) throws IOException {
        Map<ResourceLocation, Double> paintingWeights = new LinkedHashMap<>();
        paintingWeights.put(ResourceLocation.withDefaultNamespace("spruce_planks"), 0.50);
        paintingWeights.put(ResourceLocation.withDefaultNamespace("stripped_spruce_log"), 0.25);
        paintingWeights.put(ResourceLocation.withDefaultNamespace("cobblestone"), 0.15);
        paintingWeights.put(ResourceLocation.withDefaultNamespace("mossy_cobblestone"), 0.10);

        Map<ResourceLocation, Double> pathWeights = new LinkedHashMap<>();
        pathWeights.put(ResourceLocation.withDefaultNamespace("coarse_dirt"), 0.45);
        pathWeights.put(ResourceLocation.withDefaultNamespace("podzol"), 0.30);
        pathWeights.put(ResourceLocation.withDefaultNamespace("gravel"), 0.15);
        pathWeights.put(ResourceLocation.withDefaultNamespace("cobblestone"), 0.10);

        Map<ResourceLocation, Double> floorWeights = new LinkedHashMap<>();
        floorWeights.put(ResourceLocation.withDefaultNamespace("spruce_planks"), 0.80);
        floorWeights.put(ResourceLocation.withDefaultNamespace("dark_oak_planks"), 0.20);

        buildCompletePreset(
                server,
                "nordic",
                paintingWeights,
                pathWeights,
                floorWeights,
                Blocks.COBBLESTONE.defaultBlockState(),
                Blocks.SPRUCE_LOG.defaultBlockState(),
                Blocks.STONE_BRICKS.defaultBlockState(),
                Blocks.IRON_BARS.defaultBlockState(),
                Blocks.SPRUCE_DOOR.defaultBlockState(),
                Blocks.SPRUCE_STAIRS.defaultBlockState(),
                Blocks.SPRUCE_PLANKS.defaultBlockState(),
                Blocks.COBBLESTONE_WALL.defaultBlockState(),
                Blocks.LANTERN.defaultBlockState(),
                Blocks.BARREL.defaultBlockState(),
                Blocks.MOSSY_COBBLESTONE.defaultBlockState(),
                Blocks.COARSE_DIRT.defaultBlockState(),
                Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState()
        );
    }

    private static void createDesertPreset(MinecraftServer server) throws IOException {
        Map<ResourceLocation, Double> paintingWeights = new LinkedHashMap<>();
        paintingWeights.put(ResourceLocation.withDefaultNamespace("cut_sandstone"), 0.55);
        paintingWeights.put(ResourceLocation.withDefaultNamespace("smooth_sandstone"), 0.25);
        paintingWeights.put(ResourceLocation.withDefaultNamespace("sandstone"), 0.15);
        paintingWeights.put(ResourceLocation.withDefaultNamespace("terracotta"), 0.05);

        Map<ResourceLocation, Double> pathWeights = new LinkedHashMap<>();
        pathWeights.put(ResourceLocation.withDefaultNamespace("sandstone"), 0.50);
        pathWeights.put(ResourceLocation.withDefaultNamespace("smooth_sandstone"), 0.30);
        pathWeights.put(ResourceLocation.withDefaultNamespace("orange_terracotta"), 0.20);

        Map<ResourceLocation, Double> floorWeights = new LinkedHashMap<>();
        floorWeights.put(ResourceLocation.withDefaultNamespace("smooth_sandstone"), 0.65);
        floorWeights.put(ResourceLocation.withDefaultNamespace("birch_planks"), 0.35);

        buildCompletePreset(
                server,
                "desert",
                paintingWeights,
                pathWeights,
                floorWeights,
                Blocks.CUT_SANDSTONE.defaultBlockState(),
                Blocks.CHISELED_SANDSTONE.defaultBlockState(),
                Blocks.STONE_BRICKS.defaultBlockState(),
                Blocks.ACACIA_FENCE.defaultBlockState(),
                Blocks.JUNGLE_DOOR.defaultBlockState(),
                Blocks.SANDSTONE_STAIRS.defaultBlockState(),
                Blocks.SMOOTH_SANDSTONE.defaultBlockState(),
                Blocks.SANDSTONE_WALL.defaultBlockState(),
                Blocks.LANTERN.defaultBlockState(),
                Blocks.DECORATED_POT.defaultBlockState(),
                Blocks.CHISELED_RED_SANDSTONE.defaultBlockState(),
                Blocks.SMOOTH_SANDSTONE.defaultBlockState(),
                Blocks.ORANGE_TERRACOTTA.defaultBlockState()
        );
    }

    private static void buildCompletePreset(
            MinecraftServer server,
            String styleName,
            Map<ResourceLocation, Double> paintingWeights,
            Map<ResourceLocation, Double> pathWeights,
            Map<ResourceLocation, Double> floorWeights,
            BlockState baseTrim,
            BlockState pillarBlock,
            BlockState paintableWallFill,
            BlockState windowPane,
            BlockState doorBaseState,
            BlockState roofStairBase,
            BlockState roofPlank,
            BlockState postWall,
            BlockState lanternBase,
            BlockState propAccent,
            BlockState dungeonAccent,
            BlockState roadFill,
            BlockState intersectionCenter
    ) throws IOException {
        StyleDefinition def = StyleManager.createStyle(server, styleName, 5);

        // Save the 3 palettes
        def = savePresetPalette(server, def, new BlockPalette("painting", paintingWeights));
        def = savePresetPalette(server, def, new BlockPalette("path_blocks", pathWeights));
        def = savePresetPalette(server, def, new BlockPalette("floor", floorWeights));

        // 1. WALL module (5x4x1, facing NORTH -Z)
        SchematicBuilder wall = new SchematicBuilder(5, 4, 1);
        for (int x = 0; x < 5; x++) {
            for (int y = 0; y < 4; y++) {
                if (x == 0 || x == 4 || y == 3) {
                    wall.set(x, y, 0, pillarBlock);
                } else if (y == 0) {
                    wall.set(x, y, 0, baseTrim);
                } else {
                    wall.set(x, y, 0, paintableWallFill);
                }
            }
        }
        def = savePresetModule(server, def, ModuleCategory.WALL, "standard_wall", 1.0, wall);

        // 2. CORNER module (1x4x1)
        SchematicBuilder corner = new SchematicBuilder(1, 4, 1);
        corner.set(0, 0, 0, baseTrim);
        for (int y = 1; y < 4; y++) {
            corner.set(0, y, 0, pillarBlock);
        }
        def = savePresetModule(server, def, ModuleCategory.CORNER, "pillar_corner", 1.0, corner);

        // 3. WINDOW module (5x4x1, facing NORTH -Z)
        SchematicBuilder window = new SchematicBuilder(5, 4, 1);
        for (int x = 0; x < 5; x++) {
            for (int y = 0; y < 4; y++) {
                if (x == 0 || x == 4 || y == 3) {
                    window.set(x, y, 0, pillarBlock);
                } else if (y == 0) {
                    window.set(x, y, 0, baseTrim);
                } else if (x == 2 && (y == 1 || y == 2)) {
                    window.set(x, y, 0, windowPane);
                } else {
                    window.set(x, y, 0, paintableWallFill);
                }
            }
        }
        def = savePresetModule(server, def, ModuleCategory.WINDOW, "framed_window", 1.0, window);

        // 4. DOOR module (5x4x1, facing NORTH -Z)
        SchematicBuilder door = new SchematicBuilder(5, 4, 1);
        for (int x = 0; x < 5; x++) {
            for (int y = 0; y < 4; y++) {
                if (x == 0 || x == 4 || y == 3) {
                    door.set(x, y, 0, pillarBlock);
                } else if (x == 2 && y == 1) {
                    door.set(x, y, 0, doorBaseState
                            .setValue(DoorBlock.FACING, Direction.NORTH)
                            .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
                } else if (x == 2 && y == 2) {
                    door.set(x, y, 0, doorBaseState
                            .setValue(DoorBlock.FACING, Direction.NORTH)
                            .setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
                } else if (y == 0) {
                    door.set(x, y, 0, baseTrim);
                } else {
                    door.set(x, y, 0, paintableWallFill);
                }
            }
        }
        def = savePresetModule(server, def, ModuleCategory.DOOR, "main_entrance", 1.0, door);

        // 5. ROOF module (5x3x5)
        SchematicBuilder roof = new SchematicBuilder(5, 3, 5);
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                if (z == 0) {
                    roof.set(x, 0, z, roofStairBase.setValue(StairBlock.FACING, Direction.SOUTH).setValue(StairBlock.HALF, Half.BOTTOM));
                } else if (z == 4) {
                    roof.set(x, 0, z, roofStairBase.setValue(StairBlock.FACING, Direction.NORTH).setValue(StairBlock.HALF, Half.BOTTOM));
                } else {
                    roof.set(x, 0, z, roofPlank);
                }
                if (z >= 1 && z <= 3) {
                    roof.set(x, 1, z, roofPlank);
                }
                if (z == 2) {
                    roof.set(x, 2, z, pillarBlock);
                }
            }
        }
        def = savePresetModule(server, def, ModuleCategory.ROOF, "pitched_roof", 1.0, roof);

        // 6. ORNAMENT module (1x4x1 street lamppost)
        SchematicBuilder ornament = new SchematicBuilder(1, 4, 1);
        ornament.set(0, 0, 0, baseTrim);
        ornament.set(0, 1, 0, postWall);
        ornament.set(0, 2, 0, postWall);
        ornament.set(0, 3, 0, lanternBase.setValue(LanternBlock.HANGING, false));
        def = savePresetModule(server, def, ModuleCategory.ORNAMENT, "street_lamp", 1.0, ornament);

        // 7. INTERIOR_PROP module (3x2x1 shelving & table)
        SchematicBuilder prop = new SchematicBuilder(3, 2, 1);
        prop.set(0, 0, 0, propAccent);
        prop.set(0, 1, 0, propAccent);
        prop.set(1, 0, 0, Blocks.CRAFTING_TABLE.defaultBlockState());
        prop.set(1, 1, 0, lanternBase.setValue(LanternBlock.HANGING, false));
        prop.set(2, 0, 0, Blocks.CHEST.defaultBlockState());
        def = savePresetModule(server, def, ModuleCategory.INTERIOR_PROP, "workstation_prop", 1.0, prop);

        // 8. DUNGEON_ROOM module (7x5x7 chamber)
        SchematicBuilder dungeonRoom = new SchematicBuilder(7, 5, 7);
        for (int y = 0; y < 5; y++) {
            for (int x = 0; x < 7; x++) {
                for (int z = 0; z < 7; z++) {
                    boolean shell = (x == 0 || x == 6 || z == 0 || z == 6 || y == 0 || y == 4);
                    boolean cornerCol = ((x == 1 || x == 5) && (z == 1 || z == 5) && y > 0 && y < 4);
                    if (shell) {
                        dungeonRoom.set(x, y, z, (x + z + y) % 3 == 0 ? dungeonAccent : paintableWallFill);
                    } else if (cornerCol) {
                        dungeonRoom.set(x, y, z, pillarBlock);
                    } else {
                        dungeonRoom.set(x, y, z, Blocks.AIR.defaultBlockState());
                    }
                }
            }
        }
        dungeonRoom.set(3, 3, 3, lanternBase.setValue(LanternBlock.HANGING, true));
        dungeonRoom.set(3, 1, 3, Blocks.CHEST.defaultBlockState());
        def = savePresetModule(server, def, ModuleCategory.DUNGEON_ROOM, "vault_chamber", 1.0, dungeonRoom);

        // 9. DUNGEON_CORRIDOR module (3x4x3 segment)
        SchematicBuilder corridor = new SchematicBuilder(3, 4, 3);
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 3; x++) {
                for (int z = 0; z < 3; z++) {
                    if (y == 0 || y == 3 || x == 0 || x == 2) {
                        corridor.set(x, y, z, paintableWallFill);
                    } else {
                        corridor.set(x, y, z, Blocks.AIR.defaultBlockState());
                    }
                }
            }
        }
        def = savePresetModule(server, def, ModuleCategory.DUNGEON_CORRIDOR, "corridor_segment", 1.0, corridor);

        // 10. ROAD_SEGMENT module (5x1x5)
        SchematicBuilder roadSeg = new SchematicBuilder(5, 1, 5);
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                if (x == 0 || x == 4) {
                    roadSeg.set(x, 0, z, baseTrim);
                } else {
                    roadSeg.set(x, 0, z, roadFill);
                }
            }
        }
        def = savePresetModule(server, def, ModuleCategory.ROAD_SEGMENT, "street_segment", 1.0, roadSeg);

        // 11. INTERSECTION module (5x1x5)
        SchematicBuilder intersection = new SchematicBuilder(5, 1, 5);
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                if (x == 2 && z == 2) {
                    intersection.set(x, 0, z, intersectionCenter);
                } else if ((x == 0 || x == 4) && (z == 0 || z == 4)) {
                    intersection.set(x, 0, z, baseTrim);
                } else {
                    intersection.set(x, 0, z, roadFill);
                }
            }
        }
        def = savePresetModule(server, def, ModuleCategory.INTERSECTION, "crossroad", 1.0, intersection);

        StyleManager.saveStyleDefinition(server, def);
    }

    private static StyleDefinition savePresetPalette(
            MinecraftServer server,
            StyleDefinition style,
            BlockPalette palette
    ) throws IOException {
        String relPath = "palettes/" + palette.paletteType() + ".json";
        Path fullPath = StyleManager.getStyleDir(server, style.styleName()).resolve(relPath);
        Files.createDirectories(fullPath.getParent());

        var encoded = BlockPalette.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, palette)
                .getOrThrow(err -> new IOException("Failed to encode preset palette: " + err));
        Files.writeString(fullPath, new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(encoded));
        return style.withPalette(palette.paletteType(), relPath);
    }

    private static StyleDefinition savePresetModule(
            MinecraftServer server,
            StyleDefinition style,
            ModuleCategory category,
            String moduleName,
            double weight,
            SchematicBuilder schematic
    ) throws IOException {
        String relPath = "modules/" + category.getSerializedName() + "/" + moduleName + ".nbt";
        Path fullPath = StyleManager.getStyleDir(server, style.styleName()).resolve(relPath);
        Files.createDirectories(fullPath.getParent());

        CompoundTag nbt = schematic.toStructureTemplateNbt();
        NbtIo.writeCompressed(nbt, fullPath);

        StyleModuleEntry entry = new StyleModuleEntry(
                moduleName,
                category,
                relPath,
                weight,
                new BlockPos(schematic.sizeX, schematic.sizeY, schematic.sizeZ)
        );
        return style.withModule(entry);
    }

    private static class SchematicBuilder {
        final int sizeX;
        final int sizeY;
        final int sizeZ;
        final List<BlockState> palette = new ArrayList<>();
        final Map<BlockPos, Integer> blocks = new LinkedHashMap<>();

        SchematicBuilder(int sizeX, int sizeY, int sizeZ) {
            this.sizeX = sizeX;
            this.sizeY = sizeY;
            this.sizeZ = sizeZ;
        }

        void set(int x, int y, int z, BlockState state) {
            int idx = palette.indexOf(state);
            if (idx < 0) {
                idx = palette.size();
                palette.add(state);
            }
            blocks.put(new BlockPos(x, y, z), idx);
        }

        CompoundTag toStructureTemplateNbt() {
            CompoundTag root = new CompoundTag();

            ListTag sizeTag = new ListTag();
            sizeTag.add(IntTag.valueOf(sizeX));
            sizeTag.add(IntTag.valueOf(sizeY));
            sizeTag.add(IntTag.valueOf(sizeZ));
            root.put("size", sizeTag);

            ListTag paletteTag = new ListTag();
            for (BlockState state : palette) {
                paletteTag.add(NbtUtils.writeBlockState(state));
            }
            root.put("palette", paletteTag);

            ListTag blocksTag = new ListTag();
            for (Map.Entry<BlockPos, Integer> entry : blocks.entrySet()) {
                BlockPos pos = entry.getKey();
                CompoundTag blockTag = new CompoundTag();
                ListTag posList = new ListTag();
                posList.add(IntTag.valueOf(pos.getX()));
                posList.add(IntTag.valueOf(pos.getY()));
                posList.add(IntTag.valueOf(pos.getZ()));
                blockTag.put("pos", posList);
                blockTag.putInt("state", entry.getValue());
                blocksTag.add(blockTag);
            }
            root.put("blocks", blocksTag);
            root.put("entities", new ListTag());
            root.putInt("DataVersion", SharedConstants.getCurrentVersion().getDataVersion().getVersion());
            return root;
        }
    }
}
