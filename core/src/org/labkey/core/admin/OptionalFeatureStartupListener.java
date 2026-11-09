/*
 * Copyright (c) 2024-2026 LabKey Corporation
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
package org.labkey.core.admin;

import jakarta.servlet.ServletContext;
import org.apache.commons.lang3.BooleanUtils;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.settings.MapBasedStartupPropertyHandler;
import org.labkey.api.settings.OptionalFeatureFlag;
import org.labkey.api.settings.OptionalFeatureService;
import org.labkey.api.settings.StartupPropertyEntry;
import org.labkey.api.util.DOM;
import org.labkey.api.util.StartupListener;

import java.util.Comparator;
import java.util.Map;

import static org.labkey.api.settings.AppProps.SCOPE_OPTIONAL_FEATURE;
import static org.labkey.api.util.DOM.SPAN;
import static org.labkey.api.util.DOM.STRONG;

public class OptionalFeatureStartupListener implements StartupListener
{
    @Override
    public String getName()
    {
        return "Optional feature startup property handler";
    }

    @Override
    public void moduleStartupComplete(ServletContext servletContext)
    {
        ModuleLoader.getInstance().handleStartupProperties(new OptionalFeatureStartupPropertyHandler());
    }

    private static class OptionalFeatureStartupPropertyHandler extends MapBasedStartupPropertyHandler<OptionalFeatureFlag>
    {
        public OptionalFeatureStartupPropertyHandler()
        {
            super(
                SCOPE_OPTIONAL_FEATURE,
                OptionalFeatureFlag.class.getName(),
                OptionalFeatureService.get().getOptionalFeatureFlags().stream()
                    .filter(flag -> flag.getPropertyName() != null)
                    .sorted(Comparator.comparing(OptionalFeatureFlag::getPropertyName, String.CASE_INSENSITIVE_ORDER))
            );
        }

        @Override
        public DOM.Renderable getScopeDescription()
        {
            return SPAN(STRONG(getScope()), " - set these properties to true/false to enable/disable the corresponding feature flag");
        }

        @Override
        public void handle(Map<OptionalFeatureFlag, StartupPropertyEntry> properties)
        {
            OptionalFeatureService svc = OptionalFeatureService.get();
            properties.forEach((sp, entry) -> svc.setFeatureEnabled(sp.getFlag(), BooleanUtils.toBoolean(entry.getValue()), null));
        }
    }
}
