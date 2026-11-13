package net.nextfur.fwb.init;

import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.nextfur.fwb.FwMain;
import net.nextfur.fwb.items.GenerateWandItem;
import net.nextfur.fwb.items.PaletteBlockItem;
import net.nextfur.fwb.items.SelectWandItem;
import net.nextfur.fwb.items.StyleWandItem;

public class FwModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(FwMain.MODID);

    public static final DeferredItem<StyleWandItem> STYLE_WAND = ITEMS.register(
            "style_wand",
            () -> new StyleWandItem(new Item.Properties())
    );

    public static final DeferredItem<GenerateWandItem> GENERATE_WAND = ITEMS.register(
            "generate_wand",
            () -> new GenerateWandItem(new Item.Properties())
    );

    public static final DeferredItem<SelectWandItem> SELECT_WAND = ITEMS.register(
            "select_wand",
            () -> new SelectWandItem(new Item.Properties())
    );

    static {
        for (FwModBlocks.PaletteBlockSet set : FwModBlocks.PALETTE_SETS) {
            String id = set.spec().id();
            ITEMS.register(id, () -> new PaletteBlockItem(set.block().get(), new Item.Properties(), set.spec()));
            ITEMS.register(id + "_slab", () -> new PaletteBlockItem(set.slab().get(), new Item.Properties(), set.spec()));
            ITEMS.register(id + "_stairs", () -> new PaletteBlockItem(set.stairs().get(), new Item.Properties(), set.spec()));
            ITEMS.register(id + "_pole", () -> new PaletteBlockItem(set.pole().get(), new Item.Properties(), set.spec()));
        }
    }
}
