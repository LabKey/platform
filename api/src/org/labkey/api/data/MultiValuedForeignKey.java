package org.labkey.api.data;

import org.labkey.api.query.FieldKey;
import org.labkey.api.util.StringExpression;

/**
* User: adam
* Date: Sep 14, 2010
* Time: 1:10:03 PM
*/
public class MultiValuedForeignKey extends ColumnInfo.SchemaForeignKey
{
    private final String _junctionLookup;

    public MultiValuedForeignKey(ColumnInfo foreignKey, String dbSchemaName, String tableName, String lookupKey, String junctionLookup, boolean joinWithContainer)
    {
        super(foreignKey, dbSchemaName, tableName, lookupKey, joinWithContainer);

        _junctionLookup = junctionLookup;
    }

    @Override
    public ColumnInfo createLookupColumn(ColumnInfo parent, String displayField)
    {
        TableInfo junction = getLookupTableInfo();

        ColumnInfo junctionKey = junction.getColumn(_junctionLookup);       // Junction join to value table
        ColumnInfo childKey = junction.getColumn(getLookupColumnName());    // Junction join to primary table
        ForeignKey fk = junctionKey.getFk();                                // Wrapped foreign key to value table (elided lookup)

        ColumnInfo lookupColumn = fk.createLookupColumn(junctionKey, displayField);
        return new MultiValuedLookupColumn(new FieldKey(parent.getFieldKey(), displayField), parent, childKey, junctionKey, fk, lookupColumn);
    }

    @Override
    public StringExpression getURL(ColumnInfo parent)
    {
        return null;
    }
}
