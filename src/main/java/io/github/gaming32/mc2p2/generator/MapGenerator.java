package io.github.gaming32.mc2p2.generator;

import com.demonwav.mcdev.annotations.Translatable;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import io.github.gaming32.mc2p2.util.MC2P2Util;
import io.github.gaming32.mc2p2.vmf.MaterialInfo;
import io.github.gaming32.mc2p2.vmf.SimpleBrush;
import io.github.gaming32.mc2p2.vmf.SourceEntity;
import io.github.gaming32.mc2p2.vmf.SourceMap;
import io.github.gaming32.mc2p2.vmf.SourceUtil;
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.IntIntPair;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

public class MapGenerator {
    private static final Map<Block, MaterialSet> BRUSH_BLOCKS = ImmutableMap.of(
        Blocks.WHITE_CONCRETE, new MaterialSet(
            "TILE/WHITE_WALL_TILE003A",
            "TILE/WHITE_FLOOR_TILE002A",
            "TILE/WHITE_CEILING_TILE002A"
        ),
        Blocks.BLACK_CONCRETE, new MaterialSet(
            "METAL/BLACK_WALL_METAL_002C",
            "METAL/BLACK_FLOOR_METAL_001B",
            "METAL/BLACK_CEILING_METAL_001B"
        )
    );
    private static final Map<Block, String> DOOR_BLOCKS = ImmutableMap.of(
        Blocks.OAK_DOOR, "instances/p2editor/door_frame_white.vmf",
        Blocks.DARK_OAK_DOOR, "instances/p2editor/door_frame_black.vmf"
    );
    private static final Int2ObjectMap<LabFreeSpace> LAB_FREE_SPACES = Util.make(new Int2ObjectArrayMap<>(4), map -> {
        map.put(4, new LabFreeSpace(2, 2));
        map.put(3, new LabFreeSpace(3, 1));
        map.put(2, new LabFreeSpace(2, 1));
        map.put(1, new LabFreeSpace(4, 1));
    });

    private final ServerLevel level;
    private final BoundingBox area;
    private final AABB aabb;
    private final IssueConsumer issueConsumer;

    private final SourceMap.Builder map;
    private final Set<BlockPos> usedBlocks = new HashSet<>();
    private final Multimap<Block, BlockPos> blockLookup = LinkedHashMultimap.create();

    private final List<SourceEntity.EntityConnection> autoConnections = new ArrayList<>();

    private final Map<BlockPos, String> entityNamesPerBlock = new HashMap<>();
    private int entityNameId = 1;

    private final Map<BlockPos, Direction> thinBrushBlocks = new HashMap<>();

    private final Map<BlockPos, BlockState> blockStateCache;

    public MapGenerator(ServerLevel level, BoundingBox area, IssueConsumer issueConsumer) {
        this.level = level;
        this.area = area;
        this.aabb = AABB.of(area);
        this.issueConsumer = issueConsumer;
        this.map = initializeMap();

        this.blockStateCache = Maps.newHashMapWithExpectedSize(area.getXSpan() * area.getYSpan() * area.getZSpan());
    }

    public SourceMap generate() {
        initializeBlockLookup();
        generateLabs();
        scanForBrushes();
        generateDoors();
        generateButtons();
        scanForThinBrushes();
        convertEntities();
        for (final var entry : blockLookup.asMap().entrySet()) {
            issueConsumer.issue(
                IssueLevel.INFO,
                Component.translatable("mc2p2.issue.message.unknown_block", entry.getKey().getName()),
                entry.getValue()
            );
        }
        if (!autoConnections.isEmpty()) {
            generateLogicAuto();
        }
        return map.build();
    }

    private void generateButtons() {
        for (final BlockPos button : blockLookup.removeAll(Blocks.OAK_BUTTON)) {
            final BlockState state = getBlockState(button);
            final Direction facing = state.getValue(ButtonBlock.FACING);
            final AttachFace face = state.getValue(ButtonBlock.FACE);
            final Vec3 origin = switch (face) {
                case FLOOR, CEILING -> Vec3.upFromBottomCenterOf(button, face == AttachFace.CEILING ? 1 : 0).relative(facing, 0.25);
                case WALL -> Vec3.atCenterOf(button).relative(facing.getOpposite(), 0.5);
            };
            final float yRotOffset = face == AttachFace.WALL ? 180 : 0;
            map.entity(SourceEntity.builder("prop_button")
                .origin(SourceUtil.transform(aabb, origin))
                .angles(new Vec3(
                    face == AttachFace.WALL ? 90 : 0,
                    SourceUtil.transformRotation(facing.getOpposite().toYRot() + yRotOffset),
                    face == AttachFace.CEILING ? 180 : 0
                ))
                .property("Delay", "1")
                .build()
            );
            usedBlocks.add(button);
        }
    }

