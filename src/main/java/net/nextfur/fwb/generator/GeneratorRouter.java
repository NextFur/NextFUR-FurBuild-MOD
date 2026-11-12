package net.nextfur.fwb.generator;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.nextfur.fwb.data.PlayerSelectionData;
import net.nextfur.fwb.generator.engines.BuildingGeneratorEngine;
import net.nextfur.fwb.generator.engines.DungeonGeneratorEngine;
import net.nextfur.fwb.generator.engines.GridGeneratorEngine;
import net.nextfur.fwb.generator.engines.InteriorGeneratorEngine;
import net.nextfur.fwb.generator.engines.PathGeneratorEngine;
import net.nextfur.fwb.style.StyleDefinition;
import net.nextfur.fwb.style.StyleManager;
import net.nextfur.fwb.util.SelectionService;

import java.io.IOException;
import java.util.List;

public class GeneratorRouter {

    public static int executeGeneration(
            ServerPlayer player,
            GenerationType type,
            String styleName,
            String rawFlags
    ) {
        ServerLevel level = player.serverLevel();
        PlayerSelectionData selection = SelectionService.getSelection(player);
        GenerationFlags flags = GenerationFlags.parse(rawFlags);

        StyleDefinition style = StyleManager.loadStyle(level.getServer(), styleName)
                .orElseGet(() -> {
                    try {
                        return StyleManager.createStyle(level.getServer(), styleName, 5);
                    } catch (IOException e) {
                        return StyleDefinition.createNew(styleName, 5);
                    }
                });

        long seed = flags.seed().orElseGet(() -> level.getRandom().nextLong());
        RandomSource random = RandomSource.create(seed);

        List<BlockPlacementQueue.PlannedBlock> plannedBlocks = switch (type) {
            case PATH -> PathGeneratorEngine.generate(player, level, selection, style, flags, random);
            case GRID -> GridGeneratorEngine.generate(player, level, selection, style, flags, random);
            case BUILDING -> BuildingGeneratorEngine.generate(player, level, selection, style, flags, random);
            case INTERIOR -> InteriorGeneratorEngine.generate(player, level, selection, style, flags, random);
            case DUNGEON -> DungeonGeneratorEngine.generate(player, level, selection, style, flags, random);
        };

        if (plannedBlocks.isEmpty()) {
            player.sendSystemMessage(
                    Component.translatable("message.furbuild.generate.no_selection", type.getId())
                            .withStyle(ChatFormatting.RED)
            );
            return 0;
        }

        String jobDescription = type.getId() + " (" + style.styleName() + ", seed=" + seed + ")";
        BlockPlacementQueue.enqueueJob(player, level, jobDescription, flags, plannedBlocks);
        return 1;
    }
}
