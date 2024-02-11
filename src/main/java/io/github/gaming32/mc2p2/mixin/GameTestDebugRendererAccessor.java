package io.github.gaming32.mc2p2.mixin;

import net.minecraft.client.renderer.debug.GameTestDebugRenderer;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(GameTestDebugRenderer.class)
public interface GameTestDebugRendererAccessor {
    @Accessor
    Map<BlockPos, ?> getMarkers();
}
