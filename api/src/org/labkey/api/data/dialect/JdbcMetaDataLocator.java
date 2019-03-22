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

/**
 * User: adam
 * Date: 2/8/2015
 * Time: 7:45 AM
 */
public interface JdbcMetaDataLocator extends AutoCloseable, ForeignKeyResolver
{
    @Override
    void close();

    DbScope getScope();
    DatabaseMetaData getDatabaseMetaData();
    String getCatalogName();
    String getSchemaName();
    String getTableName();
    String[] getTableTypes();
    boolean supportsSchemas();
}
