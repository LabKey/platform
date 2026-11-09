/*
 * Copyright (c) 2025-2026 LabKey Corporation
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
package org.labkey.api.migration;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.services.ServiceRegistry;

import java.util.Collection;

public interface ExperimentDeleteService
{
    static @NotNull ExperimentDeleteService get()
    {
        ExperimentDeleteService ret = ServiceRegistry.get().getService(ExperimentDeleteService.class);
        if (ret == null)
            throw new IllegalStateException("ExperimentDeleteService not found");
        return ret;
    }

    static void setInstance(ExperimentDeleteService impl)
    {
        ServiceRegistry.get().registerService(ExperimentDeleteService.class, impl);
    }

    /**
     * Deletes all rows from exp.Data, exp.Object, and related tables associated with the provided ObjectIds
     */
    void deleteDataRows(Collection<Long> objectIds);
}
