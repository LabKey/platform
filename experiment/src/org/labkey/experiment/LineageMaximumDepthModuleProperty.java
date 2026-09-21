/*
 * Copyright (c) 2023-2026 LabKey Corporation
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
package org.labkey.experiment;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.exp.api.ExpLineageOptions;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleProperty;
import org.labkey.api.security.User;

public class LineageMaximumDepthModuleProperty extends ModuleProperty
{
    private final int MINIMUM_DEPTH = 1;

    public LineageMaximumDepthModuleProperty(Module module)
    {
        super(module, ExperimentService.LINEAGE_DEFAULT_MAXIMUM_DEPTH_PROPERTY_NAME);

        setLabel("Lineage Default Maximum Depth");
        setDescription(String.format("Default maximum depth lineage queries will recurse (%d-%d). Defaults to %d.", MINIMUM_DEPTH, getMaximumDepth(), ExpLineageOptions.LINEAGE_DEFAULT_MAXIMUM_DEPTH));
        setShowDescriptionInline(true);
    }

    @Override
    public void validate(@Nullable User user, Container c, @Nullable String value)
    {
        if (StringUtils.isEmpty(value))
            return;

        try
        {
            var depth = Integer.parseInt(value);
            if (depth < MINIMUM_DEPTH)
                throw new IllegalArgumentException(String.format("Invalid value for \"%s\". Minimum value is %d.", getLabel(), MINIMUM_DEPTH));

            var maxDepth = getMaximumDepth();
            if (depth > maxDepth)
                throw new IllegalArgumentException(String.format("Invalid value for \"%s\". Maximum value is %d.", getLabel(), maxDepth));
        }
        catch (NumberFormatException e)
        {
            throw new IllegalArgumentException(String.format("Invalid value for \"%s\". Must be a number.", getLabel()));
        }
    }

    private static int getMaximumDepth()
    {
        return 1_000;
    }
}
