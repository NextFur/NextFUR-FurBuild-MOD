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
import net.nextfur.fwb.util.SelectionService;

import java.util.List;

public class StyleWandItem extends Item {

    public StyleWandItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player) {
        return false;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (!level.isClientSide() && context.getPlayer() instanceof ServerPlayer serverPlayer) {
            SelectionService.handleStyleWandRightClick(serverPlayer, context.getClickedPos());
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("item.furbuild.style_wand.desc1").withStyle(ChatFormatting.GRAY));
        tooltipComponents.add(Component.translatable("item.furbuild.style_wand.desc2").withStyle(ChatFormatting.DARK_AQUA));
        tooltipComponents.add(Component.translatable("item.furbuild.style_wand.desc3").withStyle(ChatFormatting.YELLOW));
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }
}
