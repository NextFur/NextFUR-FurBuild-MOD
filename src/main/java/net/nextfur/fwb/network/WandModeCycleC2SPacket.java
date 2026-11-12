package net.nextfur.fwb.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.nextfur.fwb.FwMain;
import net.nextfur.fwb.util.SelectionService;

public record WandModeCycleC2SPacket(int scrollDirection) implements CustomPacketPayload {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(FwMain.MODID, "wand_mode_cycle");
    public static final Type<WandModeCycleC2SPacket> TYPE = new Type<>(ID);

    public static final StreamCodec<ByteBuf, WandModeCycleC2SPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            WandModeCycleC2SPacket::scrollDirection,
            WandModeCycleC2SPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(WandModeCycleC2SPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer serverPlayer) {
                SelectionService.cycleWandMode(serverPlayer, packet.scrollDirection());
            }
        });
    }
}
