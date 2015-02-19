package org.labkey.api.data.dialect;

import org.labkey.api.data.ColumnInfo.ImportedKey;

/**
 * User: adam
 * Date: 2/18/2015
 * Time: 9:53 AM
 */
public interface ForeignKeyResolver
{
    ImportedKey getImportedKey(String fkName, String pkSchemaName, String pkTableName, String pkColumnName, String colName);
}
