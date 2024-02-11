package io.github.gaming32.minecrafttop2;

import io.github.gaming32.minecrafttop2.util.MC2P2Util;
import io.github.gaming32.minecrafttop2.vmf.MaterialInfo;
import io.github.gaming32.minecrafttop2.vmf.SimpleBrush;
import io.github.gaming32.minecrafttop2.vmf.SourceEntity;
import io.github.gaming32.minecrafttop2.vmf.SourceMap;
import io.github.gaming32.minecrafttop2.vmf.SourceUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MapGenerator {
    private static final Map<Block, MaterialSet> BRUSH_BLOCKS = Map.of(
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

    private final ServerLevel level;
    private final BoundingBox area;
    private final AABB aabb;
    private final SourceMap.Builder map;
    private final Set<BlockPos> usedNonAir = new HashSet<>();
    private final Set<BlockPos> unusedNonAir = new HashSet<>();
    private final List<SourceEntity.EntityConnection> autoConnections = new ArrayList<>();
    private int entityNameId = 1;

    public MapGenerator(ServerLevel level, BoundingBox area) {
        this.level = level;
        this.area = area;
        this.aabb = AABB.of(area);
        this.map = initializeMap();
    }

    public SourceMap generate() {
        scanForBrushes();
        generateDoors();
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
        if (!autoConnections.isEmpty()) {
            generateLogicAuto();
        }
        return map.build();
    }

    private void generateLogicAuto() {
        final SourceEntity.Builder entity = SourceEntity.builder("logic_auto");
        for (final SourceEntity.EntityConnection connection : autoConnections) {
            entity.connection("OnMapSpawn", connection);
        }
        map.entity(entity.build());
    }

    private void generateDoors() {
        final List<BlockPos> doors = unusedNonAir.stream().filter(pos -> {
            final BlockState state = level.getBlockState(pos);
            if (!state.is(Blocks.OAK_DOOR) && !state.is(Blocks.DARK_OAK_DOOR)) {
                return false;
            }
            return state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER && state.getValue(DoorBlock.HINGE) == DoorHingeSide.LEFT;
        }).toList();
        for (final BlockPos door : doors) {
            final BlockState state = level.getBlockState(door);
            final String color = state.is(Blocks.OAK_DOOR) ? "white.vmf" : "black.vmf";
            final Direction facing = state.getValue(DoorBlock.FACING);
            final double rotation = SourceUtil.transformRotation(facing.toYRot());
            final Vec3 origin = Vec3.atLowerCornerOf(door);
            map.entity(SourceEntity.builder("func_instance")
                .origin(SourceUtil.transform(aabb, origin.add(0, 1, 0).relative(facing.getOpposite(), 2)))
                .angles(new Vec3(-90, rotation, 0))
                .property("file", "instances/p2editor/door_frame_" + color)
                .property("fixup_style", "0")
                .build()
            );
            final String name = nextEntityName();
            map.entity(SourceEntity.builder("prop_testchamber_door")
                .name(name)
                .origin(SourceUtil.transform(aabb, origin.relative(facing.getOpposite(), 0.75)))
                .angles(new Vec3(0, Mth.wrapDegrees(rotation + 180), 0))
                .property("AreaPortalFadeEnd", "0")
                .property("AreaPortalFadeStart", "0")
                .property("UseAreaPortalFade", "0")
                .build()
            );
            if (state.getValue(DoorBlock.OPEN)) {
                autoConnections.add(new SourceEntity.EntityConnection(name, "Open"));
            }

            markUsed(door);
            markUsed(door.above());
            final BlockPos rightHalf = door.relative(state.getValue(DoorBlock.FACING).getCounterClockWise());
            markUsed(rightHalf);
            markUsed(rightHalf.above());
        }
    }

    private void scanForBrushes() {
        BlockPos.betweenClosedStream(area).forEach(pos -> {
            final BlockState state = level.getBlockState(pos);
            if (state.isAir()) return;
            final MaterialSet materials = BRUSH_BLOCKS.get(state.getBlock());
            if (materials != null) {
                if (!usedNonAir.add(pos.immutable())) return;
                scanBrush(pos, state.getBlock(), materials);
            } else {
                unusedNonAir.add(pos.immutable());
            }
        });
    }

    @SuppressWarnings("deprecation")
    private void scanBrush(BlockPos start, Block type, MaterialSet materials) {
        final BoundingBox brushBounds = new BoundingBox(start);
        for (final Direction dir : MC2P2Util.DIRECTIONS) {
            final int end = MC2P2Util.getSide(area, dir);
            while (MC2P2Util.getSide(brushBounds, dir) != end) {
                final BoundingBox newBlocks = MC2P2Util.oneSided(brushBounds, dir).move(dir.getNormal());
                if (BlockPos.betweenClosedStream(newBlocks).anyMatch(b -> usedNonAir.contains(b) || !level.getBlockState(b).is(type))) {
                    break;
                }
                brushBounds.encapsulate(newBlocks);
                BlockPos.betweenClosedStream(newBlocks).forEach(p -> usedNonAir.add(p.immutable()));
            }
        }
        map.brush(new SimpleBrush(
            new AABB(
                SourceUtil.transform(aabb, new Vec3(brushBounds.minX(), brushBounds.minY(), brushBounds.minZ())),
                SourceUtil.transform(aabb, new Vec3(brushBounds.maxX() + 1, brushBounds.maxY() + 1, brushBounds.maxZ() + 1))
            ),
            materials.map
        ));
    }

    private void markUsed(BlockPos pos) {
        unusedNonAir.remove(pos);
        usedNonAir.add(pos);
    }

    private String nextEntityName() {
        return "named_entity_" + entityNameId++;
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
}
