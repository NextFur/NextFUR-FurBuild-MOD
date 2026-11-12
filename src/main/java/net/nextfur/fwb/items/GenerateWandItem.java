package net.nextfur.fwb.items;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.nextfur.fwb.data.SelectionMode;
import net.nextfur.fwb.init.FwDataComponents;
import net.nextfur.fwb.util.SelectionService;

import java.util.List;

public class GenerateWandItem extends Item {

    public GenerateWandItem(Properties properties) {
        super(properties.stacksTo(1).component(FwDataComponents.WAND_MODE.get(), SelectionMode.CUBOID));
    }

    @Override
    public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player) {
        return false;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (!level.isClientSide() && context.getPlayer() instanceof ServerPlayer serverPlayer) {
            SelectionService.handleGenerateWandRightClick(serverPlayer, context.getItemInHand(), context.getClickedPos());
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        SelectionMode mode = stack.getOrDefault(FwDataComponents.WAND_MODE, SelectionMode.CUBOID);
        tooltipComponents.add(Component.translatable("item.furbuild.generate_wand.mode",
                Component.literal(mode.name()).withStyle(ChatFormatting.AQUA)).withStyle(ChatFormatting.GOLD));
        tooltipComponents.add(Component.translatable("item.furbuild.generate_wand.desc1").withStyle(ChatFormatting.GRAY));
        tooltipComponents.add(Component.translatable("item.furbuild.generate_wand.desc2").withStyle(ChatFormatting.DARK_AQUA));
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }
}
