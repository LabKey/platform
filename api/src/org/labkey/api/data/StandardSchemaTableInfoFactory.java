package org.labkey.api.data;

import org.jetbrains.annotations.Nullable;

/**
 * Created by adam on 9/8/2015.
 */
public class StandardSchemaTableInfoFactory implements SchemaTableInfoFactory
{
    private final String _tableName;
    private final DatabaseTableType _tableType;
    private final @Nullable String _description;

    public StandardSchemaTableInfoFactory(String tableName, DatabaseTableType tableType, @Nullable String description)
    {
        _tableName = tableName;
        _tableType = tableType;
        _description = description;
    }

    @Override
    public String getTableName()
    {
        return _tableName;
    }

    @Override
    public SchemaTableInfo getSchemaTableInfo(DbSchema schema)
    {
        SchemaTableInfo ti = new SchemaTableInfo(schema, _tableType, _tableName);
        ti.setDescription(_description);

        return ti;
    }
}
