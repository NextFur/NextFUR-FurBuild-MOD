package net.nextfur.fwb.init;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.nextfur.fwb.FwMain;
import net.nextfur.fwb.data.SelectionMode;
import net.nextfur.fwb.style.ModuleCategory;

public class FwDataComponents {
    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENT_TYPES =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, FwMain.MODID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<SelectionMode>> WAND_MODE =
            DATA_COMPONENT_TYPES.register("wand_mode", () -> DataComponentType.<SelectionMode>builder()
                    .persistent(SelectionMode.CODEC)
                    .networkSynchronized(SelectionMode.STREAM_CODEC)
                    .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ModuleCategory>> SELECT_CATEGORY =
            DATA_COMPONENT_TYPES.register("select_category", () -> DataComponentType.<ModuleCategory>builder()
                    .persistent(ModuleCategory.CODEC)
                    .networkSynchronized(ModuleCategory.STREAM_CODEC)
                    .build());
}
