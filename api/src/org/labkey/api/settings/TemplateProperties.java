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

import org.apache.commons.lang3.BooleanUtils;
import org.labkey.api.data.Container;
import org.labkey.api.data.PropertyManager;

import java.util.Map;

/**
 * Created by marty on 7/5/2017.
 */
public interface TemplateProperties
{
    String SHOW_ONLY_IN_PROJECT_ROOT_PROPERTY_NAME = "ShowOnlyInProjectRoot";

    String getDisplayConfigs();
    String getDisplayPropertyName();
    String getModulePropertyName();
    String getFileName();
    String getShowByDefault();
    Container getContainer();

    default boolean isDisplay()
    {
        String isDisplay = getShowByDefault();
        String displayProp = getProperty(getDisplayPropertyName());

        if (displayProp != null)
        {
            isDisplay = displayProp;
        }
        return BooleanUtils.toBoolean(isDisplay);
    }

    default void setDisplay(boolean isDisplay)
    {
        setProperty(getDisplayPropertyName(), BooleanUtils.toStringTrueFalse(isDisplay));
    }

    default String getModule()
    {
        return getProperty(getModulePropertyName());
    }

    default void setModule(String module)
    {
        setProperty(getModulePropertyName(), module);
    }

    /**
     * Should this resource appear in all folders or only in the project root.
     */
    default boolean isShowOnlyInProjectRoot()
    {
        return BooleanUtils.toBoolean(getProperty(SHOW_ONLY_IN_PROJECT_ROOT_PROPERTY_NAME));
    }

    default void setIsShowOnlyInProjectRoot(boolean showOnlyInProjectRoot)
    {
        setProperty(SHOW_ONLY_IN_PROJECT_ROOT_PROPERTY_NAME, String.valueOf(showOnlyInProjectRoot));
    }

    private void setProperty(String propName, String value)
    {
        PropertyManager.PropertyMap map;
        Container container = getContainer();

        if (container != null)
        {
            if (container.isRoot())
                map = PropertyManager.getWritableProperties(getDisplayConfigs(), true);
            else
                // if not site level, default to project level
                map = PropertyManager.getWritableProperties(container.getProject(), getDisplayConfigs(), true);
        }
        else
            throw new IllegalStateException("Container is null for this TemplateProperty");

        map.put(propName, value);
        map.save();
    }

    private String getProperty(String propName)
    {
        Map<String, String> map;
        Container container = getContainer();

        if (container != null)
        {
            // check to see if there is a project override, else get the site default
            if (!container.isRoot())
            {
                map = PropertyManager.getProperties(container.getProject(), getDisplayConfigs());
                if (map.containsKey(propName))
                    return map.get(propName);
            }
            map = PropertyManager.getProperties(container, getDisplayConfigs());
            return map.get(propName);
        }
        else
            throw new IllegalStateException("Container is null for this TemplateProperty");
    }
}
