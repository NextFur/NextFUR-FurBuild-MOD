package net.nextfur.fwb.init;

import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.nextfur.fwb.FwMain;
import net.nextfur.fwb.data.PlayerSelectionData;

public class FwAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, FwMain.MODID);

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<PlayerSelectionData>> PLAYER_SELECTION =
            ATTACHMENT_TYPES.register("player_selection", () -> AttachmentType.builder(() -> PlayerSelectionData.DEFAULT)
                    .serialize(PlayerSelectionData.CODEC)
                    .copyOnDeath()
                    .build());
}
