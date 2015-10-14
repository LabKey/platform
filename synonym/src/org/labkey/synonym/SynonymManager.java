/*
 * Copyright (c) 2015 LabKey Corporation. All rights reserved. No portion of this work may be reproduced in
 * any form or by any electronic or mechanical means without written permission from LabKey Corporation.
 */
package org.labkey.synonym;

import org.apache.commons.collections15.MultiMap;
import org.apache.commons.collections15.multimap.MultiHashMap;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.cache.Cache;
import org.labkey.api.cache.CacheLoader;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.ColumnInfo.ImportedKey;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.MetadataSqlSelector;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.dialect.ForeignKeyResolver;
import org.labkey.api.data.dialect.StandardForeignKeyResolver;
import org.labkey.api.data.dialect.StandardJdbcMetaDataLocator;
import org.labkey.api.util.Pair;

import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Created by adam on 8/14/2015.
 */
public class SynonymManager
{
    private static final Logger LOG = Logger.getLogger(SynonymManager.class);

    // Cache of synonyms by datasource name. This lets admins force a re-query by clearing caches.
    static final Cache<String, Pair<Map<String, Map<String, Synonym>>, Map<String, Map<String, MultiMap<String, Synonym>>>>> SYNONYM_CACHE = CacheManager.getBlockingStringKeyCache(100, CacheManager.YEAR, "SQL Server synonyms", new SynonymLoader());

    private static final Pair<Map<String, Map<String, Synonym>>, Map<String, Map<String, MultiMap<String, Synonym>>>> NO_SYNONYMS;

    static
    {
        Map<String, Map<String, Synonym>> emptyMap = Collections.emptyMap();
        Map<String, Map<String, MultiMap<String, Synonym>>> emptyReverseMap = Collections.emptyMap();

        NO_SYNONYMS = new Pair<>(emptyMap, emptyReverseMap);
    }

    // Returns a map of synonyms defined in synonymScope that target targetScope. The returned map has the structure:
    // [target schema name] -> [target table/view name] -> Collection<Synonym> since multiple synonyms could be defined
    // on the same table/view.
    private static @NotNull Map<String, MultiMap<String, Synonym>> getReverseMap(DbScope synonymScope, DbScope targetScope)
    {
        return SYNONYM_CACHE.get(synonymScope.getDataSourceName()).second.get(targetScope.getDatabaseName());
    }

    // Returns a map of synonyms defined in the specified scope and schema. The returned map has the structure:
    // [synonym name] -> Synonym
    static @NotNull Map<String, Synonym> getSynonymMap(DbScope scope, String schemaName)
    {
        Map<String, Synonym> synonymMap = SYNONYM_CACHE.get(scope.getDataSourceName()).first.get(schemaName);

        return null == synonymMap ? Collections.<String, Synonym>emptyMap() : synonymMap;
    }

    static @Nullable Pair<DbScope, Synonym> getSynonym(DbScope scope, String schemaName, String requestedTableName)
    {
        Synonym synonym = getSynonymMap(scope, schemaName).get(requestedTableName);

        // tableName is not a synonym... return null
        if (null == synonym)
            return null;

        // tableName is a synonym to an object in the same scope
        if (synonym.getTargetDatabase().equals(scope.getDatabaseName()))
            return new Pair<>(scope, synonym);

        // tableName is a synonym to an object in a different scope... find the corresponding scope so we can return it
        for (DbScope candidate : DbScope.getDbScopes())
            if (candidate.getSqlDialect().isSqlServer() && null != candidate.getDatabaseName() && candidate.getDatabaseName().equalsIgnoreCase(synonym.getTargetDatabase()))
                return new Pair<>(candidate, synonym);

        LOG.error("Could not access metadata for " + synonym + ". No labkey.xml datasource is defined for the target database.");

        return null;
    }

    public static class Synonym
    {
        private String _schemaName;
        private String _name;
        private String _targetServer;
        private String _targetDatabase;
        private String _targetSchema;
        private String _targetTable;

        public Synonym()
        {
        }

