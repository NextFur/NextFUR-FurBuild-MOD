package net.nextfur.fwb.style;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
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
