package io.github.gaming32.mc2p2.network;

import io.github.gaming32.mc2p2.generator.IssueLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public record IssueMarkersPayload(IssueLevel level, Component message, Collection<BlockPos> blocks) implements CustomPacketPayload {
    public static final ResourceLocation ID = new ResourceLocation("mc2p2:issue_markers");

    public IssueMarkersPayload(FriendlyByteBuf buf) {
        this(buf.readEnum(IssueLevel.class), buf.readComponent(), buf.readList(FriendlyByteBuf::readBlockPos));
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeEnum(level);
        buf.writeComponent(message);
        buf.writeCollection(blocks, FriendlyByteBuf::writeBlockPos);
    }

    @NotNull
    @Override
    public ResourceLocation id() {
        return ID;
    }
}
