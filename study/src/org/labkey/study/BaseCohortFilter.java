package org.labkey.study;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;

import java.util.Collections;
import java.util.Map;

public abstract class BaseCohortFilter implements CohortFilter
{
    protected final Type _type;

    protected BaseCohortFilter(Type type)
    {
        _type = type;
    }

    public CohortFilter.Type getType()
    {
        return _type;
    }

    protected ColumnInfo getCohortColumn(TableInfo table, Container container)
    {
        FieldKey cohortColKey = _type.getFilterColumn(container);
        Map<FieldKey, ColumnInfo> cohortColumnMap = QueryService.get().getColumns(table, Collections.singleton(cohortColKey));
        ColumnInfo cohortColumn = cohortColumnMap.get(cohortColKey);
        if (cohortColumn == null)
            throw new IllegalStateException("A column with key '" + cohortColKey.toString() + "'  was not found on table " + table.getName());
        return cohortColumn;
    }
}