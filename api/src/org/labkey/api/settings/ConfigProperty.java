/*
 * Copyright (c) 2017 LabKey Corporation
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
 * Created by klum on 6/12/2017.
 */
public class ConfigProperty
{
    public static final String DEFAULT_SCOPE = "none";
    public static final String SYS_PROP_PREFIX = "labkey.prop.";
    public static final String SCOPE_LOOK_AND_FEEL_SETTINGS = "LookAndFeelSettings";
    public static final String SCOPE_SITE_SETTINGS = "SiteSettings";
    public static final String SCOPE_SITE_ROOT_SETTINGS = "SiteRootSettings";
    public static final String SCOPE_SCRIPT_ENGINE_DEFINITION = "ScriptEngineDefinition";
    public static final String SCOPE_USER_ROLES = "UserRoles";
    public static final String SCOPE_GROUP_ROLES = "GroupRoles";
    public static final String SCOPE_USER_GROUPS = "UserGroups";
    public static final String SCOPE_EXPERIMENTAL_FEATURE = AppProps.EXPERIMENTAL_FEATURE;

    private final String _name;
    private final String _value;
    private final modifier _modifier;
    private final String _scope;


    public enum modifier
    {
        bootstrap,
        startup,
        immutable,
    }

    public ConfigProperty(String name, String value, @Nullable String modifierType, @Nullable String scope)
    {
        _name = name;
        _value = value;
        _modifier = null==modifierType ?  modifier.bootstrap : modifier.valueOf(modifierType);
        _scope = null==scope ? DEFAULT_SCOPE : scope;
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

        ConfigProperty config = (ConfigProperty) o;

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
