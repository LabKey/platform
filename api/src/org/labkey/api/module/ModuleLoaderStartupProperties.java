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
package org.labkey.api.module;

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.settings.StandardStartupPropertyHandler;
import org.labkey.api.settings.StartupProperty;
import org.labkey.api.settings.StartupPropertyEntry;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.Map;
import java.util.Objects;

public enum ModuleLoaderStartupProperties implements StartupProperty
{
    include
    {
        @Override
        public String getDescription()
        {
            return "Comma-separated list of modules to enable during this server session. Note: Respected only when the \"startup\" modifier is specified.";
        }
    },
    exclude
    {
        @Override
        public String getDescription()
        {
            return "Comma-separated list of modules to disable during this server session. Note: Respected only when the \"startup\" modifier is specified.";
        }
    };

    private final LinkedList<String> _list = new LinkedList<>();

    LinkedList<String> getList()
    {
        return new LinkedList<>(_list);
    }

    static void populate()
    {
        ModuleLoader.getInstance().handleStartupProperties(new StandardStartupPropertyHandler<>("ModuleLoader", ModuleLoaderStartupProperties.class) {
            @Override
            public void handle(Map<ModuleLoaderStartupProperties, StartupPropertyEntry> map)
            {
                map.forEach((sp, cp)-> Arrays.stream(StringUtils.split(cp.getValue(), ","))
                    .map(StringUtils::trimToNull)
                    .filter(Objects::nonNull)
                    .forEach(sp._list::add));
            }
        });
    }
}
