package io.github.gaming32.mc2p2.generator;

import net.minecraft.ChatFormatting;
import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

public enum IssueLevel implements StringRepresentable {
    INFO(ChatFormatting.AQUA),
    WARN(ChatFormatting.GOLD),
    ERROR(ChatFormatting.RED);

    public final ChatFormatting color;
    private final String lowercase;

    IssueLevel(ChatFormatting color) {
        this.color = color;
        lowercase = name().toLowerCase(Locale.ROOT);
    }

    @NotNull
    @Override
    public String getSerializedName() {
        return lowercase;
    }
}
