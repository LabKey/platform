/*
 * Copyright (c) 2018-2019 LabKey Corporation
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
package org.labkey.api.security.permissions;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

public class DesignSampleTypePermission extends AbstractPermission
{
    public DesignSampleTypePermission()
    {
        super("Design Sample Types", "Can create and design new sample types or change existing ones.");
    }

    @Override
    public @NotNull Collection<String> getSerializationAliases()
    {
        // Support legacy name
        return List.of("org.labkey.api.security.permissions.DesignSampleSetPermission");
    }
}
