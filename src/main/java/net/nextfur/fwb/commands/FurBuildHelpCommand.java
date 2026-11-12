package net.nextfur.fwb.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.nextfur.fwb.style.DefaultStylePresets;
import net.nextfur.fwb.style.ModuleCategory;

import java.util.List;
import java.util.Locale;

public class FurBuildHelpCommand {

    public static final List<String> HELP_TOPICS = List.of(
            "overview", "wands", "style", "generate", "flags", "presets", "undo"
    );

    public static final SuggestionProvider<CommandSourceStack> TOPIC_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(HELP_TOPICS, builder);

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // /furbuild [help [topic]] | /furbuild presets [force]
        dispatcher.register(Commands.literal("furbuild")
                .executes(ctx -> sendHelpTopic(ctx.getSource(), "overview"))
                .then(Commands.literal("help")
                        .executes(ctx -> sendHelpTopic(ctx.getSource(), "overview"))
                        .then(Commands.argument("topic", StringArgumentType.word())
                                .suggests(TOPIC_SUGGESTIONS)
                                .executes(ctx -> sendHelpTopic(ctx.getSource(), StringArgumentType.getString(ctx, "topic")))
                        )
                )
                .then(Commands.literal("presets")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> executeInstallPresets(ctx, true))
                        .then(Commands.argument("force", BoolArgumentType.bool())
                                .executes(ctx -> executeInstallPresets(ctx, BoolArgumentType.getBool(ctx, "force")))
                        )
                )
        );

        // Alias: /furhelp [topic]
        dispatcher.register(Commands.literal("furhelp")
                .executes(ctx -> sendHelpTopic(ctx.getSource(), "overview"))
                .then(Commands.argument("topic", StringArgumentType.word())
                        .suggests(TOPIC_SUGGESTIONS)
                        .executes(ctx -> sendHelpTopic(ctx.getSource(), StringArgumentType.getString(ctx, "topic")))
                )
        );
    }

    public static int executeInstallPresets(CommandContext<CommandSourceStack> ctx, boolean force) {
        int count = DefaultStylePresets.installPresets(ctx.getSource().getServer(), force);
        ctx.getSource().sendSuccess(() -> Component.translatable(
                "message.furbuild.presets.installed",
                count,
                Component.literal(String.join(", ", DefaultStylePresets.PRESET_NAMES)).withStyle(ChatFormatting.AQUA)
        ).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    public static int sendHelpTopic(CommandSourceStack source, String rawTopic) {
        String topic = rawTopic == null ? "overview" : rawTopic.toLowerCase(Locale.ROOT);
        switch (topic) {
            case "wands", "wand", "furwand" -> sendWandsHelp(source);
            case "style", "styles", "furstyle" -> sendStyleHelp(source);
            case "generate", "gen", "engines" -> sendGenerateHelp(source);
            case "flags", "args", "options" -> sendFlagsHelp(source);
            case "presets", "preset", "test" -> sendPresetsHelp(source);
            case "undo" -> sendUndoHelp(source);
            default -> sendOverviewHelp(source);
        }
        return 1;
    }

    private static void sendOverviewHelp(CommandSourceStack source) {
        sendLine(source, Component.literal("==============================================").withStyle(ChatFormatting.DARK_AQUA));
        sendLine(source, Component.literal("   FurBuild (NextFUR) - Procedural Builder Help").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        sendLine(source, buildTopicNavigationBar());
        sendLine(source, Component.literal("----------------------------------------------").withStyle(ChatFormatting.DARK_GRAY));

        sendLine(source, cmdEntry("/furbuild help <topic>", "Open detailed help for a specific topic", "/furbuild help "));
        sendLine(source, cmdEntry("/furwand mode <mode>", "Set Generate Wand selection shape (cuboid, polygon, cylinder, spline)", "/furwand mode "));
        sendLine(source, cmdEntry("/furwand clear", "Clear current selection points and saved plots", "/furwand clear"));
        sendLine(source, cmdEntry("/furstyle create <name> [grid]", "Create a new style folder & style.json", "/furstyle create "));
        sendLine(source, cmdEntry("/furstyle add <style> <cat> <name> [w]", "Save Cuboid selection as a 3D .nbt module", "/furstyle add "));
        sendLine(source, cmdEntry("/furstyle addpalette <style> <type>", "Scan Cuboid selection into a weighted block palette", "/furstyle addpalette "));
        sendLine(source, cmdEntry("/furstyle presets", "Generate/reset the 4 built-in testing style presets", "/furstyle presets"));
        sendLine(source, cmdEntry("/generate <type> <style> [flags]", "Run one of the 5 procedural generators (~500 blocks/tick)", "/generate "));
        sendLine(source, cmdEntry("/undo", "Revert your last generation back to its previous blocks", "/undo"));

        sendLine(source, Component.literal("Tip: Click any command above to paste it into chat, or click a topic tab at the top!").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        sendLine(source, Component.literal("==============================================").withStyle(ChatFormatting.DARK_AQUA));
    }

    private static void sendWandsHelp(CommandSourceStack source) {
        sendLine(source, Component.literal("=== [FurBuild Help: Wands & Selection Modes] ===").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        sendLine(source, buildTopicNavigationBar());
        sendLine(source, Component.literal("1. Style Wand (furbuild:style_wand)").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
        sendLine(source, Component.literal("   • Left-Click Block: Sets Point A  |  Right-Click Block: Sets Point B").withStyle(ChatFormatting.GRAY));
        sendLine(source, Component.literal("   • Used with /furstyle add & /furstyle addpalette to capture modules and palettes.").withStyle(ChatFormatting.GRAY));

        sendLine(source, Component.literal("2. Generate Wand (furbuild:generate_wand)").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
        sendLine(source, Component.literal("   • Hold Shift + Scroll-Wheel (or use /furwand mode <mode>) to cycle shapes:").withStyle(ChatFormatting.GRAY));
        sendLine(source, Component.literal("     - CUBOID: Left-Click = Point A, Right-Click = Point B (Buildings, Grids, Interiors, Dungeons)").withStyle(ChatFormatting.WHITE));
        sendLine(source, Component.literal("     - POLYGON: Left-Click = Point 1, Right-Click = Add Points 2..N (L-shape / U-shape buildings)").withStyle(ChatFormatting.WHITE));
        sendLine(source, Component.literal("     - CYLINDER: Left-Click = Center, Right-Click = Radius & Height (Wizard towers, silos)").withStyle(ChatFormatting.WHITE));
        sendLine(source, Component.literal("     - SPLINE: Left-Click = Waypoint 1, Right-Click = Add Waypoints 2..N (Winding organic paths)").withStyle(ChatFormatting.WHITE));
        sendLine(source, cmdEntry("/furwand clear", "Clears all current selection points and saved city plots.", "/furwand clear"));
    }

    private static void sendStyleHelp(CommandSourceStack source) {
        sendLine(source, Component.literal("=== [FurBuild Help: Style System (/furstyle)] ===").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        sendLine(source, buildTopicNavigationBar());
        sendLine(source, Component.literal("Styles are saved in: world/furbuild/styles/<style_name>/").withStyle(ChatFormatting.GRAY));
        sendLine(source, cmdEntry("/furstyle create <style> [grid_size]", "Initializes style.json (default grid_size = 5).", "/furstyle create medieval 5"));
        sendLine(source, cmdEntry("/furstyle add <style> <category> <name> [weight]", "Saves Cuboid selection as a Vanilla .nbt StructureTemplate.", "/furstyle add medieval wall stone_wall 1.0"));
        sendLine(source, Component.literal("   • Categories: " + String.join(", ", ModuleCategory.allNames())).withStyle(ChatFormatting.YELLOW));
        sendLine(source, Component.literal("   • IMPORTANT: Always build 3D modules facing NORTH (-Z) for proper rotation!").withStyle(ChatFormatting.RED));
        sendLine(source, cmdEntry("/furstyle addpalette <style> <palette_type>", "Scans blocks in selection & calculates exact weight percentages.", "/furstyle addpalette medieval painting"));
        sendLine(source, Component.literal("   • Palette Types: painting (wall weathering), path_blocks (roads/paths), floor (interior floors)").withStyle(ChatFormatting.AQUA));
        sendLine(source, cmdEntry("/furstyle list", "Lists all available styles in the world.", "/furstyle list"));
        sendLine(source, cmdEntry("/furstyle info <style>", "Displays module & palette counts for a style.", "/furstyle info medieval"));
        sendLine(source, cmdEntry("/furstyle presets", "Generates the 4 pre-built testing styles (medieval, cyberpunk, nordic, desert).", "/furstyle presets"));
    }

    private static void sendGenerateHelp(CommandSourceStack source) {
        sendLine(source, Component.literal("=== [FurBuild Help: The 5 Generation Engines (/generate)] ===").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        sendLine(source, buildTopicNavigationBar());
        sendLine(source, Component.literal("Syntax: /generate <type> <style> [flags...]").withStyle(ChatFormatting.AQUA));

        sendLine(source, Component.literal("1. path").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
                .append(Component.literal(" (Best Mode: SPLINE) — Smooth Catmull-Rom curve projected onto terrain heightmap using path_blocks palette with organic edge noise and roadside ornament modules.").withStyle(ChatFormatting.GRAY)));
        sendLine(source, Component.literal("2. grid").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
                .append(Component.literal(" (Best Mode: CUBOID/POLYGON) — Binary Space Partitioning (BSP) city streets (road_segment, intersection). Saves empty City Plots so running '/generate building <style>' fills the entire city!").withStyle(ChatFormatting.GRAY)));
        sendLine(source, Component.literal("3. building").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
                .append(Component.literal(" (Best Mode: CUBOID/POLYGON/CYLINDER) — Traces perimeter (wall, corner, door, window, roof) or builds cylindrical towers with cone roofs + secondary Painting Pass.").withStyle(ChatFormatting.GRAY)));
        sendLine(source, Component.literal("4. interior").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
                .append(Component.literal(" (Best Mode: CUBOID or -interior flag) — Slices vertical floors, subdivides rooms with doorways, cuts a multi-floor stairwell, and places interior_prop furniture.").withStyle(ChatFormatting.GRAY)));
        sendLine(source, Component.literal("5. dungeon").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
                .append(Component.literal(" (Best Mode: CUBOID) — Scatters non-overlapping dungeon_room modules, connects them via Delaunay Triangulation + Minimum Spanning Tree (MST), and carves 3D A* corridors.").withStyle(ChatFormatting.GRAY)));
    }

    private static void sendFlagsHelp(CommandSourceStack source) {
        sendLine(source, Component.literal("=== [FurBuild Help: Generator Flags & Options] ===").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        sendLine(source, buildTopicNavigationBar());
        sendLine(source, Component.literal("Append any combination of unordered flags to /generate <type> <style>:").withStyle(ChatFormatting.GRAY));
        sendLine(source, Component.literal(" • -floors <int>").withStyle(ChatFormatting.AQUA)
                .append(Component.literal(" : Forces a specific number of building/interior floors (e.g. -floors 3)").withStyle(ChatFormatting.WHITE)));
        sendLine(source, Component.literal(" • -seed <long>").withStyle(ChatFormatting.AQUA)
                .append(Component.literal(" : Uses a fixed seed to reproduce the exact same random layout").withStyle(ChatFormatting.WHITE)));
        sendLine(source, Component.literal(" • -whitelist <b1,b2>").withStyle(ChatFormatting.AQUA)
                .append(Component.literal(" : Only replaces these blocks (e.g. -whitelist air)").withStyle(ChatFormatting.WHITE)));
        sendLine(source, Component.literal(" • -blacklist <b1,b2>").withStyle(ChatFormatting.AQUA)
                .append(Component.literal(" : Never overwrites these blocks (e.g. -blacklist bedrock,chest)").withStyle(ChatFormatting.WHITE)));
        sendLine(source, Component.literal(" • -exclude <c1,c2>").withStyle(ChatFormatting.AQUA)
                .append(Component.literal(" : Skips specific module categories (e.g. -exclude ornaments,windows)").withStyle(ChatFormatting.WHITE)));
        sendLine(source, Component.literal(" • -align <ground|selection>").withStyle(ChatFormatting.AQUA)
                .append(Component.literal(" : Snaps to terrain heightmap or strictly obeys selection Y-level").withStyle(ChatFormatting.WHITE)));
        sendLine(source, Component.literal(" • -width <int>").withStyle(ChatFormatting.AQUA)
                .append(Component.literal(" : Sets path brush diameter or street width (default: 4)").withStyle(ChatFormatting.WHITE)));
        sendLine(source, Component.literal(" • -interior").withStyle(ChatFormatting.AQUA)
                .append(Component.literal(" : Automatically generates floors, rooms, stairs & props inside '/generate building'").withStyle(ChatFormatting.WHITE)));
    }

    private static void sendPresetsHelp(CommandSourceStack source) {
        sendLine(source, Component.literal("=== [FurBuild Help: Built-in Testing Style Presets] ===").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        sendLine(source, buildTopicNavigationBar());
        sendLine(source, Component.literal("FurBuild includes 4 complete preset styles pre-populated with all 11 .nbt modules & 3 .json palettes:").withStyle(ChatFormatting.GRAY));
        sendLine(source, Component.literal(" • medieval").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
                .append(Component.literal(" : Stone bricks, mossy/cracked weathering, spruce timber frames, dark oak roofs, crypt dungeons.").withStyle(ChatFormatting.WHITE)));
        sendLine(source, Component.literal(" • cyberpunk").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)
                .append(Component.literal(" : Polished deepslate, black concrete, cyan glass, sea lantern neon accents, underground bunkers.").withStyle(ChatFormatting.WHITE)));
        sendLine(source, Component.literal(" • nordic").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD)
                .append(Component.literal(" : Spruce logs & planks, mossy cobblestone foundations, podzol/coarse dirt trails.").withStyle(ChatFormatting.WHITE)));
        sendLine(source, Component.literal(" • desert").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                .append(Component.literal(" : Cut & smooth sandstone, terracotta accents, acacia lattice windows, sandstone tombs.").withStyle(ChatFormatting.WHITE)));

        sendLine(source, Component.literal("Quick Test Commands (Click to paste):").withStyle(ChatFormatting.LIGHT_PURPLE));
        sendLine(source, cmdEntry("/generate building medieval -floors 3 -interior", "Generate a 3-story medieval building with interior", "/generate building medieval -floors 3 -interior"));
        sendLine(source, cmdEntry("/generate grid cyberpunk -width 5", "Generate a cyberpunk city grid & plots", "/generate grid cyberpunk -width 5"));
        sendLine(source, cmdEntry("/generate path nordic -width 4", "Generate a winding nordic road along spline waypoints", "/generate path nordic -width 4"));
        sendLine(source, cmdEntry("/generate dungeon desert -seed 42", "Generate an underground desert tomb dungeon", "/generate dungeon desert -seed 42"));
    }

    private static void sendUndoHelp(CommandSourceStack source) {
        sendLine(source, Component.literal("=== [FurBuild Help: Tick Queue & /undo] ===").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        sendLine(source, buildTopicNavigationBar());
        sendLine(source, Component.literal("• BlockPlacementQueue places ~500 blocks/tick on ServerTickEvent.Post with sounds & particles so massive builds never lag the server.").withStyle(ChatFormatting.GRAY));
        sendLine(source, Component.literal("• Every overwritten BlockState and BlockEntity NBT is recorded into your personal undo history (up to 15 generations).").withStyle(ChatFormatting.GRAY));
        sendLine(source, cmdEntry("/undo", "Reverts your most recent generation at ~500 blocks/tick.", "/undo"));
    }

    private static MutableComponent buildTopicNavigationBar() {
        MutableComponent bar = Component.literal("Topics: ").withStyle(ChatFormatting.GRAY);
        for (String topic : HELP_TOPICS) {
            String label = "[" + topic.substring(0, 1).toUpperCase(Locale.ROOT) + topic.substring(1) + "]";
            MutableComponent btn = Component.literal(label)
                    .withStyle(style -> style
                            .withColor(ChatFormatting.AQUA)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/furbuild help " + topic))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                    Component.literal("Click to view /furbuild help " + topic).withStyle(ChatFormatting.YELLOW)))
                    );
            bar.append(btn).append(Component.literal(" "));
        }
        return bar;
    }

    private static MutableComponent cmdEntry(String usage, String description, String suggestCommand) {
        MutableComponent cmd = Component.literal("  " + usage)
                .withStyle(style -> style
                        .withColor(ChatFormatting.YELLOW)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, suggestCommand))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.literal("Click to insert: " + suggestCommand).withStyle(ChatFormatting.AQUA)))
                );
        return cmd.append(Component.literal(" - " + description).withStyle(ChatFormatting.WHITE));
    }

    private static void sendLine(CommandSourceStack source, Component line) {
        source.sendSuccess(() -> line, false);
    }
}
