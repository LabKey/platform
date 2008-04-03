package org.labkey.api.query;

import org.labkey.api.data.*;
import org.labkey.api.util.StringExpressionFactory;

import java.util.Collections;

public class QueryForeignKey implements ForeignKey
{
    TableInfo _table;
    String _tableName;
    String _lookupKey;
    String _displayField;

    public QueryForeignKey(QuerySchema schema, String tableName, String lookupKey, String displayField)
    {
        _table = schema.getTable(tableName, tableName);
        _tableName = tableName;
        _lookupKey = lookupKey;
        _displayField = displayField;
    }

    public QueryForeignKey(TableInfo table, String lookupKey, String displayField)
    {
        _table = table;
        _tableName = table.getName();
        _lookupKey = lookupKey;
        _displayField = displayField;
    }

    public ColumnInfo createLookupColumn(ColumnInfo foreignKey, String displayField)
    {
        TableInfo lookupTable = getLookupTableInfo();
        if (null == lookupTable)
            return null;
        if (displayField == null)
        {
            displayField = _displayField;
            if (displayField == null)
            {
                displayField = lookupTable.getTitleColumn();
            }
            if (displayField == null)
                return null;
            if (displayField.equals(_lookupKey))
            {
                return foreignKey;
            }
        }
        return LookupColumn.create(foreignKey, lookupTable.getColumn(_lookupKey), lookupTable.getColumn(displayField), true);
    }

    public TableInfo getLookupTableInfo()
    {
        return _table;
    }

    public StringExpressionFactory.StringExpression getURL(ColumnInfo parent)
    {
        TableInfo table = getLookupTableInfo();
        if (table == null)
            return null;
        return table.getDetailsURL(Collections.singletonMap(_lookupKey, parent));
    }
}
