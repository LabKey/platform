package org.labkey.api.query;

import org.labkey.api.data.ForeignKey;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.LookupColumn;
import org.labkey.api.view.ActionURL;
import org.labkey.api.util.StringExpressionFactory;

import java.util.Collections;

/**
 * Class which is not really a foreign key to another table.
 * It's just used for a link on a column bound to rowid which displays
 * a different column from the same table.
 */
public class TitleForeignKey implements ForeignKey
{
    ActionURL _baseURL;
    ColumnInfo _lookupKey;
    ColumnInfo _displayColumn;
    String _paramName;
    public TitleForeignKey(ActionURL baseURL, ColumnInfo lookupKey, ColumnInfo displayColumn, String paramName)
    {
        _baseURL = baseURL;
        _lookupKey = lookupKey;
        _displayColumn = displayColumn;
        _paramName = paramName;
    }

    public ColumnInfo createLookupColumn(ColumnInfo parent, String displayField)
    {
        if (displayField != null)
            return null;
        return LookupColumn.create(parent, _lookupKey, _displayColumn, true);
    }

    public TableInfo getLookupTableInfo()
    {
        return null;
    }

    public StringExpressionFactory.StringExpression getURL(ColumnInfo parent)
    {
        if (_baseURL == null)
            return null;
        return new LookupURLExpression(_baseURL, Collections.singletonMap(_paramName, parent));
    }
}
