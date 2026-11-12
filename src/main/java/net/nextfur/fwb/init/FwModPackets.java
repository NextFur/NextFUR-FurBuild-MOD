package net.nextfur.fwb.init;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.nextfur.fwb.FwMain;
import net.nextfur.fwb.network.SelectionSyncS2CPacket;
import net.nextfur.fwb.network.WandModeCycleC2SPacket;

public class FwModPackets {

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar(FwMain.MODID).versioned("1.0");

        registrar.playToClient(
                SelectionSyncS2CPacket.TYPE,
                SelectionSyncS2CPacket.STREAM_CODEC,
                SelectionSyncS2CPacket::handle
        );

        registrar.playToServer(
                WandModeCycleC2SPacket.TYPE,
                WandModeCycleC2SPacket.STREAM_CODEC,
                WandModeCycleC2SPacket::handle
        );
    }
}
