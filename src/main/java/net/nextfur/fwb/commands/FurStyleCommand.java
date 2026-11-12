package net.nextfur.fwb.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.nextfur.fwb.data.PlayerSelectionData;
import net.nextfur.fwb.style.BlockPalette;
import net.nextfur.fwb.style.ModuleCategory;
import net.nextfur.fwb.style.StyleDefinition;
import net.nextfur.fwb.style.StyleManager;
import net.nextfur.fwb.style.StyleModuleEntry;
import net.nextfur.fwb.util.SelectionService;

import java.util.List;
import java.util.Optional;

public class FurStyleCommand {

    public static final SuggestionProvider<CommandSourceStack> STYLE_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(StyleManager.listStyles(context.getSource().getServer()), builder);

    public static final SuggestionProvider<CommandSourceStack> CATEGORY_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(ModuleCategory.allNames(), builder);

    public static final SuggestionProvider<CommandSourceStack> PALETTE_TYPE_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(List.of("path_blocks", "painting", "floor"), builder);

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("furstyle")
                .requires(source -> source.hasPermission(2))
                // 1. /furstyle create <style_name> [grid_size]
                .then(Commands.literal("create")
                        .then(Commands.argument("style_name", StringArgumentType.word())
                                .executes(ctx -> executeCreate(ctx, StringArgumentType.getString(ctx, "style_name"), 5))
                                .then(Commands.argument("grid_size", IntegerArgumentType.integer(1, 64))
                                        .executes(ctx -> executeCreate(
                                                ctx,
                                                StringArgumentType.getString(ctx, "style_name"),
                                                IntegerArgumentType.getInteger(ctx, "grid_size")
                                        ))
                                )
                        )
                )
                // 2. /furstyle add <style_name> <category> <module_name> [weight]
                .then(Commands.literal("add")
                        .then(Commands.argument("style_name", StringArgumentType.word())
                                .suggests(STYLE_SUGGESTIONS)
                                .then(Commands.argument("category", StringArgumentType.word())
                                        .suggests(CATEGORY_SUGGESTIONS)
                                        .then(Commands.argument("module_name", StringArgumentType.word())
                                                .executes(ctx -> executeAddModule(
                                                        ctx,
                                                        StringArgumentType.getString(ctx, "style_name"),
                                                        StringArgumentType.getString(ctx, "category"),
                                                        StringArgumentType.getString(ctx, "module_name"),
                                                        1.0
                                                ))
                                                .then(Commands.argument("weight", DoubleArgumentType.doubleArg(0.01, 1000.0))
                                                        .executes(ctx -> executeAddModule(
                                                                ctx,
                                                                StringArgumentType.getString(ctx, "style_name"),
                                                                StringArgumentType.getString(ctx, "category"),
                                                                StringArgumentType.getString(ctx, "module_name"),
                                                                DoubleArgumentType.getDouble(ctx, "weight")
                                                        ))
                                                )
                                        )
                                )
                        )
                )
                // 3. /furstyle addpalette <style_name> <palette_type>
                .then(Commands.literal("addpalette")
                        .then(Commands.argument("style_name", StringArgumentType.word())
                                .suggests(STYLE_SUGGESTIONS)
                                .then(Commands.argument("palette_type", StringArgumentType.word())
                                        .suggests(PALETTE_TYPE_SUGGESTIONS)
                                        .executes(ctx -> executeAddPalette(
                                                ctx,
                                                StringArgumentType.getString(ctx, "style_name"),
                                                StringArgumentType.getString(ctx, "palette_type")
                                        ))
                                )
                        )
                )
                // 4. /furstyle list
                .then(Commands.literal("list")
                        .executes(FurStyleCommand::executeList)
                )
                // 5. /furstyle info <style_name>
                .then(Commands.literal("info")
                        .then(Commands.argument("style_name", StringArgumentType.word())
                                .suggests(STYLE_SUGGESTIONS)
                                .executes(ctx -> executeInfo(ctx, StringArgumentType.getString(ctx, "style_name")))
                        )
                )
                // 6. /furstyle presets
                .then(Commands.literal("presets")
                        .executes(ctx -> FurBuildHelpCommand.executeInstallPresets(ctx, true))
                )
        );
    }

    private static int executeCreate(CommandContext<CommandSourceStack> ctx, String styleName, int gridSize) {
        try {
            StyleDefinition def = StyleManager.createStyle(ctx.getSource().getServer(), styleName, gridSize);
            ctx.getSource().sendSuccess(() -> Component.translatable(
                    "message.furbuild.style.created",
                    Component.literal(def.styleName()).withStyle(ChatFormatting.AQUA),
                    def.gridSize(),
                    def.gridSize()
            ).withStyle(ChatFormatting.GREEN), true);
            ctx.getSource().sendSuccess(() -> Component.translatable(
                    "message.furbuild.style.north_reminder"
            ).withStyle(ChatFormatting.YELLOW), false);
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.translatable("message.furbuild.style.error", e.getMessage()));
            return 0;
        }
    }

    private static int executeAddModule(
            CommandContext<CommandSourceStack> ctx,
            String styleName,
            String categoryStr,
            String moduleName,
            double weight
    ) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(Component.literal("Only players can save selections."));
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

        PlayerSelectionData selection = SelectionService.getSelection(player);
        if (!selection.hasCuboidSelection()) {
            ctx.getSource().sendFailure(Component.translatable("message.furbuild.style.need_cuboid"));
            return 0;
        }

        BoundingBox box = BoundingBox.fromCorners(selection.posA().get(), selection.posB().get());
        try {
            StyleModuleEntry entry = StyleManager.saveModule(
                    player.serverLevel(),
                    styleName,
                    category,
                    moduleName,
                    weight,
                    box
            );

            ctx.getSource().sendSuccess(() -> Component.translatable(
                    "message.furbuild.style.module_saved",
                    Component.literal(entry.name()).withStyle(ChatFormatting.AQUA),
                    Component.literal(category.getSerializedName()).withStyle(ChatFormatting.GOLD),
                    Component.literal(styleName).withStyle(ChatFormatting.GREEN),
                    weight
            ).withStyle(ChatFormatting.GREEN), true);

            // Always remind the player to build modules facing NORTH for proper rotation!
            ctx.getSource().sendSuccess(() -> Component.translatable(
                    "message.furbuild.style.north_reminder"
            ).withStyle(ChatFormatting.YELLOW), false);
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.translatable("message.furbuild.style.error", e.getMessage()));
            return 0;
        }
    }

    private static int executeAddPalette(
            CommandContext<CommandSourceStack> ctx,
            String styleName,
            String paletteType
    ) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(Component.literal("Only players can scan block palettes."));
            return 0;
        }

        PlayerSelectionData selection = SelectionService.getSelection(player);
        if (!selection.hasCuboidSelection()) {
            ctx.getSource().sendFailure(Component.translatable("message.furbuild.style.need_cuboid"));
            return 0;
        }

        BoundingBox box = BoundingBox.fromCorners(selection.posA().get(), selection.posB().get());
        try {
            BlockPalette palette = StyleManager.savePaletteFromSelection(
                    player.serverLevel(),
                    styleName,
                    paletteType,
                    box
            );

            if (palette.weights().isEmpty()) {
                ctx.getSource().sendFailure(Component.translatable("message.furbuild.style.palette_empty"));
                return 0;
            }

            String summary = String.join(", ", palette.formatPercentages());
            ctx.getSource().sendSuccess(() -> Component.translatable(
                    "message.furbuild.style.palette_saved",
                    Component.literal(palette.paletteType()).withStyle(ChatFormatting.GOLD),
                    Component.literal(styleName).withStyle(ChatFormatting.AQUA),
                    Component.literal(summary).withStyle(ChatFormatting.WHITE)
            ).withStyle(ChatFormatting.GREEN), true);
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.translatable("message.furbuild.style.error", e.getMessage()));
            return 0;
        }
    }

    private static int executeList(CommandContext<CommandSourceStack> ctx) {
        List<String> styles = StyleManager.listStyles(ctx.getSource().getServer());
        if (styles.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.translatable("message.furbuild.style.list_empty")
                    .withStyle(ChatFormatting.YELLOW), false);
        } else {
            ctx.getSource().sendSuccess(() -> Component.translatable(
                    "message.furbuild.style.list",
                    Component.literal(String.join(", ", styles)).withStyle(ChatFormatting.AQUA)
            ).withStyle(ChatFormatting.GOLD), false);
        }
        return 1;
    }

    private static int executeInfo(CommandContext<CommandSourceStack> ctx, String styleName) {
        Optional<StyleDefinition> opt = StyleManager.loadStyle(ctx.getSource().getServer(), styleName);
        if (opt.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("message.furbuild.style.not_found", styleName));
            return 0;
        }
        StyleDefinition style = opt.get();
        ctx.getSource().sendSuccess(() -> Component.translatable(
                "message.furbuild.style.info",
                Component.literal(style.styleName()).withStyle(ChatFormatting.AQUA),
                style.gridSize(),
                style.moduleHeight(),
                style.modules().size(),
                style.palettes().size()
        ).withStyle(ChatFormatting.GOLD), false);
        return 1;
    }
}
