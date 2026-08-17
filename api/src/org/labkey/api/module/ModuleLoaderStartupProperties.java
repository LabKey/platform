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
import java.util.List;
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

        @Override
        void handle(String value)
        {
            ModuleLoader.getInstance().setModuleIncludeList(splitValues(value));
        }
    },
    exclude
    {
        @Override
        public String getDescription()
        {
            return "Comma-separated list of modules to disable during this server session. Note: Respected only when the \"startup\" modifier is specified.";
        }

        @Override
        void handle(String value)
        {
            ModuleLoader.getInstance().setModuleExcludeList(splitValues(value));
        }
    },
    distributionName
    {
        @Override
        public String getDescription()
        {
            return "Distribution name to show in the admin console, include in the export diagnostics zip file, and " +
                "report to mothership. This name overrides the value provided in the distribution.properties file " +
                "that's bundled with the distribution. Note: Respected only when the \"startup\" modifier is specified.";
        }

        @Override
        void handle(String value)
        {
            ModuleLoader.getInstance().setDistributionNameOverride(StringUtils.trimToNull(value));
        }
    };

    /**
     * Apply the value supplied for this property. Abstract, rather than a shared default implementation, so that adding
     * a constant forces a decision about how its value is handled.
     */
    abstract void handle(String value);

    private static List<String> splitValues(String value)
    {
        return Arrays.stream(StringUtils.split(value, ","))
            .map(StringUtils::trimToNull)
            .filter(Objects::nonNull)
            .toList();
    }

    static void populate()
    {
        ModuleLoader.getInstance().handleStartupProperties(new StandardStartupPropertyHandler<>("ModuleLoader", ModuleLoaderStartupProperties.class) {
            @Override
            public void handle(Map<ModuleLoaderStartupProperties, StartupPropertyEntry> map)
            {
                map.forEach((sp, cp) -> sp.handle(cp.getValue()));
            }
        });
    }
}
