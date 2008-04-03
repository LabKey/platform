package org.labkey.api.data;

public class VirtualTable extends AbstractTableInfo
{
    public VirtualTable(DbSchema schema)
    {
        super(schema);
    }
    public SQLFragment getFromSQL(String alias)
    {
        throw new UnsupportedOperationException();
    }
}
