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
package org.labkey.api.exp.api;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.exp.Identifiable;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;

public interface ExpLineageService
{
    static ExpLineageService get()
    {
        return ServiceRegistry.get().getService(ExpLineageService.class);
    }

    static void setInstance(ExpLineageService impl)
    {
        ServiceRegistry.get().registerService(ExpLineageService.class, impl);
    }

    /**
     * Get the lineage for the seed Identifiable object. Typically, the seed object is an ExpMaterial,
     * an ExpData (in a DataClass), or an ExpRun.
     */
    @NotNull
    ExpLineage getLineage(Container c, User user, @NotNull Identifiable start, @NotNull ExpLineageOptions options);
}
