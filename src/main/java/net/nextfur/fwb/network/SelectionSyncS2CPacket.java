package net.nextfur.fwb.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.nextfur.fwb.FwMain;
import net.nextfur.fwb.client.ClientSelectionHolder;
import net.nextfur.fwb.data.PlayerSelectionData;
import net.nextfur.fwb.init.FwAttachments;

public record SelectionSyncS2CPacket(PlayerSelectionData selectionData) implements CustomPacketPayload {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(FwMain.MODID, "selection_sync");
    public static final Type<SelectionSyncS2CPacket> TYPE = new Type<>(ID);

    public static final StreamCodec<ByteBuf, SelectionSyncS2CPacket> STREAM_CODEC = StreamCodec.composite(
            PlayerSelectionData.STREAM_CODEC,
            SelectionSyncS2CPacket::selectionData,
            SelectionSyncS2CPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SelectionSyncS2CPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ClientSelectionHolder.set(packet.selectionData());
            if (ctx.player() != null) {
                ctx.player().setData(FwAttachments.PLAYER_SELECTION, packet.selectionData());
            }
        });
    }
}
