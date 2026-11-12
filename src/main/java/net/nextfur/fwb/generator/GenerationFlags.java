package net.nextfur.fwb.generator;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.nextfur.fwb.style.ModuleCategory;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Locale;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;

public record GenerationFlags(
        OptionalInt floors,
        OptionalLong seed,
        Set<Block> whitelist,
        Set<Block> blacklist,
        Set<ModuleCategory> excludedCategories,
        AlignMode alignMode,
        int width,
        boolean includeInterior
) {
    public enum AlignMode {
        GROUND,
        SELECTION,
        AUTO
    }

    public static final GenerationFlags DEFAULT = new GenerationFlags(
            OptionalInt.empty(),
            OptionalLong.empty(),
            Set.of(),
            Set.of(),
            Set.of(),
            AlignMode.AUTO,
            4,
            false
    );

    public boolean canOverwrite(BlockState existingState) {
        Block block = existingState.getBlock();
        if (!blacklist.isEmpty() && blacklist.contains(block)) {
            return false;
        }
        if (!whitelist.isEmpty() && !whitelist.contains(block)) {
            return false;
        }
        return true;
    }

    public boolean isExcluded(ModuleCategory category) {
        return excludedCategories.contains(category);
    }

    public boolean resolveSnapToGround(GenerationType type) {
        if (alignMode == AlignMode.GROUND) return true;
        if (alignMode == AlignMode.SELECTION) return false;
        return type == GenerationType.PATH || type == GenerationType.GRID;
    }

    public static GenerationFlags parse(String rawArgs) {
        if (rawArgs == null || rawArgs.isBlank()) {
            return DEFAULT;
        }

        OptionalInt floors = OptionalInt.empty();
        OptionalLong seed = OptionalLong.empty();
        Set<Block> whitelist = new HashSet<>();
        Set<Block> blacklist = new HashSet<>();
        Set<ModuleCategory> excluded = EnumSet.noneOf(ModuleCategory.class);
        AlignMode alignMode = AlignMode.AUTO;
        int width = 4;
        boolean includeInterior = false;

        String[] tokens = rawArgs.trim().split("\\s+");
        int i = 0;
        while (i < tokens.length) {
            String token = tokens[i].toLowerCase(Locale.ROOT);
            switch (token) {
                case "-floors", "-floor", "-h" -> {
                    if (i + 1 < tokens.length) {
                        try {
                            floors = OptionalInt.of(Math.max(1, Integer.parseInt(tokens[++i])));
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
                case "-seed", "-s" -> {
                    if (i + 1 < tokens.length) {
                        String val = tokens[++i];
                        try {
                            seed = OptionalLong.of(Long.parseLong(val));
                        } catch (NumberFormatException e) {
                            seed = OptionalLong.of(val.hashCode());
                        }
                    }
                }
                case "-whitelist" -> {
                    if (i + 1 < tokens.length) {
                        parseBlockList(tokens[++i], whitelist);
                    }
                }
                case "-blacklist" -> {
                    if (i + 1 < tokens.length) {
                        parseBlockList(tokens[++i], blacklist);
                    }
                }
                case "-exclude" -> {
                    if (i + 1 < tokens.length) {
                        String[] cats = tokens[++i].split(",");
                        for (String cat : cats) {
                            ModuleCategory parsed = ModuleCategory.byName(cat.trim());
                            if (parsed != null) {
                                excluded.add(parsed);
                            }
                        }
                    }
                }
                case "-align" -> {
                    if (i + 1 < tokens.length) {
                        String modeStr = tokens[++i].toLowerCase(Locale.ROOT);
                        if (modeStr.equals("ground")) {
                            alignMode = AlignMode.GROUND;
                        } else if (modeStr.equals("selection")) {
                            alignMode = AlignMode.SELECTION;
                        }
                    }
                }
                case "-width", "-w" -> {
                    if (i + 1 < tokens.length) {
                        try {
                            width = Math.max(1, Integer.parseInt(tokens[++i]));
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
                case "-interior" -> includeInterior = true;
                default -> {
                }
            }
            i++;
        }

        return new GenerationFlags(
                floors,
                seed,
                Collections.unmodifiableSet(whitelist),
                Collections.unmodifiableSet(blacklist),
                Collections.unmodifiableSet(excluded),
                alignMode,
                width,
                includeInterior
        );
    }

    private static void parseBlockList(String csv, Set<Block> target) {
        String[] parts = csv.split(",");
        for (String part : parts) {
            String trimmed = part.trim().toLowerCase(Locale.ROOT);
            if (trimmed.isEmpty()) continue;
            ResourceLocation rl = trimmed.contains(":")
                    ? ResourceLocation.tryParse(trimmed)
                    : ResourceLocation.withDefaultNamespace(trimmed);
            if (rl != null && BuiltInRegistries.BLOCK.containsKey(rl)) {
                target.add(BuiltInRegistries.BLOCK.get(rl));
            }
        }
    }
}