    @SuppressWarnings("deprecation")
    private void generateLabs() {
        final Collection<BlockPos> glassBlocks = blockLookup.get(Blocks.GLASS);
        for (final BlockPos pane : blockLookup.removeAll(Blocks.GLASS_PANE)) {
            if (usedBlocks.contains(pane)) continue;
            final Direction facing = getGlassPaneDirection(pane, true);
            if (facing == null) continue;
            boolean foundMatch = false;
            for (int width = 4; width >= 1; width--) {
                final BoundingBox match = MC2P2Util.matchBlocks(
                    pane, IntIntPair.of(width, 2), facing.getCounterClockWise(), Direction.UP,
                    otherPos -> otherPos.equals(pane) || getGlassPaneDirection(otherPos, false) == facing
                );
                if (match == null) continue;
                foundMatch = true;
                BlockPos.betweenClosedStream(match)
                    .map(BlockPos::immutable)
                    .forEach(usedBlocks::add);
                BlockPos.betweenClosedStream(MC2P2Util.moved(match, facing.getOpposite()))
                    .map(BlockPos::immutable)
                    .forEach(e -> {
                        usedBlocks.add(e);
                        glassBlocks.remove(e);
                    });
                final LabFreeSpace labFreeSpace = LAB_FREE_SPACES.get(width);
                final boolean hasRoomBehind = BlockPos.betweenClosedStream(
                    MC2P2Util.moved(match, facing.getOpposite(), labFreeSpace.depth)
                        .encapsulate(pane.relative(facing.getOpposite(), 2))
                ).map(this::getBlockState).allMatch(s -> s.isAir() && !s.is(Blocks.VOID_AIR));
                if (!hasRoomBehind) {
                    issueConsumer.issue(
                        IssueLevel.WARN,
                        Component.translatable(
                            "mc2p2.issue.message.observation_room_no_room"
                        ),
                        List.of(pane)
                    );
                }
                BlockPos.betweenClosedStream(
                    MC2P2Util.copy(match)
                        .encapsulate(new BlockPos(match.minX(), match.minY() - 1, match.minZ()).relative(facing.getCounterClockWise()))
                        .encapsulate(new BlockPos(match.maxX(), match.maxY() + labFreeSpace.height, match.maxZ()).relative(facing.getClockWise()))
                ).filter(p -> !match.isInside(p)).forEach(adjacentPos -> {
                    final Block adjacentBlock = getBlockState(adjacentPos).getBlock();
                    if (!BRUSH_BLOCKS.containsKey(adjacentBlock)) return;
                    final BlockPos immutable = adjacentPos.immutable();
                    blockLookup.remove(adjacentBlock, immutable);
                    thinBrushBlocks.put(immutable, facing);
                });
                map.entity(SourceEntity.builder("func_instance")
                    .origin(SourceUtil.transform(aabb, new Vec3(match.minX(), match.minY() + 1, match.minZ()).relative(facing.getClockWise(), 0.5 * width)))
                    .angles(new Vec3(0, SourceUtil.transformRotation(facing.toYRot()), 0))
                    .property("file", "instances/labs/observation_room_" + width * 64 + "x128_1.vmf")
                    .build()
                );
                break;
            }
            if (!foundMatch) {
                issue(IssueLevel.ERROR, "no_observation_room", pane);
            }
        }
    }

    @Nullable
    private Direction getGlassPaneDirection(BlockPos pos, boolean createIssues) {
        final Direction direction = getGlassPaneDirectionNoBlockCheck(pos, createIssues);
        if (direction != null && !getBlockState(pos.relative(direction.getOpposite())).is(Blocks.GLASS)) {
            if (createIssues) {
                issue(IssueLevel.ERROR, "glass_pane_not_glass", pos);
            }
            return null;
        }
        return direction;
    }

