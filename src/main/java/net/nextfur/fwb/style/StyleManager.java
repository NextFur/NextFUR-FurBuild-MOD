package net.nextfur.fwb.style;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.storage.LevelResource;
import net.nextfur.fwb.FwMain;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

public class StyleManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static Path getStylesRootDir(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("furbuild").resolve("styles");
    }

    public static Path getStyleDir(MinecraftServer server, String styleName) {
        String safeName = sanitizeName(styleName);
        return getStylesRootDir(server).resolve(safeName);
    }

    public static String sanitizeName(String name) {
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_\\-]", "_");
    }

    public static List<String> listStyles(MinecraftServer server) {
        Path root = getStylesRootDir(server);
        if (!Files.exists(root)) {
            return Collections.emptyList();
        }
        List<String> styles = new ArrayList<>();
        try (Stream<Path> stream = Files.list(root)) {
            stream.filter(Files::isDirectory)
                    .filter(dir -> Files.exists(dir.resolve("style.json")))
                    .forEach(dir -> styles.add(dir.getFileName().toString()));
        } catch (IOException e) {
            FwMain.LOGGER.error("Failed to list FurBuild styles", e);
        }
        return styles;
    }

    public static StyleDefinition createStyle(MinecraftServer server, String styleName, int gridSize) throws IOException {
        String safeName = sanitizeName(styleName);
        Path styleDir = getStyleDir(server, safeName);
        Files.createDirectories(styleDir);
        Files.createDirectories(styleDir.resolve("modules"));
        Files.createDirectories(styleDir.resolve("palettes"));

        StyleDefinition definition = StyleDefinition.createNew(safeName, gridSize);
        saveStyleDefinition(server, definition);
        return definition;
    }

    public static void saveStyleDefinition(MinecraftServer server, StyleDefinition definition) throws IOException {
        Path styleDir = getStyleDir(server, definition.styleName());
        Files.createDirectories(styleDir);
        Path styleJson = styleDir.resolve("style.json");

        DataResult<JsonElement> result = StyleDefinition.CODEC.encodeStart(JsonOps.INSTANCE, definition);
        JsonElement json = result.getOrThrow(err -> new IOException("Failed to encode style.json: " + err));
        try (Writer writer = Files.newBufferedWriter(styleJson, StandardCharsets.UTF_8)) {
            GSON.toJson(json, writer);
        }
    }

    public static Optional<StyleDefinition> loadStyle(MinecraftServer server, String styleName) {
        Path styleJson = getStyleDir(server, styleName).resolve("style.json");
        if (!Files.exists(styleJson)) {
            return Optional.empty();
        }
        try (Reader reader = Files.newBufferedReader(styleJson, StandardCharsets.UTF_8)) {
            JsonElement element = JsonParser.parseReader(reader);
            return StyleDefinition.CODEC.parse(JsonOps.INSTANCE, element)
                    .resultOrPartial(err -> FwMain.LOGGER.error("Error parsing style.json for {}: {}", styleName, err));
        } catch (Exception e) {
            FwMain.LOGGER.error("Failed to load style {}", styleName, e);
            return Optional.empty();
        }
    }

    public static StyleModuleEntry saveModule(
            ServerLevel level,
            String styleName,
            ModuleCategory category,
            String moduleName,
            double weight,
            BoundingBox selectionBox
    ) throws IOException {
        MinecraftServer server = level.getServer();
        String safeStyle = sanitizeName(styleName);
        String safeModule = sanitizeName(moduleName);

        StyleDefinition style = loadStyle(server, safeStyle)
                .orElseGet(() -> {
                    try {
                        return createStyle(server, safeStyle, 5);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });

        BlockPos minPos = new BlockPos(selectionBox.minX(), selectionBox.minY(), selectionBox.minZ());
        Vec3i size = new Vec3i(
                selectionBox.getXSpan(),
                selectionBox.getYSpan(),
                selectionBox.getZSpan()
        );

        StructureTemplate template = new StructureTemplate();
        template.fillFromWorld(level, minPos, size, true, Blocks.STRUCTURE_VOID);

        String relativeFile = "modules/" + category.getSerializedName() + "/" + safeModule + ".nbt";
        Path nbtPath = getStyleDir(server, safeStyle).resolve(relativeFile);
        Files.createDirectories(nbtPath.getParent());

        CompoundTag tag = template.save(new CompoundTag());
        NbtIo.writeCompressed(tag, nbtPath);

        StyleModuleEntry entry = new StyleModuleEntry(
                safeModule,
                category,
                relativeFile,
                weight,
                new BlockPos(size.getX(), size.getY(), size.getZ())
        );

        StyleDefinition updatedStyle = style.withModule(entry);
        saveStyleDefinition(server, updatedStyle);
        return entry;
    }

    public static StyleModuleEntry saveMappedModule(
            ServerLevel level,
            String styleName,
            ModuleCategory category,
            String moduleName,
            double weight,
            BoundingBox objectBox,
            Optional<BoundingBox> parentPlaceBox
    ) throws IOException {
        Rotation normalizeRot = Rotation.NONE;
        if (parentPlaceBox.isPresent() && category.isDirectionalFacade()) {
            normalizeRot = detectNormalizationRotation(objectBox, parentPlaceBox.get());
        }
        if (normalizeRot == Rotation.NONE) {
            return saveModule(level, styleName, category, moduleName, weight, objectBox);
        }

        MinecraftServer server = level.getServer();
        String safeStyle = sanitizeName(styleName);
        String safeModule = sanitizeName(moduleName);

        StyleDefinition style = loadStyle(server, safeStyle)
                .orElseGet(() -> {
                    try {
                        return createStyle(server, safeStyle, 5);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });

        int sizeX = objectBox.getXSpan();
        int sizeY = objectBox.getYSpan();
        int sizeZ = objectBox.getZSpan();
        int normSizeX = (normalizeRot == Rotation.CLOCKWISE_90 || normalizeRot == Rotation.COUNTERCLOCKWISE_90) ? sizeZ : sizeX;
        int normSizeZ = (normalizeRot == Rotation.CLOCKWISE_90 || normalizeRot == Rotation.COUNTERCLOCKWISE_90) ? sizeX : sizeZ;

        CompoundTag rootTag = buildRotatedStructureTag(level, objectBox, normalizeRot, sizeX, sizeY, sizeZ, normSizeX, normSizeZ);

        String relativeFile = "modules/" + category.getSerializedName() + "/" + safeModule + ".nbt";
        Path nbtPath = getStyleDir(server, safeStyle).resolve(relativeFile);
        Files.createDirectories(nbtPath.getParent());
        NbtIo.writeCompressed(rootTag, nbtPath);

        StyleModuleEntry entry = new StyleModuleEntry(
                safeModule,
                category,
                relativeFile,
                weight,
                new BlockPos(normSizeX, sizeY, normSizeZ)
        );
        StyleDefinition updatedStyle = style.withModule(entry);
        saveStyleDefinition(server, updatedStyle);
        return entry;
    }

    private static Rotation detectNormalizationRotation(BoundingBox objectBox, BoundingBox parentBox) {
        double objCenterX = (objectBox.minX() + objectBox.maxX()) * 0.5;
        double objCenterZ = (objectBox.minZ() + objectBox.maxZ()) * 0.5;

        double distNorth = Math.abs(objCenterZ - parentBox.minZ());
        double distSouth = Math.abs(parentBox.maxZ() - objCenterZ);
        double distWest = Math.abs(objCenterX - parentBox.minX());
        double distEast = Math.abs(parentBox.maxX() - objCenterX);

        double min = Math.min(Math.min(distNorth, distSouth), Math.min(distWest, distEast));
        if (min == distEast) {
            return Rotation.COUNTERCLOCKWISE_90;
        } else if (min == distSouth) {
            return Rotation.CLOCKWISE_180;
        } else if (min == distWest) {
            return Rotation.CLOCKWISE_90;
        }
        return Rotation.NONE;
    }

    private static CompoundTag buildRotatedStructureTag(
            ServerLevel level,
            BoundingBox objectBox,
            Rotation rot,
            int sizeX, int sizeY, int sizeZ,
            int normSizeX, int normSizeZ
    ) {
        List<BlockState> palette = new ArrayList<>();
        ListTag blocksTag = new ListTag();

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = objectBox.minY(); y <= objectBox.maxY(); y++) {
            for (int z = objectBox.minZ(); z <= objectBox.maxZ(); z++) {
                for (int x = objectBox.minX(); x <= objectBox.maxX(); x++) {
                    cursor.set(x, y, z);
                    BlockState state = level.getBlockState(cursor);
                    if (state.is(Blocks.STRUCTURE_VOID)) {
                        continue;
                    }
                    int rx = x - objectBox.minX();
                    int ry = y - objectBox.minY();
                    int rz = z - objectBox.minZ();

                    int nx;
                    int nz;
                    switch (rot) {
                        case CLOCKWISE_90 -> {
                            nx = (sizeZ - 1) - rz;
                            nz = rx;
                        }
                        case CLOCKWISE_180 -> {
                            nx = (sizeX - 1) - rx;
                            nz = (sizeZ - 1) - rz;
                        }
                        case COUNTERCLOCKWISE_90 -> {
                            nx = rz;
                            nz = (sizeX - 1) - rx;
                        }
                        default -> {
                            nx = rx;
                            nz = rz;
                        }
                    }

                    BlockState rotatedState = state.rotate(rot);
                    int stateIdx = palette.indexOf(rotatedState);
                    if (stateIdx < 0) {
                        stateIdx = palette.size();
                        palette.add(rotatedState);
                    }

                    CompoundTag blockTag = new CompoundTag();
                    ListTag posList = new ListTag();
                    posList.add(IntTag.valueOf(nx));
                    posList.add(IntTag.valueOf(ry));
                    posList.add(IntTag.valueOf(nz));
                    blockTag.put("pos", posList);
                    blockTag.putInt("state", stateIdx);

                    BlockEntity be = level.getBlockEntity(cursor);
                    if (be != null) {
                        blockTag.put("nbt", be.saveWithFullMetadata(level.registryAccess()));
                    }
                    blocksTag.add(blockTag);
                }
            }
        }

        CompoundTag root = new CompoundTag();
        ListTag sizeTag = new ListTag();
        sizeTag.add(IntTag.valueOf(normSizeX));
        sizeTag.add(IntTag.valueOf(sizeY));
        sizeTag.add(IntTag.valueOf(normSizeZ));
        root.put("size", sizeTag);

        ListTag paletteTag = new ListTag();
        for (BlockState state : palette) {
            paletteTag.add(NbtUtils.writeBlockState(state));
        }
        root.put("palette", paletteTag);
        root.put("blocks", blocksTag);
        root.put("entities", new ListTag());
        root.putInt("DataVersion", SharedConstants.getCurrentVersion().getDataVersion().getVersion());
        return root;
    }

    public static List<String> feedPlaceIntoStyle(
            ServerLevel level,
            String styleName,
            String placeType,
            BoundingBox placeBox
    ) throws IOException {
        MinecraftServer server = level.getServer();
        String safeStyle = sanitizeName(styleName);
        String safePlaceType = placeType == null || placeType.isBlank() ? "building" : sanitizeName(placeType);

        StyleDefinition style = loadStyle(server, safeStyle)
                .orElseGet(() -> {
                    try {
                        return createStyle(server, safeStyle, 5);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });

        Map<ResourceLocation, Integer> primaryCounts = new LinkedHashMap<>();
        Map<ResourceLocation, Integer> floorCounts = new LinkedHashMap<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int y = placeBox.minY(); y <= placeBox.maxY(); y++) {
            for (int z = placeBox.minZ(); z <= placeBox.maxZ(); z++) {
                for (int x = placeBox.minX(); x <= placeBox.maxX(); x++) {
                    cursor.set(x, y, z);
                    BlockState state = level.getBlockState(cursor);
                    if (state.isAir() || state.is(Blocks.STRUCTURE_VOID)) {
                        continue;
                    }
                    if (!state.isCollisionShapeFullBlock(level, cursor)) {
                        continue;
                    }
                    ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
                    if (y == placeBox.minY()) {
                        floorCounts.merge(id, 1, Integer::sum);
                    } else {
                        primaryCounts.merge(id, 1, Integer::sum);
                    }
                }
            }
        }

        List<String> extractedPalettes = new ArrayList<>();
        if (safePlaceType.equals("road") || safePlaceType.equals("path") || safePlaceType.equals("street")) {
            Map<ResourceLocation, Integer> combined = new LinkedHashMap<>(floorCounts);
            primaryCounts.forEach((k, v) -> combined.merge(k, v, Integer::sum));
            if (!combined.isEmpty()) {
                style = savePaletteObject(server, style, BlockPalette.fromCounts("path_blocks", combined));
                extractedPalettes.add("path_blocks");
            }
        } else {
            if (!primaryCounts.isEmpty()) {
                style = savePaletteObject(server, style, BlockPalette.fromCounts("painting", primaryCounts));
                extractedPalettes.add("painting");
            }
            if (!floorCounts.isEmpty()) {
                style = savePaletteObject(server, style, BlockPalette.fromCounts("floor", floorCounts));
                extractedPalettes.add("floor");
            }
        }
        saveStyleDefinition(server, style);
        return extractedPalettes;
    }

    private static StyleDefinition savePaletteObject(
            MinecraftServer server,
            StyleDefinition style,
            BlockPalette palette
    ) throws IOException {
        String relativeFile = "palettes/" + palette.paletteType() + ".json";
        Path palettePath = getStyleDir(server, style.styleName()).resolve(relativeFile);
        Files.createDirectories(palettePath.getParent());

        DataResult<JsonElement> encoded = BlockPalette.CODEC.encodeStart(JsonOps.INSTANCE, palette);
        JsonElement json = encoded.getOrThrow(err -> new IOException("Failed to encode palette JSON: " + err));
        try (Writer writer = Files.newBufferedWriter(palettePath, StandardCharsets.UTF_8)) {
            GSON.toJson(json, writer);
        }
        return style.withPalette(palette.paletteType(), relativeFile);
    }

    public static Optional<StructureTemplate> loadModuleTemplate(
            ServerLevel level,
            String styleName,
            StyleModuleEntry entry
    ) {
        Path nbtPath = getStyleDir(level.getServer(), styleName).resolve(entry.file());
        if (!Files.exists(nbtPath)) {
            return Optional.empty();
        }
        try {
            CompoundTag tag = NbtIo.readCompressed(nbtPath, NbtAccounter.unlimitedHeap());
            StructureTemplate template = level.getStructureManager().readStructure(tag);
            return Optional.of(template);
        } catch (Exception e) {
            FwMain.LOGGER.error("Failed to load module template {} for style {}", entry.name(), styleName, e);
            return Optional.empty();
        }
    }

    public static BlockPalette savePaletteFromSelection(
            ServerLevel level,
            String styleName,
            String paletteType,
            BoundingBox selectionBox
    ) throws IOException {
        MinecraftServer server = level.getServer();
        String safeStyle = sanitizeName(styleName);
        String safePalette = sanitizeName(paletteType);

        StyleDefinition style = loadStyle(server, safeStyle)
                .orElseGet(() -> {
                    try {
                        return createStyle(server, safeStyle, 5);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });

        Map<ResourceLocation, Integer> counts = new LinkedHashMap<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = selectionBox.minY(); y <= selectionBox.maxY(); y++) {
            for (int z = selectionBox.minZ(); z <= selectionBox.maxZ(); z++) {
                for (int x = selectionBox.minX(); x <= selectionBox.maxX(); x++) {
                    cursor.set(x, y, z);
                    BlockState state = level.getBlockState(cursor);
                    if (!state.isAir() && !state.is(Blocks.STRUCTURE_VOID)) {
                        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
                        counts.merge(id, 1, Integer::sum);
                    }
                }
            }
        }

        BlockPalette palette = BlockPalette.fromCounts(safePalette, counts);
        String relativeFile = "palettes/" + safePalette + ".json";
        Path palettePath = getStyleDir(server, safeStyle).resolve(relativeFile);
        Files.createDirectories(palettePath.getParent());

        DataResult<JsonElement> encoded = BlockPalette.CODEC.encodeStart(JsonOps.INSTANCE, palette);
        JsonElement json = encoded.getOrThrow(err -> new IOException("Failed to encode palette JSON: " + err));
        try (Writer writer = Files.newBufferedWriter(palettePath, StandardCharsets.UTF_8)) {
            GSON.toJson(json, writer);
        }

        StyleDefinition updatedStyle = style.withPalette(safePalette, relativeFile);
        saveStyleDefinition(server, updatedStyle);
        return palette;
    }

    public static Optional<BlockPalette> loadPalette(MinecraftServer server, String styleName, String paletteType) {
        String safeStyle = sanitizeName(styleName);
        String safePalette = sanitizeName(paletteType);
        Path palettePath = getStyleDir(server, safeStyle).resolve("palettes").resolve(safePalette + ".json");
        if (!Files.exists(palettePath)) {
            return Optional.empty();
        }
        try (Reader reader = Files.newBufferedReader(palettePath, StandardCharsets.UTF_8)) {
            JsonElement element = JsonParser.parseReader(reader);
            return BlockPalette.CODEC.parse(JsonOps.INSTANCE, element)
                    .resultOrPartial(err -> FwMain.LOGGER.error("Error parsing palette {}: {}", paletteType, err));
        } catch (Exception e) {
            FwMain.LOGGER.error("Failed to load palette {} for style {}", paletteType, styleName, e);
            return Optional.empty();
        }
    }
}
