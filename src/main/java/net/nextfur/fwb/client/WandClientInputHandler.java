package net.nextfur.fwb.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.nextfur.fwb.items.GenerateWandItem;
import net.nextfur.fwb.network.WandModeCycleC2SPacket;

public class WandClientInputHandler {

    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.screen != null) {
            return;
        }

        if (!player.isShiftKeyDown()) {
            return;
        }

        ItemStack mainHand = player.getMainHandItem();
        ItemStack offHand = player.getOffhandItem();
        if (!(mainHand.getItem() instanceof GenerateWandItem) && !(offHand.getItem() instanceof GenerateWandItem)) {
            return;
        }

        double scrollDelta = event.getScrollDeltaY();
        if (Math.abs(scrollDelta) < 1.0e-4) {
            return;
        }

        int direction = scrollDelta > 0 ? 1 : -1;
        PacketDistributor.sendToServer(new WandModeCycleC2SPacket(direction));
        event.setCanceled(true);
    }
}
