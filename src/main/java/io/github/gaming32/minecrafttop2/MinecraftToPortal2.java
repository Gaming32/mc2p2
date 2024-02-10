package io.github.gaming32.minecrafttop2;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.logging.LogUtils;
import io.github.gaming32.minecrafttop2.steam.SteamGames;
import io.github.gaming32.minecrafttop2.steam.SteamUtil;
import io.github.gaming32.minecrafttop2.vmf.MaterialInfo;
import io.github.gaming32.minecrafttop2.vmf.SimpleBrush;
import io.github.gaming32.minecrafttop2.vmf.SourceEntity;
import io.github.gaming32.minecrafttop2.vmf.SourceMap;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.platinumdigitalgroup.jvdf.VDFWriter;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class MinecraftToPortal2 implements ModInitializer {
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
                            .executes(MinecraftToPortal2::generateMap)
                        )
                    )
                )
            );
        });
    }

    private static int generateMap(CommandContext<CommandSourceStack> context) {
        generateMap("mc2p2_test_map");
        compileMap("mc2p2_test_map", true, context.getSource().getServer(), t -> {
            if (t == null) {
                LOGGER.info("Finished compiling map");
            }
        });
        context.getSource().sendSuccess(() -> Component.literal("Hi"), false);
        return 1;
    }

    public static void generateMap(String mapName) {
        if (SteamGames.PORTAL_2_PATH == null) return;
        final Path mapPath = SteamGames.PORTAL_2_PATH.resolve("sdk_content/maps/" + mapName + ".vmf");
        final MaterialInfo wallMaterial = new MaterialInfo(
            "TILE/WHITE_WALL_TILE003A",
            MaterialInfo.UvAxis.DEFAULT,
            MaterialInfo.UvAxis.shift(-256)
        );
        final SourceMap map = SourceMap.builder()
            .brush(new SimpleBrush(
                new AABB(-512, 0, -512, 512, 64, 512),
                Map.of(Direction.UP, MaterialInfo.defaultFor("TILE/WHITE_FLOOR_TILE002A"))
            ))
            .brush(new SimpleBrush(
                new AABB(-512 - 64, 64, -512, -512, 512 + 64, 512),
                Map.of(Direction.EAST, wallMaterial)
            ))
            .brush(new SimpleBrush(
                new AABB(512 + 64, 64, -512, 512, 512 + 64, 512),
                Map.of(Direction.WEST, wallMaterial)
            ))
            .brush(new SimpleBrush(
                new AABB(-512, 64, -512 - 64, 512, 512 + 64, -512),
                Map.of(Direction.SOUTH, wallMaterial)
            ))
            .brush(new SimpleBrush(
                new AABB(-512, 64, 512 + 64, 512, 512 + 64, 512),
                Map.of(Direction.NORTH, wallMaterial)
            ))
            .brush(new SimpleBrush(
                new AABB(-512, 512 + 64, -512, 512, 512 + 128, 512),
                Map.of(Direction.DOWN, MaterialInfo.defaultFor("TILE/WHITE_CEILING_TILE002A"))
            ))
            .entity(SourceEntity.builder("info_player_start")
                .origin(new Vec3(0, 64, 0))
                .build()
            )
            .build();
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
            .exceptionally(t -> {
                LOGGER.error("Failed to compile {}", mapName, t);
                onFinishCompile.accept(t);
                return null;
            });
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
