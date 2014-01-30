/*
 * Copyright (c) 2008-2013 LabKey Corporation
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
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DatabaseTableType;
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
    public static final User SITE_CONFIG_USER = new User("site settings", -1); // Historically, site settings have used user id -1

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
        // Make sure the core.Containers table actually exists in the database; if not, just return the default value.
        // A bit cheesy, but this allows us to safely use AppProps, etc. even at bootstrap time.
        if (CoreSchema.getInstance().getTableInfoContainers().getTableType() == DatabaseTableType.NOT_IN_DB)
            return defaultValue;

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
