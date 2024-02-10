package io.github.gaming32.minecrafttop2.vmf;

import net.platinumdigitalgroup.jvdf.VDFNode;

public interface ToVmfWithId {
    VDFNode toVmf(int id);

    int idsUsed();
}
