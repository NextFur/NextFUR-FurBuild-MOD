package net.nextfur.fwb.util;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.nextfur.fwb.data.PlayerSelectionData;
import net.nextfur.fwb.data.SelectionMode;
import net.nextfur.fwb.init.FwAttachments;
import net.nextfur.fwb.init.FwDataComponents;
import net.nextfur.fwb.items.GenerateWandItem;
import net.nextfur.fwb.network.SelectionSyncS2CPacket;

import java.util.Locale;

public class SelectionService {

    public static PlayerSelectionData getSelection(ServerPlayer player) {
        return player.getData(FwAttachments.PLAYER_SELECTION);
    }

    public static void setSelection(ServerPlayer player, PlayerSelectionData data) {
        player.setData(FwAttachments.PLAYER_SELECTION, data);
        syncToClient(player, data);
    }

    public static void syncToClient(ServerPlayer player, PlayerSelectionData data) {
        PacketDistributor.sendToPlayer(player, new SelectionSyncS2CPacket(data));
    }

    public static void syncToClient(ServerPlayer player) {
        syncToClient(player, getSelection(player));
    }

    public static void setWandMode(ServerPlayer player, SelectionMode newMode) {
        PlayerSelectionData current = getSelection(player);
        PlayerSelectionData updated = current.withMode(newMode);
        setSelection(player, updated);

        // Also update the DataComponent on the held GenerateWandItem if present
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.getItem() instanceof GenerateWandItem) {
                stack.set(FwDataComponents.WAND_MODE, newMode);
            }
        }

        player.displayClientMessage(
                Component.translatable("message.furbuild.wand.mode_changed",
                        Component.literal(newMode.name()).withStyle(ChatFormatting.AQUA)
                ).withStyle(ChatFormatting.GOLD),
                true
        );
        player.level().playSound(null, player.blockPosition(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.5f, 1.2f);
    }

    public static void cycleWandMode(ServerPlayer player, int direction) {
        ItemStack mainHand = player.getMainHandItem();
        ItemStack offHand = player.getOffhandItem();
        SelectionMode currentMode = getSelection(player).mode();

        if (mainHand.getItem() instanceof GenerateWandItem) {
            currentMode = mainHand.getOrDefault(FwDataComponents.WAND_MODE, currentMode);
        } else if (offHand.getItem() instanceof GenerateWandItem) {
            currentMode = offHand.getOrDefault(FwDataComponents.WAND_MODE, currentMode);
        }

        SelectionMode nextMode = currentMode.next(direction);
        setWandMode(player, nextMode);
    }

    public static void handleStyleWandLeftClick(ServerPlayer player, BlockPos pos) {
        PlayerSelectionData current = getSelection(player);
        PlayerSelectionData updated = current.withMode(SelectionMode.CUBOID).withPosA(pos);
        setSelection(player, updated);
        player.displayClientMessage(
                Component.translatable("message.furbuild.selection.pos_a", formatPos(pos)).withStyle(ChatFormatting.GREEN),
                true
        );
    }

    public static void handleStyleWandRightClick(ServerPlayer player, BlockPos pos) {
        PlayerSelectionData current = getSelection(player);
        PlayerSelectionData updated = current.withMode(SelectionMode.CUBOID).withPosB(pos);
        setSelection(player, updated);
        player.displayClientMessage(
                Component.translatable("message.furbuild.selection.pos_b", formatPos(pos)).withStyle(ChatFormatting.AQUA),
                true
        );
    }

    public static void handleGenerateWandLeftClick(ServerPlayer player, ItemStack stack, BlockPos pos) {
        SelectionMode wandMode = stack.getOrDefault(FwDataComponents.WAND_MODE, getSelection(player).mode());
        PlayerSelectionData current = getSelection(player).withMode(wandMode);
        PlayerSelectionData updated;

        switch (wandMode) {
            case POLYGON, SPLINE -> {
                updated = current.withResetPoint1(pos);
                player.displayClientMessage(
                        Component.translatable("message.furbuild.selection.point_start",
                                wandMode.name(), formatPos(pos)).withStyle(ChatFormatting.GREEN),
                        true
                );
            }
            case CYLINDER -> {
                updated = current.withPosA(pos);
                player.displayClientMessage(
                        Component.translatable("message.furbuild.selection.cylinder_center", formatPos(pos)).withStyle(ChatFormatting.GREEN),
                        true
                );
            }
            case CUBOID -> {
                updated = current.withPosA(pos);
                player.displayClientMessage(
                        Component.translatable("message.furbuild.selection.pos_a", formatPos(pos)).withStyle(ChatFormatting.GREEN),
                        true
                );
            }
            default -> updated = current.withPosA(pos);
        }

        setSelection(player, updated);
    }

    public static void handleGenerateWandRightClick(ServerPlayer player, ItemStack stack, BlockPos pos) {
        SelectionMode wandMode = stack.getOrDefault(FwDataComponents.WAND_MODE, getSelection(player).mode());
        PlayerSelectionData current = getSelection(player).withMode(wandMode);
        PlayerSelectionData updated;

        switch (wandMode) {
            case POLYGON, SPLINE -> {
                updated = current.withAppendedPoint(pos);
                int idx = updated.points().size();
                player.displayClientMessage(
                        Component.translatable("message.furbuild.selection.point_added",
                                idx, wandMode.name(), formatPos(pos)).withStyle(ChatFormatting.AQUA),
                        true
                );
            }
            case CYLINDER -> {
                updated = current.withPosB(pos);
                int radius = 1;
                int height = 1;
                if (updated.posA().isPresent()) {
                    BlockPos center = updated.posA().get();
                    int dx = pos.getX() - center.getX();
                    int dz = pos.getZ() - center.getZ();
                    radius = Math.max(1, (int) Math.round(Math.sqrt(dx * dx + dz * dz)));
                    height = Math.abs(pos.getY() - center.getY()) + 1;
                }
                player.displayClientMessage(
                        Component.translatable("message.furbuild.selection.cylinder_radius",
                                radius, height, formatPos(pos)).withStyle(ChatFormatting.AQUA),
                        true
                );
            }
            case CUBOID -> {
                updated = current.withPosB(pos);
                player.displayClientMessage(
                        Component.translatable("message.furbuild.selection.pos_b", formatPos(pos)).withStyle(ChatFormatting.AQUA),
                        true
                );
            }
            default -> updated = current.withPosB(pos);
        }

        setSelection(player, updated);
    }

    public static String formatPos(BlockPos pos) {
        return String.format(Locale.ROOT, "(%d, %d, %d)", pos.getX(), pos.getY(), pos.getZ());
    }
}
