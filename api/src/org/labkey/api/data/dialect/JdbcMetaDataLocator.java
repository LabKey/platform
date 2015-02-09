package org.labkey.api.data.dialect;

import org.labkey.api.data.DbScope;

import java.sql.DatabaseMetaData;

/**
 * User: adam
 * Date: 2/8/2015
 * Time: 7:45 AM
 */
public interface JdbcMetaDataLocator extends AutoCloseable
{
    @Override
    void close();

    DbScope getScope();
    DatabaseMetaData getDatabaseMetaData();
    String getCatalogName();
    String getSchemaName();
    String getTableName();
    String[] getTableTypes();
}
