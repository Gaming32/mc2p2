package io.github.gaming32.minecrafttop2.vmf;

import io.github.gaming32.minecrafttop2.util.MC2P2Util;
import net.minecraft.core.Direction;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

public record MaterialInfo(String material, UvAxis uAxis, UvAxis vAxis) {
    public static final MaterialInfo NODRAW = defaultFor("TOOLS/TOOLSNODRAW");
    public static final MaterialInfo SKYBOX = defaultFor("TOOLS/TOOLSSKYBOX");

    public MaterialInfo {
        material = material.toUpperCase(Locale.ROOT);
    }

    public static MaterialInfo defaultFor(String material) {
        return new MaterialInfo(material, UvAxis.DEFAULT, UvAxis.DEFAULT);
    }

    public static Map<Direction, MaterialInfo> ofSingleDirection(Direction direction, MaterialInfo material) {
        final Map<Direction, MaterialInfo> result = new EnumMap<>(Direction.class);
        for (final Direction dir : MC2P2Util.DIRECTIONS) {
            result.put(dir, dir == direction ? material : NODRAW);
        }
        return result;
    }

    public record UvAxis(int shift, double scale) {
        public static final UvAxis DEFAULT = new UvAxis(0, 0.25);

        public static UvAxis shift(int shift) {
            return new UvAxis(shift, 0.25);
        }
    }
}
