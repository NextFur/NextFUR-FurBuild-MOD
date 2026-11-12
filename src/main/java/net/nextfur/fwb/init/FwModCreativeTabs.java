package net.nextfur.fwb.init;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
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
                        for (DeferredHolder<Item, ? extends Item> item : FwModItems.ITEMS.getEntries()) {
                            output.accept(item.get());
                        }
                    })
                    .build());
}
