/*
 * Copyright (c) 2008-2017 LabKey Corporation
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
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.ContainerManager.RootContainerException;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.security.User;

import java.util.Map;

/**
 * Base class for configuration properties persisted through the "prop" schema.
 * User: adam
 * Date: Aug 2, 2008
 */
public abstract class AbstractSettingsGroup
{
    public static final User SITE_CONFIG_USER = new User("site settings", -1); // Historically, site settings have used user id -1

    protected abstract String getGroupName();

    protected User getPropertyConfigUser()
    {
        return SITE_CONFIG_USER;
    }

    protected boolean lookupBooleanValue(String name, boolean defaultValue)
    {
        return "TRUE".equalsIgnoreCase(lookupStringValue(name, defaultValue ? "TRUE" : "FALSE" ) );
    }

    protected int lookupIntValue(String name, int defaultValue)
    {
        try
        {
            return Integer.parseInt(lookupStringValue(name, Integer.toString(defaultValue)));
        }
        catch(NumberFormatException e)
        {
            return defaultValue;
        }
    }

    protected String lookupStringValue(String name, @Nullable String defaultValue)
    {
        Container root = null;

        try
        {
            root = ContainerManager.getRoot();
        }
        catch (RootContainerException e)
        {
        }

        return null != root ? lookupStringValue(root, name, defaultValue) : defaultValue;
    }

    protected String lookupStringValue(Container c, String name, @Nullable String defaultValue)
    {
        Map<String, String> props = getProperties(c);
        String value = props.get(name);
        return value != null ? value : defaultValue;
    }

    public Map<String, String> getProperties(Container c)
    {
        return PropertyManager.getProperties(SITE_CONFIG_USER, c, getGroupName());
    }
}
