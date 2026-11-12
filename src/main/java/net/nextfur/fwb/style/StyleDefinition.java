package net.nextfur.fwb.style;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public record StyleDefinition(
        String styleName,
        int gridSize,
        int moduleHeight,
        List<StyleModuleEntry> modules,
        Map<String, String> palettes
) {
    public static final Codec<StyleDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("style_name").forGetter(StyleDefinition::styleName),
            Codec.INT.optionalFieldOf("grid_size", 5).forGetter(StyleDefinition::gridSize),
            Codec.INT.optionalFieldOf("module_height", 4).forGetter(StyleDefinition::moduleHeight),
            StyleModuleEntry.CODEC.listOf().optionalFieldOf("modules", List.of()).forGetter(StyleDefinition::modules),
            Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("palettes", Map.of()).forGetter(StyleDefinition::palettes)
    ).apply(instance, StyleDefinition::new));

    public static StyleDefinition createNew(String styleName, int gridSize) {
        return new StyleDefinition(styleName, Math.max(1, gridSize), 4, List.of(), Map.of());
    }

    public StyleDefinition withModule(StyleModuleEntry newEntry) {
        List<StyleModuleEntry> updated = new ArrayList<>();
        for (StyleModuleEntry existing : modules) {
            if (!(existing.category() == newEntry.category() && existing.name().equalsIgnoreCase(newEntry.name()))) {
                updated.add(existing);
            }
        }
        updated.add(newEntry);
        int updatedHeight = this.moduleHeight;
        if ((newEntry.category() == ModuleCategory.WALL || newEntry.category() == ModuleCategory.WINDOW || newEntry.category() == ModuleCategory.DOOR)
                && newEntry.size().getY() > 0) {
            updatedHeight = newEntry.size().getY();
        }
        return new StyleDefinition(styleName, gridSize, updatedHeight, List.copyOf(updated), palettes);
    }

    public StyleDefinition withPalette(String paletteType, String relativePath) {
        Map<String, String> updated = new LinkedHashMap<>(palettes);
        updated.put(paletteType, relativePath);
        return new StyleDefinition(styleName, gridSize, moduleHeight, modules, Map.copyOf(updated));
    }

    public List<StyleModuleEntry> getModulesByCategory(ModuleCategory category) {
        return modules.stream().filter(m -> m.category() == category).toList();
    }

    public Optional<StyleModuleEntry> pickWeightedModule(ModuleCategory category, RandomSource random) {
        List<StyleModuleEntry> candidates = getModulesByCategory(category);
        if (candidates.isEmpty()) return Optional.empty();
        double totalWeight = candidates.stream().mapToDouble(m -> Math.max(0.01, m.weight())).sum();
        double roll = random.nextDouble() * totalWeight;
        double acc = 0.0;
        for (StyleModuleEntry entry : candidates) {
            acc += Math.max(0.01, entry.weight());
            if (roll <= acc) {
                return Optional.of(entry);
            }
        }
        return Optional.of(candidates.get(0));
    }
}
