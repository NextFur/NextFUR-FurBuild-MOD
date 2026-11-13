package net.nextfur.fwb.items;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;
import net.nextfur.fwb.init.FwPaletteDefinitions;

import java.util.List;

public class PaletteBlockItem extends BlockItem {

    private final FwPaletteDefinitions.BlockSetSpec spec;

    public PaletteBlockItem(Block block, Properties properties, FwPaletteDefinitions.BlockSetSpec spec) {
        super(block, properties);
        this.spec = spec;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        Component familyName = Component.translatable("tooltip.furbuild.palette_family." + spec.family().id());
        tooltipComponents.add(Component.translatable(
                "tooltip.furbuild.palette_info",
                familyName,
                spec.color().gradientStep(),
                spec.color().hexCode()
        ).withStyle(ChatFormatting.GRAY));
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }
}
