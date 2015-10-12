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
package org.labkey.synonym;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.DatabaseTableType;
import org.labkey.api.data.DbSchema.TableMetaDataLoader;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.SchemaTableInfoFactory;
import org.labkey.api.data.StandardSchemaTableInfoFactory;
import org.labkey.api.data.dialect.ForeignKeyResolver;
import org.labkey.api.data.dialect.JdbcMetaDataLocator;
import org.labkey.api.data.dialect.StandardTableResolver;
import org.labkey.api.util.Pair;
import org.labkey.synonym.SynonymManager.Synonym;
import org.labkey.synonym.SynonymManager.SynonymForeignKeyResolver;
import org.labkey.synonym.SynonymManager.SynonymJdbcMetaDataLocator;

import java.sql.SQLException;
import java.util.Map;

/**
 * Created by adam on 8/14/2015.
 */
public class SynonymTableResolver extends StandardTableResolver
{
    @Override
    public void addTableInfoFactories(Map<String, SchemaTableInfoFactory> map, DbScope scope, String schemaName) throws SQLException
    {
        Map<String, Synonym> synonymMap = SynonymManager.getSynonymMap(scope, schemaName);

        // Put a SchemaTableInfoFactory into the map for each synonym
        for (String synonymName : synonymMap.keySet())
        {
            try (JdbcMetaDataLocator locator = getJdbcMetaDataLocator(scope, schemaName, synonymName))
            {
                new TableMetaDataLoader<SchemaTableInfoFactory>(locator, true)
                {
                    @Override
                    protected void handleTable(String tableName, DatabaseTableType tableType, String description) throws SQLException
                    {
                        map.put(synonymName, new StandardSchemaTableInfoFactory(synonymName, tableType, description));
                    }
                }.load();
            }
        }
    }

    @Override
    public JdbcMetaDataLocator getJdbcMetaDataLocator(DbScope scope, @Nullable String schemaName, @Nullable String requestedTableName) throws SQLException
    {
        Pair<DbScope, Synonym> pair = SynonymManager.getSynonym(scope, schemaName, requestedTableName);

        if (null == pair)
            return super.getJdbcMetaDataLocator(scope, schemaName, requestedTableName);      // Not a valid synonym, so return the standard locator
        else
            return new SynonymJdbcMetaDataLocator(pair.first, scope, pair.second);  // tableName is a synonym, so return a synonym locator
    }

    @Override
    public ForeignKeyResolver getForeignKeyResolver(DbScope scope, @Nullable String schemaName, @Nullable String tableName)
    {
        Pair<DbScope, Synonym> pair = SynonymManager.getSynonym(scope, schemaName, tableName);

        if (null == pair)
            return super.getForeignKeyResolver(scope, schemaName, tableName);       // Not a synonym, so return the standard resolver
        else
            return new SynonymForeignKeyResolver(pair.first, scope, pair.second);   // tableName is a synonym, so return a synonym resolver
    }
}
