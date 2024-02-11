package io.github.gaming32.mc2p2.generator;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.Collection;

@FunctionalInterface
public interface IssueConsumer {
    void issue(IssueLevel level, Component message, Collection<BlockPos> blocks);
}
