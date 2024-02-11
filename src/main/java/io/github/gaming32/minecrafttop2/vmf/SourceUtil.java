package io.github.gaming32.minecrafttop2.vmf;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public class SourceUtil {
    public static final DecimalFormat DEC_FORMAT = new DecimalFormat("#.###", DecimalFormatSymbols.getInstance(Locale.ROOT));

    public static String getVectorString(Vec3 vec) {
        return DEC_FORMAT.format(vec.x) + ' ' + DEC_FORMAT.format(vec.z) + ' ' + DEC_FORMAT.format(vec.y);
    }

    // Y is up only for rotations, because Source
    public static String getRotationString(Vec3 vec) {
        return DEC_FORMAT.format(vec.x) + ' ' + DEC_FORMAT.format(vec.y) + ' ' + DEC_FORMAT.format(vec.z);
    }

    public static Vec3 transform(AABB bounds, Vec3 pos) {
        return new Vec3(
            (bounds.maxX - pos.x) * 64,
            (pos.y - bounds.minY) * 64,
            (pos.z - bounds.minZ) * 64
        );
    }

    public static BlockPos transform(BoundingBox bounds, BlockPos pos) {
        return new BlockPos(
            (bounds.maxX() - pos.getX()) * 64,
            (pos.getY() - bounds.minY()) * 64,
            (pos.getZ() - bounds.minZ()) * 64
        );
    }

    public static double transformRotation(double rotation) {
        return Mth.wrapDegrees(-rotation + 90);
    }
}
