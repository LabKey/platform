package org.labkey.study.query;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ContainerFilter;
import org.labkey.study.model.DatasetDefinition;

public class LinkedDatasetTable extends DatasetTableImpl
{
    LinkedDatasetTable(@NotNull StudyQuerySchema schema, ContainerFilter cf, @NotNull DatasetDefinition dsd)
    {
        super(schema, cf, dsd);
    }

    @Override
    protected boolean acceptColumn(ColumnInfo column)
    {
        if (getUserSchema().getStudy().getTimepointType().isVisitBased())
        {
            // issue : 47937 don't add the date field to linked datasets
            return (!"date".equalsIgnoreCase(column.getName()));
        }
        return true;
    }
}
