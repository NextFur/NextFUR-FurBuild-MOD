package net.nextfur.fwb.items;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.nextfur.fwb.init.FwDataComponents;
import net.nextfur.fwb.style.ModuleCategory;
import net.nextfur.fwb.util.SelectionService;

import java.util.List;

public class SelectWandItem extends Item {

    public SelectWandItem(Properties properties) {
        super(properties.stacksTo(1).component(FwDataComponents.SELECT_CATEGORY.get(), ModuleCategory.WALL));
    }

    @Override
    public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player) {
        return false;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (!level.isClientSide() && context.getPlayer() instanceof ServerPlayer serverPlayer) {
            if (serverPlayer.isShiftKeyDown()) {
                SelectionService.handleSelectWandQuickMap(serverPlayer, context.getItemInHand());
            } else {
                SelectionService.handleSelectWandRightClick(serverPlayer, context.getItemInHand(), context.getClickedPos());
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        ItemStack stack = player.getItemInHand(usedHand);
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
                SelectionService.handleSelectWandQuickMap(serverPlayer, stack);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }
        return InteractionResultHolder.pass(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        ModuleCategory category = stack.getOrDefault(FwDataComponents.SELECT_CATEGORY, ModuleCategory.WALL);
        tooltipComponents.add(Component.translatable("item.furbuild.select_wand.category",
                Component.literal(category.getSerializedName()).withStyle(ChatFormatting.AQUA)).withStyle(ChatFormatting.GOLD));
        tooltipComponents.add(Component.translatable("item.furbuild.select_wand.desc1").withStyle(ChatFormatting.GRAY));
        tooltipComponents.add(Component.translatable("item.furbuild.select_wand.desc2").withStyle(ChatFormatting.DARK_AQUA));
        tooltipComponents.add(Component.translatable("item.furbuild.select_wand.desc3").withStyle(ChatFormatting.YELLOW));
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }
}