    @Nullable
    private Direction getGlassPaneDirectionNoBlockCheck(BlockPos pos, boolean createIssues) {
        final BlockState state = getBlockState(pos);
        if (!state.is(Blocks.GLASS_PANE)) {
            return null;
        }
        final boolean north = state.getValue(IronBarsBlock.NORTH);
        final boolean south = state.getValue(IronBarsBlock.SOUTH);
        final boolean east = state.getValue(IronBarsBlock.EAST);
        final boolean west = state.getValue(IronBarsBlock.WEST);
        final int directionCount = z2i(north) + z2i(south) + z2i(east) + z2i(west);
        if (directionCount == 3) {
            if (!north) {
                return Direction.NORTH;
            }
            if (!south) {
                return Direction.SOUTH;
            }
            if (!east) {
                return Direction.EAST;
            }
            if (!west) {
                return Direction.WEST;
            }
        }
        if (createIssues) {
            if (directionCount < 2) {
                issue(IssueLevel.ERROR, "glass_pane_small", pos);
            } else if (directionCount == 2) {
                issue(IssueLevel.ERROR, "glass_pane_not_glass", pos);
            } else if (directionCount > 3) {
                issue(IssueLevel.ERROR, "glass_pane_too_linked", pos);
            }
        }
        return null;
    }

    private void convertEntities() {
        for (final Entity entity : level.getEntities(null, aabb)) {
            if (entity instanceof ArmorStand armorStand) {
                map.entity(SourceEntity.builder("info_player_start")
                    .origin(SourceUtil.transform(aabb, armorStand.position()))
                    .angles(new Vec3(0, SourceUtil.transformRotation(armorStand.getYRot()), 0))
                    .build()
                );
                map.entity(SourceEntity.builder("weapon_portalgun")
                    .origin(SourceUtil.transform(aabb, armorStand.position()))
                    .property("CanFirePortal1", "1")
                    .property("CanFirePortal2", "1")
                    .build()
                );
            }
        }
    }

    private void generateLogicAuto() {
        final SourceEntity.Builder entity = SourceEntity.builder("logic_auto");
        for (final SourceEntity.EntityConnection connection : autoConnections) {
            entity.connection("OnMapSpawn", connection);
        }
        map.entity(entity.build());
    }

    private void generateDoors() {
        for (final var doorBlockType : DOOR_BLOCKS.entrySet()) {
            for (final BlockPos door : blockLookup.removeAll(doorBlockType.getKey())) {
                if (!usedBlocks.add(door)) continue;
                final BlockState state = getBlockState(door);
                if (state.getValue(DoorBlock.HALF) != DoubleBlockHalf.LOWER) continue;
                final Direction facing = state.getValue(DoorBlock.FACING);
                if (state.getValue(DoorBlock.HINGE) != DoorHingeSide.LEFT) {
                    final BlockState neighbor = getBlockState(door.relative(facing.getCounterClockWise()));
                    if (
                        !neighbor.is(doorBlockType.getKey())
                            || neighbor.getValue(DoorBlock.FACING) != facing
                            || neighbor.getValue(DoorBlock.HINGE) == DoorHingeSide.RIGHT
                    ) {
                        issue(IssueLevel.ERROR, "unpaired_right_door", door);
                    }
                    continue;
                }
                final BlockPos neighborPos = door.relative(facing.getClockWise());
                final BlockState neighbor = getBlockState(neighborPos);
                if (
                    !neighbor.is(doorBlockType.getKey())
                        || neighbor.getValue(DoorBlock.FACING) != facing
                        || neighbor.getValue(DoorBlock.HINGE) == DoorHingeSide.LEFT
                ) {
                    issue(IssueLevel.ERROR, "unpaired_left_door", door);
                    continue;
                }
                final double rotation = SourceUtil.transformRotation(facing.toYRot());
                final Vec3 origin = Vec3.atLowerCornerOf(door);
                map.entity(SourceEntity.builder("func_instance")
                    .origin(SourceUtil.transform(aabb, origin.add(0, 1, 0).relative(facing.getOpposite(), 2)))
                    .angles(new Vec3(-90, rotation, 0))
                    .property("file", doorBlockType.getValue())
                    .property("fixup_style", "0")
                    .build()
                );
                map.entity(SourceEntity.builder("func_instance")
                    .origin(SourceUtil.transform(aabb, origin.add(0, 1, 0).relative(facing, 0.5)))
                    .angles(new Vec3(-90, Mth.wrapDegrees(rotation + 180), 0))
                    .property("file", doorBlockType.getValue())
                    .property("fixup_style", "0")
                    .build()
                );
                final String name = getEntityName(door);
                map.entity(SourceEntity.builder("prop_testchamber_door")
                    .name(name)
                    .origin(SourceUtil.transform(aabb, origin.relative(facing.getOpposite(), 0.75)))
                    .angles(new Vec3(0, Mth.wrapDegrees(rotation + 180), 0))
                    .property("AreaPortalFadeEnd", "0")
                    .property("AreaPortalFadeStart", "0")
                    .property("UseAreaPortalFade", "0")
                    .build()
                );

                final List<BlockPos> doorBlocks = List.of(door, door.above(), neighborPos, neighborPos.above());
                if (state.getValue(DoorBlock.OPEN) || neighbor.getValue(DoorBlock.OPEN)) {
                    if (state.getValue(DoorBlock.OPEN) != neighbor.getValue(DoorBlock.OPEN)) {
                        issue(IssueLevel.WARN, "mismatched_door_open", doorBlocks);
                    }
                    autoConnections.add(new SourceEntity.EntityConnection(name, "Open"));
                }
                usedBlocks.addAll(doorBlocks);
                for (final BlockPos doorBlock : doorBlocks) {
                    if (doorBlock != door) {
                        entityNamesPerBlock.put(doorBlock, name);
                    }
                }
            }
        }
    }

