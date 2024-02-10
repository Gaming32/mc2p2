package io.github.gaming32.minecrafttop2.vmf;

import io.github.gaming32.minecrafttop2.util.MC2P2Util;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.platinumdigitalgroup.jvdf.VDFNode;

import java.util.Map;

public record SimpleBrush(AABB bounds, Map<Direction, MaterialInfo> materials) implements ToVmfWithId {
    @Override
    public VDFNode toVmf(int id) {
        final VDFNode result = new VDFNode();
        result.put("id", id++);
        for (final Direction dir : MC2P2Util.DIRECTIONS) {
            final Direction up = switch (dir.getAxis().getPlane()) {
                case HORIZONTAL -> Direction.UP;
                case VERTICAL -> Direction.NORTH;
            };
            final Direction left = switch (dir.getAxis().getPlane()) {
                case HORIZONTAL -> dir.getCounterClockWise();
                case VERTICAL -> switch (dir.getAxisDirection()) {
                    case POSITIVE -> Direction.EAST;
                    case NEGATIVE -> Direction.WEST;
                };
            };
            final VDFNode side = new VDFNode();
            result.put("side", side);
            side.put("id", id++);
            side.put("plane", getPlaneString(
                MC2P2Util.getCorner(bounds, up.getOpposite(), left, dir),
                MC2P2Util.getCorner(bounds, up, left, dir),
                MC2P2Util.getCorner(bounds, up, left.getOpposite(), dir)
            ));
            final MaterialInfo material = materials.getOrDefault(dir, MaterialInfo.NODRAW);
            side.put("material", material.material());
            side.put("uaxis", getUvString(material.uAxis(), left.getNormal()));
            side.put("vaxis", getUvString(material.vAxis(), up.getNormal()));
            side.put("rotation", "0");
            side.put("lightmapscale", "16");
            side.put("smoothing_groups", "0");
        }
        return result;
    }

    @Override
    public int idsUsed() {
        return 7;
    }

    private static String getUvString(MaterialInfo.UvAxis axis, Vec3i dir) {
        return "[" + dir.getX() + " " + dir.getZ() + " " + dir.getY() + " " + axis.shift() + "] " + axis.scale();
    }

    private static String getPlaneString(Vec3 bottomLeft, Vec3 upperLeft, Vec3 upperRight) {
        return '(' + SourceUtil.getVectorString(bottomLeft)
            + ") (" + SourceUtil.getVectorString(upperLeft)
            + ") (" + SourceUtil.getVectorString(upperRight)
            + ')';
    }
}
