/*
 * Copyright (c) 2021-2026 LabKey Corporation
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
package org.labkey.study.query;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.study.Dataset;
import org.labkey.study.model.DatasetDefinition;

public class DatasetFactory
{
    public static DatasetTableImpl createDataset(@NotNull StudyQuerySchema schema, ContainerFilter cf, @NotNull DatasetDefinition dsd)
    {
        Dataset.PublishSource source = dsd.getPublishSource();
        if (source != null)
        {
            switch (source)
            {
                case Assay -> {
                    return new AssayDatasetTable(schema, cf, dsd);
                }
                case SampleType -> {
                    return new SampleDatasetTable(schema, cf, dsd);
                }
                default -> throw new IllegalStateException("Unknown publish source type " + source);
            }
        }
        else if (dsd.getSourceQueryName() != null)
        {
            return new QueryDatasetTable(schema, cf, dsd);
        }
        else
            return new DatasetTableImpl(schema, cf, dsd);
    }
}
