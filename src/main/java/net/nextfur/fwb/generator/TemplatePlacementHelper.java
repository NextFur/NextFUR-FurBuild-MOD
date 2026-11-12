package net.nextfur.fwb.generator;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.nextfur.fwb.FwMain;
import net.nextfur.fwb.style.BlockPalette;
import net.nextfur.fwb.style.StyleManager;
import net.nextfur.fwb.style.StyleModuleEntry;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class TemplatePlacementHelper {

    public static boolean placeModuleIntoBuilder(
            ServerLevel level,
            String styleName,
            StyleModuleEntry moduleEntry,
            BlockPos origin,
            Rotation rotation,
            Mirror mirror,
            Optional<BlockPalette> paintingPalette,
            RandomSource random,
            BlockPlacementQueue.Builder builder
    ) {
        Path nbtPath = StyleManager.getStyleDir(level.getServer(), styleName).resolve(moduleEntry.file());
        if (!Files.exists(nbtPath)) {
            return false;
        }

        try {
            CompoundTag root = NbtIo.readCompressed(nbtPath, NbtAccounter.unlimitedHeap());
            HolderGetter<Block> blockLookup = level.holderLookup(Registries.BLOCK);

            ListTag paletteTag = root.getList("palette", Tag.TAG_COMPOUND);
            if (paletteTag.isEmpty()) {
                ListTag palettes = root.getList("palettes", Tag.TAG_LIST);
                if (!palettes.isEmpty()) {
                    paletteTag = palettes.getList(0);
                }
            }

            List<BlockState> statePalette = new ArrayList<>(paletteTag.size());
            for (int i = 0; i < paletteTag.size(); i++) {
                statePalette.add(NbtUtils.readBlockState(blockLookup, paletteTag.getCompound(i)));
            }

            ListTag blocksTag = root.getList("blocks", Tag.TAG_COMPOUND);
            for (int i = 0; i < blocksTag.size(); i++) {
                CompoundTag blockEntry = blocksTag.getCompound(i);
                ListTag posTag = blockEntry.getList("pos", Tag.TAG_INT);
                if (posTag.size() < 3) continue;

                BlockPos relPos = new BlockPos(posTag.getInt(0), posTag.getInt(1), posTag.getInt(2));
                int stateIdx = blockEntry.getInt("state");
                if (stateIdx < 0 || stateIdx >= statePalette.size()) continue;

                BlockState rawState = statePalette.get(stateIdx);
                if (rawState.is(Blocks.STRUCTURE_VOID)) {
                    continue;
                }

                BlockPos transformedRel = StructureTemplate.calculateRelativePosition(
                        new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings()
                                .setMirror(mirror)
                                .setRotation(rotation),
                        relPos
                );
                BlockPos targetPos = origin.offset(transformedRel);

                BlockState transformedState = rawState.mirror(mirror).rotate(rotation);
                if (paintingPalette.isPresent() && isPaintablePlaceholder(transformedState)) {
                    transformedState = paintingPalette.get().sample(random, transformedState);
                }

                CompoundTag beNbt = blockEntry.contains("nbt", Tag.TAG_COMPOUND)
                        ? blockEntry.getCompound("nbt").copy()
                        : null;

                builder.setBlock(targetPos, transformedState, beNbt);
            }

            return true;
        } catch (Exception e) {
            FwMain.LOGGER.error("Failed to place module {} from style {}", moduleEntry.name(), styleName, e);
            return false;
        }
    }

    /**
     * Checks whether a block is a placeholder/primary wall block eligible for the secondary Painting Pass.
     */
    public static boolean isPaintablePlaceholder(BlockState state) {
        return state.is(BlockTags.WOOL)
                || state.is(Blocks.STONE_BRICKS)
                || state.is(Blocks.COBBLESTONE)
                || state.is(Blocks.STONE);
    }
}
