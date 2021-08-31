/*
 * Copyright (c) 2015 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.api.data.dialect;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.TableInfo;

import java.sql.DatabaseMetaData;
import java.sql.SQLException;

/**
 * User: adam
 * Date: 2/8/2015
 * Time: 7:45 AM
 *
 * JDBC metadata methods are inconsistent with their parameters. The schema and table parameters are sometimes patterns
 * and sometimes simple strings. Callers must be very careful to review the metadata method JavaDocs and use the
 * appropriate parameter-providing methods: *NamePattern() methods for the pattern parameters and *Name() methods for
 * the string-providing parameters. This ensures correct escaping of special characters; see #43821.
 */
public interface JdbcMetaDataLocator extends AutoCloseable, ForeignKeyResolver
{
    @Override
    void close();

    // Once the implementation is constructed, one of these schema methods must be called...
    JdbcMetaDataLocator singleSchema(@NotNull String schemaName);
    JdbcMetaDataLocator allSchemas();

    // ...followed by one of these table methods.
    JdbcMetaDataLocator singleTable(@NotNull String tableName) throws SQLException;
    JdbcMetaDataLocator singleTable(@NotNull TableInfo tableInfo) throws SQLException;
    JdbcMetaDataLocator allTables();

    DbScope getScope();
    DatabaseMetaData getDatabaseMetaData();
    String getCatalogName();
    String getSchemaName();
    String getSchemaNamePattern();
    String getTableName();
    String getTableNamePattern();
    String[] getTableTypes();
    boolean supportsSchemas();
}
