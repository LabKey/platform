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
import org.labkey.api.data.PropertyManager;

import java.util.Map;

/**
 * Created by marty on 7/5/2017.
 */
public interface TemplateProperties
{
    String getDisplayConfigs();
    String getDisplayPropertyName();
    String getModulePropertyName();
    String getFileName();
    String getShowByDefault();

    default boolean isDisplay()
    {
        String isDisplay = getShowByDefault();
        Map<String, String> map = PropertyManager.getProperties(getDisplayConfigs());
        if (!map.isEmpty())
        {
            isDisplay = map.get(getDisplayPropertyName());
        }
        return BooleanUtils.toBoolean(isDisplay);
    }

    default void setDisplay(boolean isDisplay)
    {
        PropertyManager.PropertyMap map = PropertyManager.getWritableProperties(getDisplayConfigs(), true);
        map.put(getDisplayPropertyName(), BooleanUtils.toStringTrueFalse(isDisplay));
        map.save();
    }

    default String getModule()
    {
        Map<String, String> map = PropertyManager.getProperties(getDisplayConfigs());
        return map.get(getModulePropertyName());
    }

    default void setModule(String module)
    {
        PropertyManager.PropertyMap map = PropertyManager.getWritableProperties(getDisplayConfigs(), true);
        map.put(getModulePropertyName(), module);
        map.save();
    }
}
