package io.github.gaming32.mc2p2;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import io.github.gaming32.mc2p2.generator.IssueConsumer;
import io.github.gaming32.mc2p2.generator.MapGenerator;
import io.github.gaming32.mc2p2.network.ClearIssueMarkersPayload;
import io.github.gaming32.mc2p2.network.IssueMarkersPayload;
import io.github.gaming32.mc2p2.steam.SteamGames;
import io.github.gaming32.mc2p2.steam.SteamUtil;
import io.github.gaming32.mc2p2.vmf.SourceMap;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.Optionull;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.platinumdigitalgroup.jvdf.VDFWriter;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class MC2P2 implements ModInitializer {
    public static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public void onInitialize() {
        LOGGER.info("Steam location: {}", SteamUtil.STEAM_DIR);
        LOGGER.info("Portal 2 location: {}", SteamGames.PORTAL_2_PATH);

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(literal("mc2p2")
                .then(argument("from", BlockPosArgument.blockPos())
                    .then(argument("to", BlockPosArgument.blockPos())
                        .then(argument("name", StringArgumentType.word())
                            .executes(MC2P2::generateMap)
                        )
                    )
                )
            );
        });
    }

    private static int generateMap(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final PacketSender issueSender = Optionull.map(context.getSource().getPlayer(), ServerPlayNetworking::getSender);
        if (issueSender != null) {
            issueSender.sendPacket(ClearIssueMarkersPayload.INSTANCE);
        }
        final BoundingBox mapArea = BoundingBox.fromCorners(
            BlockPosArgument.getLoadedBlockPos(context, "from"),
            BlockPosArgument.getLoadedBlockPos(context, "to")
        );
        final String mapName = StringArgumentType.getString(context, "name"); // TODO: Validation
        context.getSource().sendSuccess(() -> Component.translatable("mc2p2.generate.starting"), false);
        generateMap(mapName, context.getSource().getLevel(), mapArea, (level, message, blocks) -> {
            final MutableComponent component = Component.translatable("mc2p2.issue.level." + level.getSerializedName(), message);
            component.withStyle(level.color);
            if (!blocks.isEmpty()) {
                final BlockPos pos = blocks.iterator().next();
                component.append(" ").append(
                    Component.translatable("mc2p2.issue.log.position", pos.getX(), pos.getY(), pos.getZ()).withStyle(s -> s
                        .applyFormat(ChatFormatting.UNDERLINE)
                        .withHoverEvent(new HoverEvent(
                            HoverEvent.Action.SHOW_TEXT,
                            Component.translatable("mc2p2.issue.log.teleport")
                        ))
                        .withClickEvent(new ClickEvent(
                            ClickEvent.Action.RUN_COMMAND,
                            "/tp " + pos.getX() + " " + pos.getY() + " " + pos.getZ()
                        ))
                    )
                );
            }
            context.getSource().sendSystemMessage(component);
            if (issueSender != null) {
                issueSender.sendPacket(new IssueMarkersPayload(level, message, blocks));
            }
        });
        context.getSource().sendSuccess(() -> Component.translatable("mc2p2.compile.starting"), false);
        compileMap(mapName, true, context.getSource().getServer(), t -> {
            if (t == null) {
                LOGGER.info("Compiled map successfully");
                context.getSource().sendSuccess(() -> Component.translatable("mc2p2.compile.success"), false);
            } else {
                LOGGER.error("Failed to compile map", t);
                context.getSource().sendFailure(Component.translatable("mc2p2.compile.error", t.getLocalizedMessage()));
            }
        });
        return 1;
    }

    public static void generateMap(String mapName, ServerLevel level, BoundingBox area, IssueConsumer issueConsumer) {
        if (SteamGames.PORTAL_2_PATH == null) return;
        final Path mapPath = SteamGames.PORTAL_2_PATH.resolve("sdk_content/maps/" + mapName + ".vmf");
        final SourceMap map = new MapGenerator(level, area, issueConsumer).generate();
        try {
            Files.writeString(mapPath, new VDFWriter().write(map.toVmf(), true), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.error("Failed to write map", e);
        }
    }

    public static void compileMap(
        String mapName, boolean run, Executor serverExecutor, Consumer<@Nullable Throwable> onFinishCompile
    ) {
        if (SteamGames.PORTAL_2_PATH == null) return;
        final Path mapPath = SteamGames.PORTAL_2_PATH.resolve("sdk_content/maps/" + mapName + ".vmf");
        final Path bspPath = mapPath.resolveSibling(mapName + ".bsp");
        final Path targetPath = SteamGames.PORTAL_2_PATH.resolve("portal2/maps").resolve(bspPath.getFileName());
        runCompilerStep("vbsp", mapPath)
            .thenCompose(p -> runCompilerStep("vvis", mapPath))
            .thenCompose(p -> runCompilerStep("vrad", mapPath))
            .thenRunAsync(() -> {
                try {
                    Files.copy(bspPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            })
            .thenRunAsync(() -> {
                onFinishCompile.accept(null);
                if (run) {
                    try {
                        new ProcessBuilder(
                            SteamGames.PORTAL_2_PATH.resolve("portal2.exe").toString(),
                            "-dev",
                            "-game", SteamGames.PORTAL_2_PATH.resolve("portal2").toString(),
                            "+map", mapName,
                            "+sv_lan", "1"
                        ).start();
                    } catch (IOException e) {
                        LOGGER.error("Failed to start game", e);
                    }
                }
            }, serverExecutor)
            .exceptionallyAsync(t -> {
                if (t instanceof CompletionException && t.getCause() != null) {
                    t = t.getCause();
                }
                onFinishCompile.accept(t);
                return null;
            }, serverExecutor);
    }

    private static CompletableFuture<Void> runCompilerStep(String executable, Path mapPath) {
        assert SteamGames.PORTAL_2_PATH != null;
        LOGGER.info("Starting {}...", executable);
        try {
            return new ProcessBuilder(
                SteamGames.PORTAL_2_PATH.resolve("bin/" + executable + ".exe").toString(),
                "-game", SteamGames.PORTAL_2_PATH.resolve("portal2").toString(),
                mapPath.toString()
            )
                .inheritIO()
                .start()
                .onExit()
                .thenAccept(process -> {
                    if (process.exitValue() != 0) {
                        throw new IllegalStateException(executable + " failed with exit code " + process.exitValue());
                    }
                });
        } catch (IOException e) {
            LOGGER.error("Failed to run compile step", e);
            return CompletableFuture.failedFuture(e);
        }
    }
}
