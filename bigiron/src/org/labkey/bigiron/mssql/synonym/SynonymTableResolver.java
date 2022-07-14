/*
 * Copyright (c) 2015-2018 LabKey Corporation. All rights reserved. No portion of this work may be reproduced in
 * any form or by any electronic or mechanical means without written permission from LabKey Corporation.
 */
package org.labkey.bigiron.mssql.synonym;

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
import org.labkey.bigiron.mssql.synonym.SynonymManager.Synonym;
import org.labkey.bigiron.mssql.synonym.SynonymManager.SynonymForeignKeyResolver;
import org.labkey.bigiron.mssql.synonym.SynonymManager.SynonymJdbcMetaDataLocator;

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
            try (JdbcMetaDataLocator locator = getSingleTableLocator(scope, schemaName, synonymName))
            {
                new TableMetaDataLoader<SchemaTableInfoFactory>(locator, true)
                {
                    @Override
                    protected void handleTable(String tableName, DatabaseTableType tableType, String description)
                    {
                        map.put(synonymName, new StandardSchemaTableInfoFactory(synonymName, tableType, description));
                    }
                }.load();
            }
        }
    }

    @Override
    public JdbcMetaDataLocator getSingleTableLocator(DbScope scope, String schemaName, String tableName) throws SQLException
    {
        Pair<DbScope, Synonym> pair = SynonymManager.getSynonym(scope, schemaName, tableName);

        if (null == pair)
            return super.getSingleTableLocator(scope, schemaName, tableName); // Not a valid synonym, so standard handling

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
