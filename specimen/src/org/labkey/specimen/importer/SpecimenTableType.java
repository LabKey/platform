package org.labkey.specimen.importer;

import java.util.Collection;

public class SpecimenTableType
{
    private final String _name;
    private final String _tableName;
    private final Collection<? extends ImportableColumn> _columns;

    public SpecimenTableType(String name, String tableName, Collection<? extends ImportableColumn> columns)
    {
        _name = name;
        _tableName = tableName;
        _columns = columns;
    }

    public String getName()
    {
        return _name;
    }

    public String getTableName()
    {
        return _tableName;
    }

    public Collection<? extends ImportableColumn> getColumns()
    {
        return _columns;
    }
}
