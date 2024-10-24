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

import org.labkey.api.data.DbScope;

import java.sql.DatabaseMetaData;
import java.sql.SQLException;

/**
 * JDBC metadata methods are inconsistent with their parameters. The schema and table parameters are sometimes patterns
 * and sometimes simple strings. Callers must be very careful to review the metadata method JavaDocs and use the
 * appropriate parameter-providing methods: *NamePattern() methods for the pattern parameters and *Name() methods for
 * the string-providing parameters. This ensures correct escaping of special characters; see #43821.
 */
public interface JdbcMetaDataLocator extends AutoCloseable, ForeignKeyResolver
{
    @Override
    void close() throws SQLException;

    DbScope getScope();
    DatabaseMetaData getDatabaseMetaData();
    String getCatalogName();
    String getSchemaName();
    String getSchemaNamePattern();
    String getTableName();
    String getTableNamePattern();
    String[] getTableTypes();
}
