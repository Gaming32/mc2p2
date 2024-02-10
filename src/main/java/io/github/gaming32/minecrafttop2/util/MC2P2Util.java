package io.github.gaming32.minecrafttop2.util;

import net.minecraft.Util;
import net.minecraft.core.Direction;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.EnumMap;
import java.util.Map;

public class MC2P2Util {
    public static final Direction[] DIRECTIONS = Direction.values();
    private static final Map<Direction, Vec3> NORMAL_32_MAP = Util.make(new EnumMap<>(Direction.class), map -> {
        for (final Direction dir : DIRECTIONS) {
            map.put(dir, Vec3.atLowerCornerOf(dir.getNormal().multiply(32)));
        }
    });

    public static Vec3 getNormal32(Direction direction) {
        return NORMAL_32_MAP.get(direction);
    }

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
}
