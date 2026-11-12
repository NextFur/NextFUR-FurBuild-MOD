package net.nextfur.fwb.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.nextfur.fwb.data.PlayerSelectionData;
import net.nextfur.fwb.data.SelectionMode;
import net.nextfur.fwb.util.SelectionService;

import java.util.List;

public class FurWandCommand {

    public static final SuggestionProvider<CommandSourceStack> MODE_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(List.of("cuboid", "polygon", "cylinder", "circle", "spline", "polyline"), builder);

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("furwand")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("mode")
                        .then(Commands.argument("mode", StringArgumentType.word())
                                .suggests(MODE_SUGGESTIONS)
                                .executes(ctx -> executeSetMode(ctx, StringArgumentType.getString(ctx, "mode")))
                        )
                )
                .then(Commands.literal("clear")
                        .executes(FurWandCommand::executeClear)
                )
        );
    }

    private static int executeSetMode(CommandContext<CommandSourceStack> ctx, String modeName) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(Component.literal("Only players can change wand modes."));
            return 0;
        }

        SelectionMode mode = SelectionMode.byName(modeName);
        if (mode == null) {
            ctx.getSource().sendFailure(Component.translatable("message.furbuild.wand.invalid_mode", modeName));
            return 0;
        }

        SelectionService.setWandMode(player, mode);
        return 1;
    }

    private static int executeClear(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            return 0;
        }
        PlayerSelectionData current = SelectionService.getSelection(player);
        SelectionService.setSelection(player, current.clearSelection());
        ctx.getSource().sendSuccess(() -> Component.translatable("message.furbuild.wand.cleared")
                .withStyle(ChatFormatting.YELLOW), false);
        return 1;
    }
}
