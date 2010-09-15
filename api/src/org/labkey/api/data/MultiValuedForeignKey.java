package org.labkey.api.data;

import org.labkey.api.collections.NamedObjectList;
import org.labkey.api.util.StringExpression;

/**
* User: adam
* Date: Sep 14, 2010
* Time: 1:10:03 PM
*/
public class MultiValuedForeignKey implements ForeignKey
{
    private final ForeignKey _fk;
    private final String _junctionLookup;

    public MultiValuedForeignKey(ForeignKey fk, String junctionLookup)
    {
        _fk = fk;
        _junctionLookup = junctionLookup;
    }

    @Override
    public ColumnInfo createLookupColumn(ColumnInfo parent, String displayField)
    {
        TableInfo junction = _fk.getLookupTableInfo();

        ColumnInfo junctionKey = junction.getColumn(_junctionLookup);       // Junction join to value table
        ColumnInfo childKey = junction.getColumn(getLookupColumnName());    // Junction join to primary table
        ForeignKey fk = junctionKey.getFk();                                // Wrapped foreign key to value table (elided lookup)

        ColumnInfo lookupColumn = fk.createLookupColumn(junctionKey, displayField);
        return new MultiValuedLookupColumn(lookupColumn.getFieldKey(), parent, childKey, junctionKey, fk, lookupColumn);
    }

    @Override
    public TableInfo getLookupTableInfo()
    {
        ColumnInfo junctionColumn = _fk.getLookupTableInfo().getColumn(_junctionLookup);
        if (junctionColumn != null)
        {
            ForeignKey junctionFK = junctionColumn.getFk();
            if (junctionFK != null)
            {
                return junctionFK.getLookupTableInfo();
            }
        }
        return null;
    }

    @Override
    public StringExpression getURL(ColumnInfo parent)
    {
        return _fk.getURL(parent);
    }

    @Override
    public NamedObjectList getSelectList()
    {
        return _fk.getSelectList();
    }

    @Override
    public String getLookupContainerId()
    {
        return _fk.getLookupContainerId();
    }

    @Override
    public String getLookupTableName()
    {
        return _fk.getLookupTableName();
    }

    @Override
    public String getLookupSchemaName()
    {
        return _fk.getLookupSchemaName();
    }

    @Override
    public String getLookupColumnName()
    {
        return _fk.getLookupColumnName();
    }
}
