package net.nextfur.fwb.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.nextfur.fwb.data.PlayerSelectionData;
import net.nextfur.fwb.style.ModuleCategory;
import net.nextfur.fwb.util.SelectionService;

public class FurMapCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("furmap")
                .requires(source -> source.hasPermission(2))
                // /furmap start <style_name> [place_type]
                .then(Commands.literal("start")
                        .then(Commands.argument("style_name", StringArgumentType.word())
                                .suggests(FurStyleCommand.STYLE_SUGGESTIONS)
                                .executes(ctx -> FurStyleCommand.executeFeedPlace(
                                        ctx,
                                        StringArgumentType.getString(ctx, "style_name"),
                                        "building"
                                ))
                                .then(Commands.argument("place_type", StringArgumentType.word())
                                        .suggests(FurStyleCommand.PLACE_TYPE_SUGGESTIONS)
                                        .executes(ctx -> FurStyleCommand.executeFeedPlace(
                                                ctx,
                                                StringArgumentType.getString(ctx, "style_name"),
                                                StringArgumentType.getString(ctx, "place_type")
                                        ))
                                )
                        )
                )
                // /furmap type <category>
                .then(Commands.literal("type")
                        .then(Commands.argument("category", StringArgumentType.word())
                                .suggests(FurStyleCommand.CATEGORY_SUGGESTIONS)
                                .executes(ctx -> executeSetType(ctx, StringArgumentType.getString(ctx, "category")))
                        )
                )
                // /furmap save [category] [module_name] [weight]
                .then(Commands.literal("save")
                        .executes(ctx -> executeSave(ctx, null, null, 1.0))
                        .then(Commands.argument("category", StringArgumentType.word())
                                .suggests(FurStyleCommand.CATEGORY_SUGGESTIONS)
                                .executes(ctx -> executeSave(
                                        ctx,
                                        StringArgumentType.getString(ctx, "category"),
                                        null, 1.0
                                ))
                                .then(Commands.argument("module_name", StringArgumentType.word())
                                        .executes(ctx -> executeSave(
                                                ctx,
                                                StringArgumentType.getString(ctx, "category"),
                                                StringArgumentType.getString(ctx, "module_name"),
                                                1.0
                                        ))
                                        .then(Commands.argument("weight", DoubleArgumentType.doubleArg(0.01, 1000.0))
                                                .executes(ctx -> executeSave(
                                                        ctx,
                                                        StringArgumentType.getString(ctx, "category"),
                                                        StringArgumentType.getString(ctx, "module_name"),
                                                        DoubleArgumentType.getDouble(ctx, "weight")
                                                ))
                                        )
                                )
                        )
                )
                // /furmap clear
                .then(Commands.literal("clear")
                        .executes(FurMapCommand::executeClear)
                )
        );
    }

    private static int executeSetType(CommandContext<CommandSourceStack> ctx, String categoryStr) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(Component.literal("Only players can set select category."));
            return 0;
        }
        ModuleCategory category = ModuleCategory.byName(categoryStr);
        if (category == null) {
            ctx.getSource().sendFailure(Component.translatable(
                    "message.furbuild.style.invalid_category",
                    categoryStr,
                    String.join(", ", ModuleCategory.allNames())
            ));
            return 0;
        }
        SelectionService.setSelectCategory(player, category);
        return 1;
    }

    private static int executeSave(
            CommandContext<CommandSourceStack> ctx,
            String categoryStr,
            String moduleName,
            double weight
    ) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(Component.literal("Only players can map objects."));
            return 0;
        }
        PlayerSelectionData selection = SelectionService.getSelection(player);
        String styleName = selection.activeMappingStyle();
        if (styleName == null || styleName.isBlank()) {
            ctx.getSource().sendFailure(Component.translatable("message.furbuild.map.no_session"));
            return 0;
        }
        return FurStyleCommand.executeMapObject(ctx, styleName, categoryStr, moduleName, weight);
    }

    private static int executeClear(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            return 0;
        }
        PlayerSelectionData current = SelectionService.getSelection(player);
        SelectionService.setSelection(player, current.clearMappingSelection());
        ctx.getSource().sendSuccess(() -> Component.translatable("message.furbuild.map.cleared")
                .withStyle(ChatFormatting.YELLOW), false);
        return 1;
    }
}
