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
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.SchemaTableInfoFactory;
import org.labkey.api.data.TableInfo;

import java.sql.SQLException;
import java.util.Map;

/**
 * Created by adam on 8/14/2015.
 */
public class StandardTableResolver implements TableResolver
{
    // Do nothing by default
    @Override
    public void addTableInfoFactories(Map<String, SchemaTableInfoFactory> map, DbScope scope, String schemaName) throws SQLException
    {
    }

    @Override
    public JdbcMetaDataLocator getJdbcMetaDataLocator(DbScope scope, String schemaName, String schemaNamePattern, String tableName, String tableNamePattern) throws SQLException
    {
        return new StandardJdbcMetaDataLocator(scope, schemaName, schemaNamePattern, tableName, tableNamePattern);
    }

    @Override
    public JdbcMetaDataLocator getAllSchemasLocator(DbScope scope) throws SQLException
    {
        return getJdbcMetaDataLocator(scope, null, "%", null, "%");
    }

    @Override
    public JdbcMetaDataLocator getAllTablesLocator(DbScope scope, String schemaName) throws SQLException
    {
        return getJdbcMetaDataLocator(scope, schemaName, escapeName(scope, schemaName), null, "%");
    }

    @Override
    public JdbcMetaDataLocator getSingleTableLocator(DbScope scope, String schemaName, String tableName) throws SQLException
    {
        return getJdbcMetaDataLocator(scope, schemaName, escapeName(scope, schemaName), tableName, escapeName(scope, tableName));
    }

    @Override
    public JdbcMetaDataLocator getSingleTableLocator(DbScope scope, String schemaName, TableInfo tableInfo) throws SQLException
    {
        return getSingleTableLocator(scope, schemaName, tableInfo.getMetaDataName());
    }

    private static final ForeignKeyResolver STANDARD_RESOLVER = new StandardForeignKeyResolver();

    @Override
    public ForeignKeyResolver getForeignKeyResolver(DbScope scope, @Nullable String schemaName, @Nullable String tableName)
    {
        return STANDARD_RESOLVER;
    }

    // We must escape LIKE wild card characters in cases where we're passing a single table or schema name as a pattern
    // parameter, see #43821
    public static String escapeName(DbScope scope, @NotNull String name)
    {
        String escape = scope.getSearchStringEscape();

        String ret = name.replace(escape, escape + escape);
        ret = ret.replace("_", escape + "_");
        ret = ret.replace("%", escape + "%");

        return ret;
    }
}
