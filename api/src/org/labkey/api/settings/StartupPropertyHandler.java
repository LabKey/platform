/*
 * Copyright (c) 2022-2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.api.settings;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.util.DOM;

import java.util.Collection;
import java.util.Map;

import static org.labkey.api.util.DOM.STRONG;

public abstract class StartupPropertyHandler<T extends StartupProperty>
{
    private final String _scope;
    private final String _startupPropertyClassName;
    private final Map<String, T> _properties;

    protected StartupPropertyHandler(String scope, String startupPropertyClassName, Map<String, T> properties)
    {
        _scope = scope;
        _startupPropertyClassName = startupPropertyClassName;
        _properties = properties;
    }

    public String getScope()
    {
        return _scope;
    }

    // Override to provide more detailed scope-wide guidance on the Startup Properties page
    public DOM.Renderable getScopeDescription()
    {
        return STRONG(getScope());
    }

    public String getStartupPropertyClassName()
    {
        return _startupPropertyClassName;
    }

    // Startup properties come from the global properties files in most cases. Tests can override to provide test properties.
    @NotNull
    public Collection<StartupPropertyEntry> getStartupPropertyEntries()
    {
        return ModuleLoader.getInstance().getStartupPropertyEntries(getScope());
    }

    // Tests can override to avoid standard checks
    public boolean performChecks()
    {
        return true;
    }


    public Map<String, T> getProperties()
    {
        return _properties;
    }

    public Collection<T> getDocumentationProperties()
    {
        return getProperties().values();
    }
}
