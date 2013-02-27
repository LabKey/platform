/*
 * Copyright (c) 2008-2012 LabKey Corporation
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

import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.security.User;

import java.util.Map;

/**
 * User: adam
 * Date: Aug 2, 2008
 * Time: 11:13:39 AM
 */
public abstract class AbstractSettingsGroup
{
    private static Logger _log = Logger.getLogger(AbstractSettingsGroup.class);

    public static final User SITE_CONFIG_USER = new User("site settings", -1); // Historically, site settings have userd user id -1

    protected abstract String getGroupName();

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
        return lookupStringValue(ContainerManager.getRoot(), name, defaultValue);
    }

    protected String lookupStringValue(Container c, String name, @Nullable String defaultValue)
    {
        Map props = getProperties(c);
        String value = (String) props.get(name);
        return value != null ? value : defaultValue;
    }

    public Map<String, String> getProperties(Container c)
    {
        return PropertyManager.getProperties(SITE_CONFIG_USER, c, getGroupName());
    }
}
