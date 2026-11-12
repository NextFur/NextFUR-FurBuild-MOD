package net.nextfur.fwb.style;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;

public record StyleModuleEntry(
        String name,
        ModuleCategory category,
        String file,
        double weight,
        BlockPos size
) {
    public static final Codec<StyleModuleEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("name").forGetter(StyleModuleEntry::name),
            ModuleCategory.CODEC.fieldOf("category").forGetter(StyleModuleEntry::category),
            Codec.STRING.fieldOf("file").forGetter(StyleModuleEntry::file),
            Codec.DOUBLE.optionalFieldOf("weight", 1.0).forGetter(StyleModuleEntry::weight),
            BlockPos.CODEC.fieldOf("size").forGetter(StyleModuleEntry::size)
    ).apply(instance, StyleModuleEntry::new));
}
