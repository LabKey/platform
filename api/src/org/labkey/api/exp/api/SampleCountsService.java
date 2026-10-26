/*
 * Copyright (c) 2026 LabKey Corporation
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
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.services.ServiceRegistry;

/**
 * Serves sample counts that a module maintains ahead of time, so a caller can read them instead of counting samples.
 * Registered by the module that owns those counts; null when no module does, in which case callers count live.
 */
public interface SampleCountsService
{
    static void setInstance(SampleCountsService impl)
    {
        ServiceRegistry.get().registerService(SampleCountsService.class, impl);
    }

    @Nullable
    static SampleCountsService get()
    {
        return ServiceRegistry.get().getService(SampleCountsService.class);
    }

    /**
     * A scalar expression for how many samples a sample type has in the folders the container filter allows, suitable
     * for a correlated subquery against a sample type table.
     *
     * @param sampleTypeRowId the enclosing query's sample type RowId column
     * @param liveCount counts the samples directly; the returned expression falls back to it wherever the maintained
     *                  counts can't answer, so a count that isn't ready is slow rather than wrong. An implementation
     *                  may embed it more than once, and must append this fragment itself at each position rather than
     *                  copying its SQL text, so that its bound parameters stay aligned with their placeholders.
     * @return null when this service can't answer at all, leaving the caller to use liveCount
     */
    @Nullable
    SQLFragment getSampleCountSQL(@NotNull SQLFragment sampleTypeRowId, @NotNull ContainerFilter cf,
                                  @NotNull DbSchema schema, @NotNull SQLFragment liveCount);
}
