package net.nextfur.fwb.style;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record BlockPalette(
        String paletteType,
        Map<ResourceLocation, Double> weights
) {
    public static final Codec<BlockPalette> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("palette_type").forGetter(BlockPalette::paletteType),
            Codec.unboundedMap(ResourceLocation.CODEC, Codec.DOUBLE).fieldOf("weights").forGetter(BlockPalette::weights)
    ).apply(instance, BlockPalette::new));

    public static BlockPalette fromCounts(String paletteType, Map<ResourceLocation, Integer> counts) {
        int total = counts.values().stream().mapToInt(Integer::intValue).sum();
        Map<ResourceLocation, Double> normalized = new LinkedHashMap<>();
        if (total > 0) {
            for (Map.Entry<ResourceLocation, Integer> entry : counts.entrySet()) {
                double ratio = Math.round((entry.getValue() * 10000.0) / total) / 10000.0;
                normalized.put(entry.getKey(), ratio);
            }
        }
        return new BlockPalette(paletteType, normalized);
    }

    public BlockState sample(RandomSource random, BlockState fallback) {
        if (weights == null || weights.isEmpty()) {
            return fallback;
        }
        double totalWeight = 0.0;
        for (double w : weights.values()) {
            if (w > 0) totalWeight += w;
        }
        if (totalWeight <= 0.0) {
            return fallback;
        }
        double roll = random.nextDouble() * totalWeight;
        double cumulative = 0.0;
        for (Map.Entry<ResourceLocation, Double> entry : weights.entrySet()) {
            double w = entry.getValue();
            if (w <= 0) continue;
            cumulative += w;
            if (roll <= cumulative) {
                Block block = BuiltInRegistries.BLOCK.get(entry.getKey());
                if (block != Blocks.AIR) {
                    return block.defaultBlockState();
                }
            }
        }
        ResourceLocation firstKey = weights.keySet().iterator().next();
        Block firstBlock = BuiltInRegistries.BLOCK.get(firstKey);
        return firstBlock != Blocks.AIR ? firstBlock.defaultBlockState() : fallback;
    }

    public List<String> formatPercentages() {
        List<String> list = new ArrayList<>();
        double total = weights.values().stream().mapToDouble(Double::doubleValue).sum();
        if (total <= 0) total = 1.0;
        for (Map.Entry<ResourceLocation, Double> e : weights.entrySet()) {
            double pct = (e.getValue() / total) * 100.0;
            list.add(String.format("%.1f%% %s", pct, e.getKey()));
        }
        return list;
    }
}
