package org.labkey.api.data;

import java.sql.SQLException;

/**
 * Created by adam on 9/8/2015.
 */
public interface SchemaTableInfoFactory
{
    String getTableName();
    SchemaTableInfo getSchemaTableInfo(DbSchema schema) throws SQLException;
}
