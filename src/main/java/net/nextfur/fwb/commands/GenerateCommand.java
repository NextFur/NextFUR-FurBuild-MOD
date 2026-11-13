package net.nextfur.fwb.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.nextfur.fwb.generator.GenerationType;
import net.nextfur.fwb.generator.GeneratorRouter;

import java.util.List;

public class GenerateCommand {

    public static final SuggestionProvider<CommandSourceStack> TYPE_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(GenerationType.allNames(), builder);

    public static final SuggestionProvider<CommandSourceStack> FLAG_SUGGESTIONS = (context, builder) -> {
        String remaining = builder.getRemaining();
        int lastSpace = remaining.lastIndexOf(' ');
        var offsetBuilder = lastSpace >= 0 ? builder.createOffset(builder.getStart() + lastSpace + 1) : builder;
        return SharedSuggestionProvider.suggest(
                List.of("-floors", "-seed", "-whitelist", "-blacklist", "-exclude", "-align", "-width", "-interior",
                        "-winfreq", "-road", "-btype", "-distance"),
                offsetBuilder
        );
    };

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("generate")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("type", StringArgumentType.word())
                        .suggests(TYPE_SUGGESTIONS)
                        .then(Commands.argument("style", StringArgumentType.word())
                                .suggests(FurStyleCommand.STYLE_SUGGESTIONS)
                                .executes(ctx -> execute(
                                        ctx,
                                        StringArgumentType.getString(ctx, "type"),
                                        StringArgumentType.getString(ctx, "style"),
                                        ""
                                ))
                                .then(Commands.argument("args", StringArgumentType.greedyString())
                                        .suggests(FLAG_SUGGESTIONS)
                                        .executes(ctx -> execute(
                                                ctx,
                                                StringArgumentType.getString(ctx, "type"),
                                                StringArgumentType.getString(ctx, "style"),
                                                StringArgumentType.getString(ctx, "args")
                                        ))
                                )
                        )
                )
        );
    }

    private static int execute(
            CommandContext<CommandSourceStack> ctx,
            String typeStr,
            String styleName,
            String rawArgs
    ) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(Component.literal("Only players can run /generate."));
            return 0;
        }

        GenerationType type = GenerationType.byName(typeStr);
        if (type == null) {
            ctx.getSource().sendFailure(Component.translatable(
                    "message.furbuild.generate.invalid_type",
                    typeStr,
                    String.join(", ", GenerationType.allNames())
            ));
            return 0;
        }

        return GeneratorRouter.executeGeneration(player, type, styleName, rawArgs);
    }
}
