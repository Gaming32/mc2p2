package io.github.gaming32.mc2p2.client;

import io.github.gaming32.mc2p2.generator.IssueLevel;
import io.github.gaming32.mc2p2.mixin.GameTestDebugRendererAccessor;
import io.github.gaming32.mc2p2.network.ClearIssueMarkersPayload;
import io.github.gaming32.mc2p2.network.IssueMarkersPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

public class MC2P2Client implements ClientModInitializer {
    public static final Map<BlockPos, IssueLevel> ISSUE_MARKERS = new HashMap<>();

    @Override
    public void onInitializeClient() {
        registerHandler(IssueMarkersPayload.ID, IssueMarkersPayload::new, (minecraft, responder, payload) -> {
            final String messageText = payload.message().getString();
            assert payload.level().color.getColor() != null;
            final int color = payload.level().color.getColor() | 0x80000000;
            for (final BlockPos pos : payload.blocks()) {
                final IssueLevel oldLevel = ISSUE_MARKERS.get(pos);
                if (oldLevel != null && oldLevel.compareTo(payload.level()) >= 0) continue;
                ISSUE_MARKERS.put(pos, payload.level());
                minecraft.debugRenderer.gameTestDebugRenderer.addMarker(pos, color, messageText, Integer.MAX_VALUE);
            }
        });

        registerHandler(ClearIssueMarkersPayload.ID, buf -> ClearIssueMarkersPayload.INSTANCE, (minecraft, responder, payload) -> {
            ((GameTestDebugRendererAccessor)minecraft.debugRenderer.gameTestDebugRenderer).getMarkers().keySet().removeAll(ISSUE_MARKERS.keySet());
            ISSUE_MARKERS.clear();
        });
    }

    private static <P extends CustomPacketPayload> void registerHandler(
        ResourceLocation id, FriendlyByteBuf.Reader<P> reader, PacketHandler<P> handler
    ) {
        ClientPlayNetworking.registerGlobalReceiver(id, (client, handler1, buf, responseSender) -> {
            final P payload = reader.apply(buf);
            client.execute(() -> handler.handle(client, responseSender, payload));
        });
    }

    @FunctionalInterface
    private interface PacketHandler<P extends CustomPacketPayload> {
        void handle(Minecraft minecraft, PacketSender responder, P payload);
    }
}
