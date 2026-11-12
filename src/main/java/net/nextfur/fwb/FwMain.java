package net.nextfur.fwb;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.nextfur.fwb.generator.BlockPlacementQueue;
import net.nextfur.fwb.init.FwAttachments;
import net.nextfur.fwb.init.FwDataComponents;
import net.nextfur.fwb.init.FwModBlocks;
import net.nextfur.fwb.init.FwModCommands;
import net.nextfur.fwb.init.FwModCreativeTabs;
import net.nextfur.fwb.init.FwModEvents;
import net.nextfur.fwb.init.FwModItems;
import net.nextfur.fwb.init.FwModPackets;
import net.nextfur.fwb.style.DefaultStylePresets;
import org.slf4j.Logger;

@Mod(FwMain.MODID)
public class FwMain {
    public static final String MODID = "furbuild";
    public static final Logger LOGGER = LogUtils.getLogger();

    public FwMain(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("Initializing FurBuild (NextFUR) - Minecraft 1.21.1 / NeoForge 21.1.215");

        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        modEventBus.addListener(FwModPackets::register);

        FwModBlocks.BLOCKS.register(modEventBus);
        FwModItems.ITEMS.register(modEventBus);
        FwDataComponents.DATA_COMPONENT_TYPES.register(modEventBus);
        FwAttachments.ATTACHMENT_TYPES.register(modEventBus);
        FwModCreativeTabs.CREATIVE_MODE_TABS.register(modEventBus);

        modEventBus.addListener(this::commonSetup);

        NeoForge.EVENT_BUS.register(this);

        FwModEvents.register();
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("FurBuild common setup completed.");
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        FwModCommands.registerCommands(event.getDispatcher());
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        int installed = DefaultStylePresets.installPresets(event.getServer(), false);
        LOGGER.info("FurBuild server starting event triggered. Installed {} new default style presets.", installed);
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        BlockPlacementQueue.clearAll();
    }
}
