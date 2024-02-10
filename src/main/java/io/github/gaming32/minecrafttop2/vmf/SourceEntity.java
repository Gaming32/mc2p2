package io.github.gaming32.minecrafttop2.vmf;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.world.phys.Vec3;
import net.platinumdigitalgroup.jvdf.VDFNode;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public record SourceEntity(
    String clazz,
    Vec3 origin,
    Vec3 angles,
    @Nullable String name,
    Map<String, String> properties,
    Multimap<String, EntityConnection> connections,
    @Nullable SimpleBrush brush
) implements ToVmfWithId {
    @Override
    public VDFNode toVmf(int id) {
        final VDFNode result = new VDFNode();
        // This is a weird order, but it's the order Hammer uses
        result.put("id", id++);
        result.put("classname", clazz);
        if (brush == null) {
            result.put("angles", SourceUtil.getRotationString(angles));
        } else {
            result.put("origin", SourceUtil.getVectorString(origin));
        }
        for (final var entry : properties.entrySet()) {
            result.put(entry.getKey(), entry.getValue());
        }
        if (name != null) {
            result.put("targetname", name);
        }
        if (!connections.isEmpty()) {
            final VDFNode connectionsVmf = new VDFNode();
            result.put("connections", connectionsVmf);
            for (final var entry : connections.entries()) {
                connectionsVmf.put(entry.getKey(), entry.getValue().toVmf());
            }
        }
        if (brush == null) {
            result.put("origin", SourceUtil.getVectorString(origin));
        } else {
            result.put("solid", brush.toVmf(id));
        }
        return result;
    }

    @Override
    public int idsUsed() {
        return 1 + (brush != null ? brush.idsUsed() : 0);
    }

    public static Builder builder(String clazz) {
        return new Builder(clazz);
    }

    public record EntityConnection(String target, String input, double delay, int maxTriggers) {
        private static final char SEPARATOR = '\u001b'; // Some engine branches use ','. Portal 2 supports scripting, so it uses ESC.

        public EntityConnection(String target, String input, double delay) {
            this(target, input, delay, -1);
        }

        public EntityConnection(String target, String input) {
            this(target, input, 0.0, -1);
        }

        public String toVmf() {
            return target + SEPARATOR + input + SEPARATOR + delay + SEPARATOR + maxTriggers;
        }
    }

    public static class Builder {
        private final String clazz;
        private Vec3 origin = Vec3.ZERO;
        private Vec3 angles = Vec3.ZERO;
        private String name = null;
        private final ImmutableMap.Builder<String, String> properties = ImmutableMap.builder();
        private final ImmutableMultimap.Builder<String, EntityConnection> connections = ImmutableMultimap.builder();
        private SimpleBrush brush = null;

        private Builder(String clazz) {
            this.clazz = clazz;
        }

        public Builder origin(Vec3 origin) {
            this.origin = origin;
            return this;
        }

        public Builder angles(Vec3 angles) {
            this.angles = angles;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder property(String key, String value) {
            this.properties.put(key, value);
            return this;
        }

        public Builder connection(String output, EntityConnection connection) {
            this.connections.put(output, connection);
            return this;
        }

        public Builder brush(SimpleBrush brush) {
            this.brush = brush;
            return this;
        }

        public SourceEntity build() {
            return new SourceEntity(clazz, origin, angles, name, properties.build(), connections.build(), brush);
        }
    }
}
