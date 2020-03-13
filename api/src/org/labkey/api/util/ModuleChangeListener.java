package org.labkey.api.util;

import org.labkey.api.module.Module;

/** Does not fire during server startup, only after initial modules are loaded */
public interface ModuleChangeListener
{
    void onModuleChanged(Module m);
}
