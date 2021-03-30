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
        else
            return new DatasetTableImpl(schema, cf, dsd);
    }
}
