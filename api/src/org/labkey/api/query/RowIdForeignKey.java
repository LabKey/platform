package org.labkey.api.query;

import org.labkey.api.data.ForeignKey;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.LookupColumn;
import org.labkey.api.util.StringExpressionFactory;

import java.util.Collections;

/**
 * A lookup from the __PRIMARY KEY__ of a table back to ITSELF.
 * Why would anyone want to have a lookup back to the exact same column?
 * So that, if the column is used in a query, it is then a lookup so that the user can choose columns which were not
 * necessarily included in the query.
 * This particular type of Foreign Key is treated specially in the Query Designer.  You can only select columns from the
 * lookup table if the RowIdForeignKey is attached to a column that has been imported into another table. 
 */
public class RowIdForeignKey implements ForeignKey
{
    protected ColumnInfo _rowidColumn;
    public RowIdForeignKey(ColumnInfo rowidColumn)
    {
        _rowidColumn = rowidColumn;
    }
    public ColumnInfo createLookupColumn(ColumnInfo parent, String displayField)
    {
        TableInfo lookupTable = getLookupTableInfo();
        if (displayField == null)
            displayField = lookupTable.getTitleColumn();
        if (displayField == null)
            return null;
        return LookupColumn.create(parent, _rowidColumn, lookupTable.getColumn(displayField), true);
    }

    public TableInfo getLookupTableInfo()
    {
        return _rowidColumn.getParentTable();
    }

    public StringExpressionFactory.StringExpression getURL(ColumnInfo parent)
    {
        return getLookupTableInfo().getDetailsURL(Collections.singletonMap(_rowidColumn.getName(), parent));
    }

    public ColumnInfo getOriginalColumn()
    {
        return _rowidColumn;
    }
}
