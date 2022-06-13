package org.labkey.api.module;

import org.labkey.api.settings.StartupProperty;

public enum ModuleLoaderStartupProperties implements StartupProperty
{
    include
    {
        @Override
        public String getDescription()
        {
            return "Comma-separated list of modules to enable during this server session";
        }
    },
    exclude
    {
        @Override
        public String getDescription()
        {
            return "Comma-separated list of modules to disable during this server session";
        }
    };
}
