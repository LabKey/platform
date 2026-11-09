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
package org.labkey.api.settings;

import org.apache.logging.log4j.Logger;
import org.labkey.api.util.logging.LogHelper;

import java.util.Map;

public class RandomSiteSettingsPropertyHandler extends StandardStartupPropertyHandler<RandomStartupProperties>
{
    private static final Logger LOG = LogHelper.getLogger(AppPropsImpl.class, "Additional site settings startup properties");

    public RandomSiteSettingsPropertyHandler()
    {
        super(AppProps.SCOPE_SITE_SETTINGS, RandomStartupProperties.class);
    }

    @Override
    public void handle(Map<RandomStartupProperties, StartupPropertyEntry> properties)
    {
        if (!properties.isEmpty())
        {
            WriteableAppProps writeable = AppProps.getWriteableInstance();
            properties.forEach((rsp, cp) -> {
                LOG.info("Setting additional site-level startup property '{}' to '{}'", rsp.name(), cp.getValue());
                rsp.setValue(writeable, cp.getValue());
            });
            writeable.save(null);
        }
    }
}
