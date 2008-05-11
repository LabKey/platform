package org.labkey.study.query;

import org.labkey.api.data.TableInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.study.StudySchema;

/**
 * User: brittp
 * Date: Apr 20, 2007
 * Time: 3:18:58 PM
 */
public class RequestStatusTable extends StudyTable
{
    public RequestStatusTable(StudyQuerySchema schema)
    {
        super(schema, StudySchema.getInstance().getTableInfoSampleRequestStatus());
        for (ColumnInfo baseColumn : _rootTable.getColumnsList())
        {
            String name = baseColumn.getName();
            if ("Container".equalsIgnoreCase(name) || "EntityId".equalsIgnoreCase(name))
                continue;
            addWrapColumn(baseColumn);
        }
    }
}
