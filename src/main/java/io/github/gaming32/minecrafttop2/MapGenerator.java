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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.EnumMap;
import java.util.HashSet;
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
    private final Set<BlockPos> brushBlocks = new HashSet<>();
    private final Set<BlockPos> nonBrushBlocks = new HashSet<>();

    public MapGenerator(ServerLevel level, BoundingBox area) {
        this.level = level;
        this.area = area;
        this.aabb = AABB.of(area);
        this.map = initializeMap();
    }

    public SourceMap generate() {
        scanForBrushes();
        for (final Entity entity : level.getEntities(null, aabb)) {
            if (entity instanceof ArmorStand armorStand) {
                map.entity(SourceEntity.builder("info_player_start")
                    .origin(SourceUtil.transform(aabb, armorStand.position()))
                    .angles(new Vec3(0, armorStand.getYRot(), 0))
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
        return map.build();
    }

    private void scanForBrushes() {
        BlockPos.betweenClosedStream(area).forEach(pos -> {
            final BlockState state = level.getBlockState(pos);
            if (state.isAir()) return;
            final MaterialSet materials = BRUSH_BLOCKS.get(state.getBlock());
            if (materials != null) {
                if (!brushBlocks.add(pos.immutable())) return;
                scanBrush(pos, state.getBlock(), materials);
            } else {
                nonBrushBlocks.add(pos.immutable());
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
                if (BlockPos.betweenClosedStream(newBlocks).anyMatch(b -> !level.getBlockState(b).is(type))) {
                    break;
                }
                brushBounds.encapsulate(newBlocks);
                BlockPos.betweenClosedStream(newBlocks).forEach(p -> brushBlocks.add(p.immutable()));
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
            final MaterialInfo wallMaterial = MaterialInfo.defaultFor(wall);
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
