package org.labkey.study.plate.query;

import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.LookupURLExpression;
import org.labkey.api.util.StringExpressionFactory;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.TableInfo;
import org.labkey.api.view.ActionURL;

import java.util.Map;
import java.util.Collections;

/**
 * User: brittp
 * Date: Nov 3, 2006
 * Time: 10:06:59 AM
 */
public abstract class BasePlateTable extends FilteredTable
{
    protected PlateSchema _schema;

    public BasePlateTable(PlateSchema schema, TableInfo info)
    {
        super(info, schema.getContainer());
        _schema = schema;
    }

    protected abstract String getPlateIdColumnName();

    @Override
    public StringExpressionFactory.StringExpression getDetailsURL(Map<String, ColumnInfo> columns)
    {
        ColumnInfo rowid = columns.get(getPlateIdColumnName());
        if (rowid == null)
            return null;
        ActionURL url = new ActionURL("Plate",
            "plateDetails", _schema.getContainer());
        return new LookupURLExpression(url, Collections.singletonMap("rowId", rowid));
    }
}