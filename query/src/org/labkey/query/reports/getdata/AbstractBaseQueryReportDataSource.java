package org.labkey.query.reports.getdata;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.QueryException;
import org.labkey.api.query.QueryService;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

/**
 * User: jeckels
 * Date: 5/29/13
 */
public abstract class AbstractBaseQueryReportDataSource implements QueryReportDataSource
{
    public Map<FieldKey, ColumnInfo> getColumnMap(Collection<FieldKey> requiredInputs)
    {
        QueryDefinition sourceQueryDef = getQueryDefinition();
        ArrayList<QueryException> errors = new ArrayList<>();
        TableInfo table = sourceQueryDef.getTable(getSchema(), errors, true);
        if (!errors.isEmpty())
        {
            throw errors.get(0);
        }
        return QueryService.get().getColumns(table, requiredInputs);
    }
}
