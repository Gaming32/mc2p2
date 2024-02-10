package io.github.gaming32.minecrafttop2;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import io.github.gaming32.minecrafttop2.steam.SteamGames;
import io.github.gaming32.minecrafttop2.steam.SteamUtil;
import io.github.gaming32.minecrafttop2.vmf.MaterialInfo;
import io.github.gaming32.minecrafttop2.vmf.SimpleBrush;
import io.github.gaming32.minecrafttop2.vmf.SourceEntity;
import io.github.gaming32.minecrafttop2.vmf.SourceMap;
import io.github.gaming32.minecrafttop2.vmf.SourceUtil;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
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

    private static int generateMap(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final BoundingBox mapArea = BoundingBox.fromCorners(
            BlockPosArgument.getLoadedBlockPos(context, "from"),
            BlockPosArgument.getLoadedBlockPos(context, "to")
        );
        final String mapName = StringArgumentType.getString(context, "name"); // TODO: Validation
        generateMap(mapName, context.getSource().getLevel(), mapArea);
        compileMap(mapName, true, context.getSource().getServer(), t -> {
            if (t == null) {
                LOGGER.info("Finished compiling map");
            }
        });
        context.getSource().sendSuccess(() -> Component.literal("Hi"), false);
        return 1;
    }

    public static void generateMap(String mapName, ServerLevel level, BoundingBox area) {
        if (SteamGames.PORTAL_2_PATH == null) return;
        final Path mapPath = SteamGames.PORTAL_2_PATH.resolve("sdk_content/maps/" + mapName + ".vmf");
        final MaterialInfo skyboxMaterial = new MaterialInfo(
            "ANIM_WP/FRAMEWORK/BACKPANELS",
            MaterialInfo.UvAxis.DEFAULT,
            MaterialInfo.UvAxis.shift(-256)
        );
        final int xSize64 = area.getXSpan() * 64;
        final int ySize64 = area.getYSpan() * 64;
        final int zSize64 = area.getZSpan() * 64;
        final SourceMap.Builder map = SourceMap.builder()
            .brush(new SimpleBrush(
                new AABB(0, -16, 0, xSize64, 0, zSize64),
                Map.of(Direction.UP, skyboxMaterial)
            ))
            .brush(new SimpleBrush(
                new AABB(-16, 0, 0, 0, ySize64, zSize64),
                Map.of(Direction.EAST, skyboxMaterial)
            ))
            .brush(new SimpleBrush(
                new AABB(xSize64, 0, 0, xSize64 + 16, ySize64, zSize64),
                Map.of(Direction.WEST, skyboxMaterial)
            ))
            .brush(new SimpleBrush(
                new AABB(0, 0, -16, xSize64, ySize64, 0),
                Map.of(Direction.SOUTH, skyboxMaterial)
            ))
            .brush(new SimpleBrush(
                new AABB(0, 0, zSize64, xSize64, ySize64, zSize64 + 16),
                Map.of(Direction.NORTH, skyboxMaterial)
            ))
            .brush(new SimpleBrush(
                new AABB(0, ySize64, 0, xSize64, ySize64 + 16, zSize64),
                Map.of(Direction.DOWN, skyboxMaterial)
            ));
        final AABB aabb = AABB.of(area);
        for (final Entity entity : level.getEntities(null, aabb)) {
            if (entity instanceof ArmorStand armorStand) {
                map.entity(SourceEntity.builder("info_player_start")
                    .origin(SourceUtil.transform(aabb, armorStand.position()))
                    .angles(new Vec3(0, armorStand.getYRot(), 0))
                    .build()
                );
            }
        }
        try {
            Files.writeString(mapPath, new VDFWriter().write(map.build().toVmf(), true), StandardCharsets.UTF_8);
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