    private void initializeBlockLookup() {
        BlockPos.betweenClosedStream(area).forEach(pos -> {
            final BlockState state = getBlockState(pos);
            if (state.isAir()) return;
            blockLookup.put(state.getBlock(), pos.immutable());
        });
    }

    private void scanForBrushes() {
        final List<Direction> directions = Arrays.asList(MC2P2Util.DIRECTIONS);
        for (final var brushBlock : BRUSH_BLOCKS.entrySet()) {
            final Collection<BlockPos> inBlock = blockLookup.removeAll(brushBlock.getKey());
            for (final BlockPos pos : inBlock) {
                if (!usedBlocks.add(pos)) continue;
                scanBrush(
                    pos, inBlock::contains, brushBlock.getValue(), directions,
                    bounds -> new AABB(
                        SourceUtil.transform(aabb, new Vec3(bounds.minX(), bounds.minY(), bounds.minZ())),
                        SourceUtil.transform(aabb, new Vec3(bounds.maxX() + 1, bounds.maxY() + 1, bounds.maxZ() + 1))
                    )
                );
            }
        }
    }

    private void scanForThinBrushes() {
        for (final var thinBrush : thinBrushBlocks.entrySet()) {
            if (!usedBlocks.add(thinBrush.getKey())) continue;
            final Block targetBlock = getBlockState(thinBrush.getKey()).getBlock();
            final Direction targetDir = thinBrush.getValue();
            scanBrush(
                thinBrush.getKey(),
                pos -> getBlockState(pos).is(targetBlock) && thinBrushBlocks.get(pos) == targetDir,
                BRUSH_BLOCKS.get(targetBlock), MC2P2Util.ADJACENT_DIRECTIONS.get(targetDir),
                bounds -> {
                    if (targetDir.getAxisDirection() == Direction.AxisDirection.POSITIVE) {
                        return new AABB(
                            SourceUtil.transform(aabb, new Vec3(
                                bounds.minX() + targetDir.getStepX() * 0.75,
                                bounds.minY() + targetDir.getStepY() * 0.75,
                                bounds.minZ() + targetDir.getStepZ() * 0.75
                            )),
                            SourceUtil.transform(aabb, new Vec3(bounds.maxX() + 1, bounds.maxY() + 1, bounds.maxZ() + 1))
                        );
                    } else {
                        return new AABB(
                            SourceUtil.transform(aabb, new Vec3(bounds.minX(), bounds.minY(), bounds.minZ())),
                            SourceUtil.transform(aabb, new Vec3(
                                // The step is negative, so use addition
                                bounds.maxX() + 1 + targetDir.getStepX() * 0.75,
                                bounds.maxY() + 1 + targetDir.getStepY() * 0.75,
                                bounds.maxZ() + 1 + targetDir.getStepZ() * 0.75
                            ))
                        );
                    }
                }
            );
        }
    }

