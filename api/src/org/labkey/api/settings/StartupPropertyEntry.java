/*
 * Copyright (c) 2017-2018 LabKey Corporation
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

import org.jetbrains.annotations.Nullable;

/**
 * Represents a single line in a startup property file (including scope, name, modifier, and value). Startup property
 * entries are passed from the deployment environment into the application, and can set defaults during bootstrapping or
 * subsequent server sessions.
 * Created by klum on 6/12/2017.
 */
public class StartupPropertyEntry
{
    public static final String DEFAULT_SCOPE = "none";
    public static final String SYS_PROP_PREFIX = "labkey.prop.";

    // TODO: Move the constants defined below to the handlers that use them
    public static final String SCOPE_LOOK_AND_FEEL_SETTINGS = "LookAndFeelSettings";
    public static final String SCOPE_SITE_ROOT_SETTINGS = "SiteRootSettings";
    public static final String SCOPE_SCRIPT_ENGINE_DEFINITION = "ScriptEngineDefinition";
    public static final String SCOPE_EXPERIMENTAL_FEATURE = AppProps.EXPERIMENTAL_FEATURE;

    // TODO: Make final and @NotNull, once all startup property handling has been migrated
    private @Nullable StartupProperty _startupProperty;
    private final String _name;
    private final String _value;
    private final modifier _modifier;
    private final String _scope;


    public enum modifier
    {
        bootstrap,
        startup,
    }

    public StartupPropertyEntry(String name, String value, @Nullable String modifierType, @Nullable String scope)
    {
        _name = name;
        _value = value;
        _modifier = null==modifierType ?  modifier.bootstrap : modifier.valueOf(modifierType);
        _scope = null==scope ? DEFAULT_SCOPE : scope;
    }

    public @Nullable StartupProperty getStartupProperty()
    {
        return _startupProperty;
    }

    public void setStartupProperty(StartupProperty startupProperty)
    {
        _startupProperty = startupProperty;
    }

    public String getValue()
    {
        return _value;
    }

    public String getName()
    {
        return _name;
    }

    public modifier getModifier()
    {
        return _modifier;
    }

    public String getScope()
    {
        return _scope;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        StartupPropertyEntry config = (StartupPropertyEntry) o;

        if (!_name.equals(config.getName())) return false;
        else if (!_scope.equals(config.getScope())) return false;

        return true;
    }

    @Override
    public int hashCode()
    {
        int result = 31 * _name.hashCode();
        result = 31 * result + _scope.hashCode();

        return result;
    }
}
