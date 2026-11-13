package net.nextfur.fwb.init;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.nextfur.fwb.client.SelectionRenderer;
import net.nextfur.fwb.client.WandClientInputHandler;
import net.nextfur.fwb.generator.BlockPlacementQueue;
import net.nextfur.fwb.items.GenerateWandItem;
import net.nextfur.fwb.items.SelectWandItem;
import net.nextfur.fwb.items.StyleWandItem;
import net.nextfur.fwb.util.SelectionService;

public class FwModEvents {

    public static void register() {
        IEventBus eventBus = NeoForge.EVENT_BUS;

        eventBus.addListener(FwModEvents::onLeftClickBlock);
        eventBus.addListener(FwModEvents::onRightClickBlock);
        eventBus.addListener(FwModEvents::onRightClickItem);
        eventBus.addListener(FwModEvents::onPlayerLogin);
        eventBus.addListener(FwModEvents::onPlayerRespawn);
        eventBus.addListener(FwModEvents::onPlayerChangeDimension);
        eventBus.addListener(FwModEvents::onServerTickPost);

        if (FMLLoader.getDist() == Dist.CLIENT) {
            ClientHandlerOnly.register(eventBus);
        }
    }

    private static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        ItemStack stack = event.getItemStack();
        if (stack.getItem() instanceof StyleWandItem) {
            if (!event.getLevel().isClientSide() && event.getEntity() instanceof ServerPlayer serverPlayer) {
                if (event.getAction() == PlayerInteractEvent.LeftClickBlock.Action.START) {
                    SelectionService.handleStyleWandLeftClick(serverPlayer, event.getPos());
                }
            }
            event.setCanceled(true);
        } else if (stack.getItem() instanceof SelectWandItem) {
            if (!event.getLevel().isClientSide() && event.getEntity() instanceof ServerPlayer serverPlayer) {
                if (event.getAction() == PlayerInteractEvent.LeftClickBlock.Action.START) {
                    SelectionService.handleSelectWandLeftClick(serverPlayer, stack, event.getPos());
                }
            }
            event.setCanceled(true);
        } else if (stack.getItem() instanceof GenerateWandItem) {
            if (!event.getLevel().isClientSide() && event.getEntity() instanceof ServerPlayer serverPlayer) {
                if (event.getAction() == PlayerInteractEvent.LeftClickBlock.Action.START) {
                    SelectionService.handleGenerateWandLeftClick(serverPlayer, stack, event.getPos());
                }
            }
            event.setCanceled(true);
        }
    }

    private static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        ItemStack stack = event.getItemStack();
        if (stack.getItem() instanceof StyleWandItem) {
            if (!event.getLevel().isClientSide() && event.getEntity() instanceof ServerPlayer serverPlayer) {
                SelectionService.handleStyleWandRightClick(serverPlayer, event.getPos());
            }
            event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide()));
            event.setCanceled(true);
        } else if (stack.getItem() instanceof SelectWandItem) {
            if (!event.getLevel().isClientSide() && event.getEntity() instanceof ServerPlayer serverPlayer) {
                if (serverPlayer.isShiftKeyDown()) {
                    SelectionService.handleSelectWandQuickMap(serverPlayer, stack);
                } else {
                    SelectionService.handleSelectWandRightClick(serverPlayer, stack, event.getPos());
                }
            }
            event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide()));
            event.setCanceled(true);
        } else if (stack.getItem() instanceof GenerateWandItem) {
            if (!event.getLevel().isClientSide() && event.getEntity() instanceof ServerPlayer serverPlayer) {
                SelectionService.handleGenerateWandRightClick(serverPlayer, stack, event.getPos());
            }
            event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide()));
            event.setCanceled(true);
        }
    }

    private static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        ItemStack stack = event.getItemStack();
        if (stack.getItem() instanceof SelectWandItem && event.getEntity().isShiftKeyDown()) {
            if (!event.getLevel().isClientSide() && event.getEntity() instanceof ServerPlayer serverPlayer) {
                SelectionService.handleSelectWandQuickMap(serverPlayer, stack);
            }
            event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide()));
            event.setCanceled(true);
        }
    }

    private static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            SelectionService.syncToClient(serverPlayer);
        }
    }

    private static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            SelectionService.syncToClient(serverPlayer);
        }
    }

    private static void onPlayerChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            SelectionService.syncToClient(serverPlayer);
        }
    }

    private static void onServerTickPost(ServerTickEvent.Post event) {
        BlockPlacementQueue.tick(event.getServer());
    }

    private static class ClientHandlerOnly {
        public static void register(IEventBus eventBus) {
            eventBus.addListener(SelectionRenderer::onRenderWorld);
            eventBus.addListener(WandClientInputHandler::onMouseScroll);
        }
    }
}