    @SuppressWarnings("deprecation")
    private void scanBrush(
        BlockPos start,
        Predicate<BlockPos> inBlock,
        MaterialSet materials,
        List<Direction> directions,
        Function<BoundingBox, AABB> boundsTransformer
    ) {
        final BoundingBox brushBounds = new BoundingBox(start);
        for (final Direction dir : directions) {
            final int end = MC2P2Util.getSide(area, dir);
            while (MC2P2Util.getSide(brushBounds, dir) != end) {
                final BoundingBox newBlocks = MC2P2Util.oneSided(brushBounds, dir).move(dir.getNormal());
                if (BlockPos.betweenClosedStream(newBlocks).anyMatch(b -> usedBlocks.contains(b) || !inBlock.test(b))) {
                    break;
                }
                brushBounds.encapsulate(newBlocks);
                BlockPos.betweenClosedStream(newBlocks).forEach(p -> usedBlocks.add(p.immutable()));
            }
        }
        map.brush(new SimpleBrush(boundsTransformer.apply(brushBounds), materials.map));
    }

    private BlockState getBlockState(BlockPos pos) {
        if (!area.isInside(pos)) {
            return Blocks.VOID_AIR.defaultBlockState();
        }
        BlockState state = blockStateCache.get(pos);
        if (state == null) {
            blockStateCache.put(pos.immutable(), state = level.getBlockState(pos));
        }
        return state;
    }

    private void issue(IssueLevel level, @Translatable(prefix = "mc2p2.issue.message.") String message, BlockPos... blocks) {
        issue(level, message, Arrays.asList(blocks));
    }

    private void issue(IssueLevel level, @Translatable(prefix = "mc2p2.issue.message.") String message, Collection<BlockPos> blocks) {
        issueConsumer.issue(level, Component.translatable("mc2p2.issue.message." + message), blocks);
    }

    private String getEntityName(BlockPos pos) {
        return entityNamesPerBlock.computeIfAbsent(pos, k -> "named_entity_" + entityNameId++);
    }

    private SourceMap.Builder initializeMap() {
//        final MaterialInfo skyboxMaterial = new MaterialInfo(
//            "ANIM_WP/FRAMEWORK/BACKPANELS",
//            MaterialInfo.UvAxis.DEFAULT,
//            MaterialInfo.UvAxis.shift(-256)
//        );
        final MaterialInfo skyboxMaterial = MaterialInfo.SKYBOX;
        final int xSize64 = area.getXSpan() * 64;
        final int ySize64 = area.getYSpan() * 64;
        final int zSize64 = area.getZSpan() * 64;
        return SourceMap.builder()
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
    }

    private static int z2i(boolean z) {
        return z ? 1 : 0;
    }

    private record MaterialSet(String wall, String floor, String ceiling, Map<Direction, MaterialInfo> map) {
        public MaterialSet(String wall, String floor, String ceiling) {
            this(wall, floor, ceiling, toMap(wall, floor, ceiling));
        }

        private static Map<Direction, MaterialInfo> toMap(String wall, String floor, String ceiling) {
            final Map<Direction, MaterialInfo> result = new EnumMap<>(Direction.class);
            final MaterialInfo wallMaterial = new MaterialInfo(wall, MaterialInfo.UvAxis.DEFAULT, MaterialInfo.UvAxis.shift(256));
            final MaterialInfo floorMaterial = MaterialInfo.defaultFor(floor);
            final MaterialInfo ceilingMaterial = MaterialInfo.defaultFor(ceiling);
            for (final Direction dir : MC2P2Util.DIRECTIONS) {
                result.put(dir, switch (dir.getAxis().getPlane()) {
                    case HORIZONTAL -> wallMaterial;
                    case VERTICAL -> switch (dir.getAxisDirection()) {
                        case POSITIVE -> floorMaterial;
                        case NEGATIVE -> ceilingMaterial;
                    };
                });
            }
            return result;
        }
    }

    /**
     * @param depth The number of free blocks behind the glass panes needed.
     * @param height The number of thin blocks needed above the lab
     */
    private record LabFreeSpace(int depth, int height) {
    }
}
