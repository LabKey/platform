package org.labkey.api.util;

/** Does not fire during server startup, only after initial modules are loaded */
public interface ModuleChangeListener
{
    // could add updated/deleted/created if necessary
    void onModuleChanged(String name);
}
