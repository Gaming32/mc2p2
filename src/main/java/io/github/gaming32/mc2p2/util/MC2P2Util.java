package io.github.gaming32.mc2p2.util;

import com.google.common.collect.ImmutableMap;
import it.unimi.dsi.fastutil.ints.IntIntPair;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public class MC2P2Util {
    public static final Direction[] DIRECTIONS = Direction.values();
    public static final Map<Direction, List<Direction>> ADJACENT_DIRECTIONS = Util.make(() -> {
        final ImmutableMap.Builder<Direction, List<Direction>> result = ImmutableMap.builderWithExpectedSize(DIRECTIONS.length);
        final List<Direction> horizontalDirections = Direction.Plane.HORIZONTAL.stream().toList();
        for (final Direction direction : DIRECTIONS) {
            result.put(direction, switch (direction.getAxis().getPlane()) {
                case HORIZONTAL -> List.of(Direction.UP, direction.getCounterClockWise(), Direction.DOWN, direction.getClockWise());
                case VERTICAL -> horizontalDirections;
            });
        }
        return result.build();
    });

    public static Vec3 getCorner(AABB aabb, Direction one, Direction two, Direction three) {
        final Direction xDir = choose(Direction.Axis.X, one, two, three);
        final Direction yDir = choose(Direction.Axis.Y, one, two, three);
        final Direction zDir = choose(Direction.Axis.Z, one, two, three);
        return new Vec3(getSide(aabb, xDir), getSide(aabb, yDir), getSide(aabb, zDir));
    }

    public static Direction choose(Direction.Axis axis, Direction one, Direction two, Direction three) {
        return one.getAxis() == axis ? one : two.getAxis() == axis ? two : three;
    }

    public static double getSide(AABB aabb, Direction side) {
        return side.getAxisDirection() == Direction.AxisDirection.POSITIVE
            ? aabb.max(side.getAxis())
            : aabb.min(side.getAxis());
    }

    public static int getSide(BoundingBox box, Direction direction) {
        return switch (direction) {
            case DOWN -> box.minY();
            case UP -> box.maxY();
            case NORTH -> box.minZ();
            case SOUTH -> box.maxZ();
            case WEST -> box.minX();
            case EAST -> box.maxX();
        };
    }

    public static BoundingBox oneSided(BoundingBox box, Direction direction) {
        return switch (direction) {
            case DOWN -> new BoundingBox(box.minX(), box.minY(), box.minZ(), box.maxX(), box.minY(), box.maxZ());
            case UP -> new BoundingBox(box.minX(), box.maxY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ());
            case NORTH -> new BoundingBox(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.minZ());
            case SOUTH -> new BoundingBox(box.minX(), box.minY(), box.maxZ(), box.maxX(), box.maxY(), box.maxZ());
            case WEST -> new BoundingBox(box.minX(), box.minY(), box.minZ(), box.minX(), box.maxY(), box.maxZ());
            case EAST -> new BoundingBox(box.maxX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ());
        };
    }

    @Nullable
    public static BoundingBox matchBlocks(
        BlockPos origin, IntIntPair maxSearch, Direction axis1, Direction axis2, Predicate<BlockPos> predicate
    ) {
        final BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        final BlockPos.MutableBlockPos mutable2 = new BlockPos.MutableBlockPos();
        final Long2ObjectMap<Boolean> predicateCache = new Long2ObjectOpenHashMap<>();
        for (int i = 0; i < maxSearch.leftInt(); i++) {
            for (int j = 0; j < maxSearch.rightInt(); j++) {
                final BoundingBox match = matchBlocksStep(
                    mutable.set(origin).move(axis1.getOpposite(), i).move(axis2.getOpposite(), j),
                    maxSearch, axis1, axis2, predicate, predicateCache, mutable2
                );
                if (match != null) {
                    return match;
                }
            }
        }
        return null;
    }

    @Nullable
    private static BoundingBox matchBlocksStep(
        BlockPos origin, IntIntPair maxSearch, Direction axis1, Direction axis2,
        Predicate<BlockPos> predicate, Long2ObjectMap<Boolean> predicateCache,
        BlockPos.MutableBlockPos mutable
    ) {
        for (int i = 0; i < maxSearch.leftInt(); i++) {
            for (int j = 0; j < maxSearch.rightInt(); j++) {
                mutable.set(origin).move(axis1, i).move(axis2, j);
                Boolean matches = predicateCache.get(mutable.asLong());
                if (matches == null) {
                    matches = predicate.test(mutable);
                    predicateCache.put(mutable.asLong(), matches);
                }
                if (!matches) {
                    return null;
                }
            }
        }
        // These can be left as mutable, because they're leaving the scope they were created in, so they shouldn't be
        // attempted to be mutated anymore.
        return BoundingBox.fromCorners(
            origin,
            mutable.set(origin)
                .move(axis1, maxSearch.leftInt() - 1)
                .move(axis2, maxSearch.rightInt() - 1)
        );
    }

    public static BoundingBox copy(BoundingBox box) {
        return new BoundingBox(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ());
    }

    public static BoundingBox moved(BoundingBox box, Direction direction) {
        return moved(box, direction, 1);
    }

    @SuppressWarnings("deprecation")
    public static BoundingBox moved(BoundingBox box, Direction direction, int amount) {
        return copy(box).move(direction.getNormal().multiply(amount));
    }
}
