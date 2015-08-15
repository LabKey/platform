package org.labkey.api.data.dialect;

import org.labkey.api.data.ColumnInfo;

/**
 * Created by adam on 8/14/2015.
 */
public class StandardForeignKeyResolver implements ForeignKeyResolver
{
    @Override
    public ColumnInfo.ImportedKey getImportedKey(String fkName, String pkSchemaName, String pkTableName, String pkColumnName, String colName)
    {
        return new ColumnInfo.ImportedKey(fkName, pkSchemaName, pkTableName, pkColumnName, colName);
    }
}
