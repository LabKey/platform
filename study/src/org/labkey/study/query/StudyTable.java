package org.labkey.study.query;

import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.AliasedColumn;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ColumnInfo;

public class StudyTable extends FilteredTable
{
    protected StudyQuerySchema _schema;
    public StudyTable(StudyQuerySchema schema, TableInfo realTable)
    {
        super(realTable, schema.getContainer());
        _schema = schema;
    }

    public Container getContainer()
    {
        return _schema.getContainer();
    }
}
