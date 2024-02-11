package io.github.gaming32.mc2p2.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public enum ClearIssueMarkersPayload implements CustomPacketPayload {
    INSTANCE;

    public static final ResourceLocation ID = new ResourceLocation("mc2p2:clear_issue_makers");

    @Override
    public void write(FriendlyByteBuf buffer) {
    }

    @NotNull
    @Override
    public ResourceLocation id() {
        return ID;
    }
}
