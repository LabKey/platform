package org.labkey.query.data;

import org.labkey.api.data.AbstractTableInfo;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.TableInfo;
import org.labkey.data.xml.TableType;
import org.labkey.data.xml.ColumnType;
import org.labkey.api.query.*;
import org.labkey.api.util.StringExpressionFactory;

import java.util.Collection;
import java.util.Map;

public class QueryTableInfo extends AbstractTableInfo
{
    TableInfo _subquery;

    public QueryTableInfo(TableInfo subquery, String name, String alias)
    {
        super(subquery.getSchema());
        _subquery = subquery;
        setName(name);
        setAlias(alias);
    }

    public SQLFragment getFromSQL(String alias)
    {
        return _subquery.getFromSQL(alias);
    }
}