        @SuppressWarnings("unused")
        public void setSchemaName(String schemaName)
        {
            _schemaName = schemaName;
        }

        public String getSchemaName()
        {
            return _schemaName;
        }

        @SuppressWarnings("unused")
        public void setName(String name)
        {
            _name = name;
        }

        public String getName()
        {
            return _name;
        }

        @SuppressWarnings("unused")
        public void setTargetServer(String targetServer)
        {
            _targetServer = targetServer;
        }

        @SuppressWarnings("unused")   // NYI
        public String getTargetServer()
        {
            return _targetServer;
        }

        @SuppressWarnings("unused")
        public void setTargetDatabase(String targetDatabase)
        {
            _targetDatabase = targetDatabase;
        }

        public String getTargetDatabase()
        {
            return _targetDatabase;
        }

        @SuppressWarnings("unused")
        public void setTargetSchema(String targetSchema)
        {
            _targetSchema = targetSchema;
        }

        public String getTargetSchema()
        {
            return _targetSchema;
        }

        @SuppressWarnings("unused")
        public void setTargetTable(String targetTable)
        {
            _targetTable = targetTable;
        }

        public String getTargetTable()
        {
            return _targetTable;
        }

        @Override
        public String toString()
        {
            return _schemaName + "." + _name + " -> [" + _targetDatabase + "].[" + _targetSchema + "].[" + _targetTable + "]";
        }
    }

    private static class SynonymLoader implements CacheLoader<String, Pair<Map<String, Map<String, Synonym>>, Map<String, Map<String, MultiMap<String, Synonym>>>>>
    {
        @Override
        public Pair<Map<String, Map<String, Synonym>>, Map<String, Map<String, MultiMap<String, Synonym>>>> load(String key, @Nullable Object argument)
        {
            DbScope scope = DbScope.getDbScope(key);

            // Another option would be to add "INNER JOIN master.dbo.sysdatabases" to the SELECT DISTINCT COALESCE query below.
            // That might be a bit cleaner, but this code approach lets us detect these invalid synonyms and log a warning.
            final Set<String> validDatabaseNames = new CaseInsensitiveHashSet(new MetadataSqlSelector(scope, new SQLFragment("SELECT Name FROM master.dbo.sysdatabases")).getCollection(String.class));

            // A synonym in this scope might target other databases. We need to construct SQL that joins each synonym definition
            // to the sys.objects table its own target database, so generate a crazy UNION query with one SELECT clause per
            // distinct database.

            final String part1 = "SELECT s.* FROM (SELECT \n" +
                "SCHEMA_NAME(schema_id) AS SchemaName,\n" +
                "Name,\n" +
                "COALESCE(PARSENAME(base_object_name, 4), @@servername) AS TargetServer,\n" +
                "COALESCE(PARSENAME(base_object_name, 3), DB_NAME(DB_ID())) AS TargetDatabase,\n" +
                "COALESCE(PARSENAME(base_object_name, 2), SCHEMA_NAME(SCHEMA_ID())) AS TargetSchema,\n" +
                "PARSENAME(base_object_name, 1) AS TargetTable,\n" +
                "OBJECT_ID(base_object_name) AS ObjectId\n" +
                "FROM sys.synonyms) s\n" +
                "INNER JOIN [";
            final String part2 = "].sys.objects ON ObjectId = object_id\n" +
                "WHERE TargetServer = @@servername AND TargetDatabase = ? AND Type IN ('U', 'V')\n";  // Filter to current server only... we don't support linked servers (yet)

            final SQLFragment sql = new SQLFragment();

            // Select the distinct target databases for all synonyms... and then build the UNION query
            new MetadataSqlSelector(scope, "SELECT DISTINCT COALESCE(PARSENAME(base_object_name, 3), DB_NAME(DB_ID())) AS TargetDatabase FROM sys.synonyms").forEach(dbName -> {
                if (!validDatabaseNames.contains(dbName))
                {
                    LOG.error("One or more synonyms are defined as targeting database \"" + dbName + "\", which doesn't exist. These synonyms will be ignored.");
                }
                else
                {
                    if (!sql.isEmpty())
                        sql.append("\nUNION\n\n");
                    sql.append(part1);
                    sql.append(dbName);  // dbName is in brackets, so reasonably escaped
                    sql.append(part2);
                    sql.add(dbName);     // this dbName can be a parameter
                }
            }, String.class);

            // No synonyms... carry on
            if (sql.isEmpty())
                return NO_SYNONYMS;

            final Map<String, Map<String, Synonym>> synonymMap = new HashMap<>();
            final Map<String, Map<String, MultiMap<String, Synonym>>> reverseMap = new HashMap<>();

            new MetadataSqlSelector(scope, sql).forEach(synonym -> {
                Map<String, Synonym> map = synonymMap.get(synonym.getSchemaName());

                if (null == map)
                {
                    map = new HashMap<>();
                    synonymMap.put(synonym.getSchemaName(), map);
                }

                map.put(synonym.getName(), synonym);

                Map<String, MultiMap<String, Synonym>> schemaMap = reverseMap.get(synonym.getTargetDatabase());

                if (null == schemaMap)
                {
                    schemaMap = new HashMap<>();
                    reverseMap.put(synonym.getTargetDatabase(), schemaMap);
                }

                MultiMap<String, Synonym> tableMap = schemaMap.get(synonym.getTargetSchema());

                if (null == tableMap)
                {
                    tableMap = new MultiHashMap<>();
                    schemaMap.put(synonym.getTargetSchema(), tableMap);
                }

                tableMap.put(synonym.getTargetTable(), synonym);
            }, Synonym.class);

            return new Pair<>(synonymMap, reverseMap);
        }
    }

