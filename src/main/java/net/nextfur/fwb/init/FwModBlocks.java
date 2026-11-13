package net.nextfur.fwb.init;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.nextfur.fwb.FwMain;
import net.nextfur.fwb.blocks.PoleBlock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class FwModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(FwMain.MODID);

    public record PaletteBlockSet(
            FwPaletteDefinitions.BlockSetSpec spec,
            DeferredBlock<Block> block,
            DeferredBlock<SlabBlock> slab,
            DeferredBlock<StairBlock> stairs,
            DeferredBlock<PoleBlock> pole
    ) {}

    public static final List<PaletteBlockSet> PALETTE_SETS;
    public static final Map<String, PaletteBlockSet> BY_ID;

    static {
        List<PaletteBlockSet> sets = new ArrayList<>(FwPaletteDefinitions.BLOCK_SETS.size());
        Map<String, PaletteBlockSet> byId = new LinkedHashMap<>();

        for (FwPaletteDefinitions.BlockSetSpec spec : FwPaletteDefinitions.BLOCK_SETS) {
            String id = spec.id();
            DeferredBlock<Block> fullBlock = BLOCKS.register(id, () -> new Block(createProperties(spec)));
            DeferredBlock<SlabBlock> slab = BLOCKS.register(id + "_slab", () -> new SlabBlock(createProperties(spec)));
            DeferredBlock<StairBlock> stairs = BLOCKS.register(id + "_stairs", () -> new StairBlock(fullBlock.get().defaultBlockState(), createProperties(spec)));
            DeferredBlock<PoleBlock> pole = BLOCKS.register(id + "_pole", () -> new PoleBlock(createProperties(spec).noOcclusion()));

            PaletteBlockSet set = new PaletteBlockSet(spec, fullBlock, slab, stairs, pole);
            sets.add(set);
            byId.put(id, set);
        }

        PALETTE_SETS = Collections.unmodifiableList(sets);
        BY_ID = Collections.unmodifiableMap(byId);
    }

    private static BlockBehaviour.Properties createProperties(FwPaletteDefinitions.BlockSetSpec spec) {
        if (spec.family().isWood()) {
            return BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .instrument(NoteBlockInstrument.BASS)
                    .strength(2.0F, 3.0F)
                    .sound(SoundType.WOOD)
                    .ignitedByLava();
        }
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.STONE)
                .instrument(NoteBlockInstrument.BASEDRUM)
                .requiresCorrectToolForDrops()
                .strength(1.5F, 6.0F)
                .sound(SoundType.STONE);
    }
}
