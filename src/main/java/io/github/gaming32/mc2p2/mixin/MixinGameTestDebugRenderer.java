package io.github.gaming32.mc2p2.mixin;

import io.github.gaming32.mc2p2.client.MC2P2Client;
import net.minecraft.client.renderer.debug.GameTestDebugRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameTestDebugRenderer.class)
public class MixinGameTestDebugRenderer {
    @Inject(method = "clear", at = @At("HEAD"))
    private void clearIssueMarkersMap(CallbackInfo ci) {
        MC2P2Client.ISSUE_MARKERS.clear();
    }
}