    static class SynonymJdbcMetaDataLocator extends StandardJdbcMetaDataLocator
    {
        private final ForeignKeyResolver _resolver;

        SynonymJdbcMetaDataLocator(DbScope targetScope, DbScope synonymScope, Synonym synonym) throws SQLException
        {
            super(targetScope, synonym.getTargetSchema(), synonym.getTargetTable());
            _resolver = new SynonymForeignKeyResolver(targetScope, synonymScope, synonym);
        }

        @Override
        public ImportedKey getImportedKey(String fkName, String pkSchemaName, String pkTableName, String pkColumnName, String colName)
        {
            return _resolver.getImportedKey(fkName, pkSchemaName, pkTableName, pkColumnName, colName);
        }
    }

    static class SynonymForeignKeyResolver extends StandardForeignKeyResolver
    {
        private final DbScope _targetScope;
        private final DbScope _synonymScope;
        private final Synonym _synonym;

        public SynonymForeignKeyResolver(DbScope targetScope, DbScope synonymScope, Synonym synonym)
        {
            _targetScope = targetScope;
            _synonymScope = synonymScope;
            _synonym = synonym;
        }

        @Override
        public ImportedKey getImportedKey(String fkName, String pkSchemaName, String pkTableName, String pkColumnName, String colName)
        {
            Map<String, MultiMap<String, Synonym>> reverseMap = getReverseMap(_synonymScope, _targetScope);

            // Additional logging to track down repro for #23100
            if (null == reverseMap)
            {
                LOG.info("No reverse map from " + _synonymScope + " to " + _targetScope + ", trying to resolve " + fkName + " (" + pkSchemaName + "." + pkTableName + "." + pkColumnName + " -> " + colName + ")");
            }
            else
            {
                MultiMap<String, Synonym> mmap = reverseMap.get(pkSchemaName);

                if (null != mmap)
                {
                    Collection<Synonym> synonyms = mmap.get(pkTableName);

                    if (null != synonyms)
                    {
                        for (Synonym synonym : synonyms)
                        {
                            if (_synonym.getSchemaName().equals(synonym.getSchemaName()))
                            {
                                return super.getImportedKey(fkName, synonym.getSchemaName(), synonym.getName(), pkColumnName, colName);
                            }
                        }

                        // TODO: Otherwise pick the first one?
                    }
                }
            }

            return super.getImportedKey(fkName, pkSchemaName, pkTableName, pkColumnName, colName);
        }
    }
}
