package net.nextfur.fwb.commands;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.nextfur.fwb.generator.BlockPlacementQueue;

public class UndoCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("ud")
                .requires(source -> source.hasPermission(2))
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayer();
                    if (player == null) {
                        ctx.getSource().sendFailure(Component.literal("Only players can run /ud."));
                        return 0;
                    }
                    boolean success = BlockPlacementQueue.enqueueUndo(player);
                    if (!success) {
                        ctx.getSource().sendFailure(Component.translatable("message.furbuild.undo.empty")
                                .withStyle(ChatFormatting.RED));
                        return 0;
                    }
                    return 1;
                })
        );

        dispatcher.register(Commands.literal("rd")
                .requires(source -> source.hasPermission(2))
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayer();
                    if (player == null) {
                        ctx.getSource().sendFailure(Component.literal("Only players can run /rd."));
                        return 0;
                    }
                    boolean success = BlockPlacementQueue.enqueueRedo(player);
                    if (!success) {
                        ctx.getSource().sendFailure(Component.translatable("message.furbuild.redo.empty")
                                .withStyle(ChatFormatting.RED));
                        return 0;
                    }
                    return 1;
                })
        );
    }
}
