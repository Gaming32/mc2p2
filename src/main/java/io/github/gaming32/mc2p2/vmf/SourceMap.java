package io.github.gaming32.mc2p2.vmf;

import com.google.common.collect.ImmutableList;
import net.platinumdigitalgroup.jvdf.VDFNode;

import java.util.List;

public record SourceMap(
    String skybox,
    List<SimpleBrush> brushes,
    List<SourceEntity> entities
) {
    public VDFNode toVmf() {
        final VDFNode result = new VDFNode();
        int id = 1;
        result.put("visgroups", new VDFNode());
        {
            final VDFNode world = new VDFNode();
            result.put("world", world);
            world.put("id", id++);
            world.put("mapversion", "0");
            world.put("classname", "worldspawn");
            world.put("skyname", skybox);
            world.put("maxpropscreenwidth", "-1");
            world.put("detailvbsp", "detail.vbsp");
            world.put("detailmaterial", "detail/detailsprites");
            world.put("maxblobcount", "250");
            for (final SimpleBrush brush : brushes) {
                world.put("solid", brush.toVmf(id));
                id += brush.idsUsed();
            }
        }
        for (final SourceEntity entity : entities) {
            result.put("entity", entity.toVmf(id));
            id += entity.idsUsed();
        }
        {
            final VDFNode cameras = new VDFNode();
            result.put("cameras", cameras);
            cameras.put("activecamera", "-1");
        }
        {
            final VDFNode cordons = new VDFNode();
            result.put("cordons", cordons);
            cordons.put("active", "0");
        }
        return result;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String skybox = "sky_black_nofog";
        private final ImmutableList.Builder<SimpleBrush> brushes = ImmutableList.builder();
        private final ImmutableList.Builder<SourceEntity> entities = ImmutableList.builder();

        private Builder() {
        }

        public Builder skybox(String skybox) {
            this.skybox = skybox;
            return this;
        }

        public Builder brush(SimpleBrush brush) {
            this.brushes.add(brush);
            return this;
        }

        public Builder brush(SimpleBrush... brushes) {
            this.brushes.add(brushes);
            return this;
        }

        public Builder entity(SourceEntity entity) {
            this.entities.add(entity);
            return this;
        }

        public Builder entity(SourceEntity... entities) {
            this.entities.add(entities);
            return this;
        }

        public SourceMap build() {
            return new SourceMap(skybox, brushes.build(), entities.build());
        }
    }
}
