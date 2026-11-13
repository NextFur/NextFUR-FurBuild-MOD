package net.nextfur.fwb.init;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.nextfur.fwb.FwMain;

public class FwModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, FwMain.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> FURBUILD_TAB =
            CREATIVE_MODE_TABS.register("furbuild_tab", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.furbuild"))
                    .withTabsBefore(CreativeModeTabs.COMBAT)
                    .icon(() -> new ItemStack(FwModItems.GENERATE_WAND.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(FwModItems.STYLE_WAND.get());
                        output.accept(FwModItems.SELECT_WAND.get());
                        output.accept(FwModItems.GENERATE_WAND.get());
                    })
                    .build());

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> FURBUILD_PALETTES_TAB =
            CREATIVE_MODE_TABS.register("furbuild_palettes_tab", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.furbuild.palettes"))
                    .withTabsAfter(FURBUILD_TAB.getKey())
                    .icon(() -> {
                        FwModBlocks.PaletteBlockSet iconSet = FwModBlocks.BY_ID.get("cinnabar_rust_tiles");
                        return iconSet != null
                                ? new ItemStack(iconSet.block().get())
                                : new ItemStack(FwModItems.GENERATE_WAND.get());
                    })
                    .displayItems((parameters, output) -> {
                        for (FwModBlocks.PaletteBlockSet set : FwModBlocks.PALETTE_SETS) {
                            output.accept(set.block().get());
                            output.accept(set.slab().get());
                            output.accept(set.stairs().get());
                            output.accept(set.pole().get());
                        }
                    })
                    .build());
}
