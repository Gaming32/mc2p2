package io.github.gaming32.minecrafttop2;

import com.mojang.logging.LogUtils;
import io.github.gaming32.minecrafttop2.steam.SteamGames;
import io.github.gaming32.minecrafttop2.steam.SteamUtil;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import net.platinumdigitalgroup.jvdf.VDFNode;
import net.platinumdigitalgroup.jvdf.VDFWriter;
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

import static net.minecraft.commands.Commands.literal;

public class MinecraftToPortal2 implements ModInitializer {
    public static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public void onInitialize() {
        LOGGER.info("Steam location: {}", SteamUtil.STEAM_DIR);
        LOGGER.info("Portal 2 location: {}", SteamGames.PORTAL_2_PATH);
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(literal("mc2p2")
                .executes(context -> {
                    generateMap("mc2p2_test_map");
                    compileMap("mc2p2_test_map", true, context.getSource().getServer(), () -> {
                        LOGGER.info("Finished compiling map");
                    });
                    context.getSource().sendSuccess(() -> Component.literal("Hi"), false);
                    return 1;
                })
            );
        });
    }

    public static void generateMap(String mapName) {
        if (SteamGames.PORTAL_2_PATH == null) return;
        final Path mapPath = SteamGames.PORTAL_2_PATH.resolve("sdk_content/maps/" + mapName + ".vmf");
        final VDFNode resultNode = new VDFNode();
        resultNode.put("visgroups", new VDFNode());
        {
            final VDFNode world = new VDFNode();
            resultNode.put("world", world);
            world.put("id", "1");
            world.put("mapversion", "0");
            world.put("classname", "worldspawn");
            world.put("skyname", "sky_black_nofog");
            world.put("maxpropscreenwidth", "-1");
            world.put("detailvbsp", "detail.vbsp");
            world.put("detailmaterial", "detail/detailsprites");
            world.put("maxblobcount", "250");
            final MaterialInfo wallMaterial = new MaterialInfo(
                "TILE/WHITE_WALL_TILE003A",
                MaterialInfo.UvAxis.DEFAULT,
                MaterialInfo.UvAxis.shift(-128)
            );
            world.put("solid", new SimpleBrush(
                new AABB(-512, 0, -512, 512, 32, 512),
                Map.of(Direction.UP, MaterialInfo.defaultFor("TILE/WHITE_FLOOR_TILE002A"))
            ).createNode(2));
            world.put("solid", new SimpleBrush(
                new AABB(-512 - 32, 32, -512, -512, 512 + 32, 512),
                Map.of(Direction.EAST, wallMaterial)
            ).createNode(9));
            world.put("solid", new SimpleBrush(
                new AABB(512 + 32, 32, -512, 512, 512 + 32, 512),
                Map.of(Direction.WEST, wallMaterial)
            ).createNode(16));
            world.put("solid", new SimpleBrush(
                new AABB(-512, 32, -512 - 32, 512, 512 + 32, -512),
                Map.of(Direction.SOUTH, wallMaterial)
            ).createNode(23));
            world.put("solid", new SimpleBrush(
                new AABB(-512, 32, 512 + 32, 512, 512 + 32, 512),
                Map.of(Direction.NORTH, wallMaterial)
            ).createNode(30));
            world.put("solid", new SimpleBrush(
                new AABB(-512, 512 + 32, -512, 512, 512 + 64, 512),
                Map.of(Direction.DOWN, MaterialInfo.defaultFor("TILE/WHITE_CEILING_TILE002A"))
            ).createNode(37));
        }
        {
            final VDFNode infoPlayerStart = new VDFNode();
            resultNode.put("entity", infoPlayerStart);
            infoPlayerStart.put("id", "44");
            infoPlayerStart.put("classname", "info_player_start");
            infoPlayerStart.put("angles", "0 0 0");
            infoPlayerStart.put("origin", "0 0 32");
        }
        {
            final VDFNode cameras = new VDFNode();
            resultNode.put("cameras", cameras);
            cameras.put("activecamera", "-1");
        }
        {
            final VDFNode cordons = new VDFNode();
            resultNode.put("cordons", cordons);
            cordons.put("active", "0");
        }
        try {
            Files.writeString(mapPath, new VDFWriter().write(resultNode, true), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.error("Failed to write map", e);
        }
    }

    public static void compileMap(String mapName, boolean run, Executor serverExecutor, Runnable onFinishCompile) {
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
                onFinishCompile.run();
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
