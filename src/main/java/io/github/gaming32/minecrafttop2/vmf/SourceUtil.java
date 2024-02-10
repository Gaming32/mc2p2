package io.github.gaming32.minecrafttop2.vmf;

import net.minecraft.world.phys.Vec3;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public class SourceUtil {
    public static final DecimalFormat DEC_FORMAT = new DecimalFormat("#.###", DecimalFormatSymbols.getInstance(Locale.ROOT));

    public static String getVectorString(Vec3 vec) {
        return DEC_FORMAT.format(vec.x) + ' ' + DEC_FORMAT.format(vec.z) + ' ' + DEC_FORMAT.format(vec.y);
    }
}
