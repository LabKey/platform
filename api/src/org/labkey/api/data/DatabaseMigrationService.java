package org.labkey.api.data;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CopyOnWriteCaseInsensitiveHashMap;
import org.labkey.api.data.DatabaseMigrationConfiguration.DefaultDatabaseMigrationConfiguration;
import org.labkey.api.data.SimpleFilter.FilterClause;
import org.labkey.api.data.SimpleFilter.InClause;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.query.TableSorter;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.vfs.FileLike;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public interface DatabaseMigrationService
{
    Logger LOG = LogHelper.getLogger(DatabaseMigrationService.class, "Information about database migration");

    static @NotNull DatabaseMigrationService get()
    {
        DatabaseMigrationService ret = ServiceRegistry.get().getService(DatabaseMigrationService.class);
        return ret != null ? ret : new DatabaseMigrationService() {};
    }

    static void setInstance(DatabaseMigrationService impl)
    {
        ServiceRegistry.get().registerService(DatabaseMigrationService.class, impl);
    }

    default DatabaseMigrationConfiguration getDatabaseMigrationConfiguration(FileLike labkeyRoot, @Nullable String migration)
    {
        return new DefaultDatabaseMigrationConfiguration();
    }

    // By default, no-op implementation that simply logs
    default void migrate(DatabaseMigrationConfiguration configuration)
    {
        LOG.warn("Database migration service is not present; database migration is a premium feature.");
    }

    // By default, no-op implementations
    default void registerHandler(MigrationSchemaHandler schemaHandler) {}
    default void registerHandler(MigrationTableHandler tableHandler) {}

    Map<String, MigrationFilter> _migrationFilters = new CopyOnWriteCaseInsensitiveHashMap<>();

    default void registerMigrationFilter(MigrationFilter filter)
    {
        _migrationFilters.put(filter.getName(), filter);
    }

    default @Nullable MigrationFilter getMigrationFilter(String propertyName)
    {
        return null;
    }

    interface MigrationSchemaHandler
    {
        // Marker for tables to declare themselves as site-wide (no container filtering)
        FieldKey SITE_WIDE_TABLE = FieldKey.fromParts("site-wide");

        DbSchema getSchema();

        void beforeVerification();

        void beforeSchema();

        List<TableInfo> getTablesToCopy();

        FilterClause getContainerClause(TableInfo sourceTable, FieldKey containerFieldKey, Set<String> containers);

        @Nullable FieldKey getContainerFieldKey(TableInfo sourceTable);

        void afterSchema();
    }

    class DefaultMigrationSchemaHandler implements MigrationSchemaHandler
    {
        private final DbSchema _schema;

        public DefaultMigrationSchemaHandler(DbSchema schema)
        {
            _schema = schema;
        }

        @Override
        public DbSchema getSchema()
        {
            return _schema;
        }

        @Override
        public void beforeVerification()
        {
        }

        @Override
        public void beforeSchema()
        {
        }

        @Override
        public List<TableInfo> getTablesToCopy()
        {
            Set<TableInfo> sortedTables = new LinkedHashSet<>(TableSorter.sort(getSchema(), true));

            Set<TableInfo> allTables = getSchema().getTableNames().stream()
                .map(getSchema()::getTable)
                .collect(Collectors.toCollection(HashSet::new));
            allTables.removeAll(sortedTables);

            if (!allTables.isEmpty())
            {
                LOG.info("These tables were removed by TableSorter: {}", allTables);
            }

            return sortedTables.stream()
                // Skip all views and virtual tables (e.g., test.Containers2, which is a table on SS but a view on PG)
                .filter(table -> table.getTableType() == DatabaseTableType.TABLE)
                .collect(Collectors.toCollection(ArrayList::new)); // Ensure mutable
        }

        @Override
        public FilterClause getContainerClause(TableInfo sourceTable, FieldKey containerFieldKey, Set<String> containers)
        {
            return new InClause(containerFieldKey, containers);
        }

        @Override
        public @Nullable FieldKey getContainerFieldKey(TableInfo table)
        {
            FieldKey fKey = table.getContainerFieldKey();

            if (fKey != null)
                return fKey;

            for (ColumnInfo col : table.getColumns())
            {
                ForeignKey fk = TableSorter.getForeignKey(table, col, true);
                if (fk != null)
                {
                    // Use the table's schema (or a migration schema retrieved from the table's scope), since we want a Migration schema with XML metadata applied
                    DbSchema tableSchema = table.getSchema();
                    DbSchema lookupSchema = fk.getLookupSchemaKey().equals(new SchemaKey(null, tableSchema.getName())) ?
                        tableSchema :
                        tableSchema.getScope().getSchema(fk.getLookupSchemaName(), DbSchemaType.Migration);
                    TableInfo lookupTableInfo = lookupSchema.getTable(fk.getLookupTableName());
                    if (lookupTableInfo != null)
                    {
                        fKey = lookupTableInfo.getContainerFieldKey();

                        if (null == fKey)
                        {
                            // Ignore self joins
                            if (!lookupTableInfo.getName().equalsIgnoreCase(table.getName()))
                            {
                                fKey = getContainerFieldKey(lookupTableInfo);
                            }
                        }

                        if (fKey != null)
                            return FieldKey.fromParts(col.getFieldKey(), fKey);
                    }
                }
            }

            return null;
        }

        @Override
        public void afterSchema()
        {
        }
    }

    /**
     * Rarely needed, this interface allows a module to provide a clause that filters the rows of another module's
     * table. The specific use case: Core manages core.Documents and LabBook implements its global attachment manager
     * on top of core.Documents. When copying data from core.Documents, we want LabBook to filter out the rows that
     * are not referenced by notebooks in the subset of containers being copied.
     */
    interface MigrationTableHandler
    {
        TableInfo getTableInfo();
        FilterClause getAdditionalFilterClause(Set<String> containers);
    }

    abstract class DefaultMigrationTableHandler implements MigrationTableHandler
    {
        private final TableInfo _tableInfo;

        public DefaultMigrationTableHandler(TableInfo tableInfo)
        {
            _tableInfo = tableInfo;
        }

        @Override
        public TableInfo getTableInfo()
        {
            return _tableInfo;
        }
    }

    /**
     * A MigrationFilter adds support for the named filter property in the migration configuration file. If present,
     * saveFilter() is called with the container guid and property value. Modules can register these to present
     * module-specific filters.
     */
    interface MigrationFilter
    {
        String getName();
        void saveFilter(String guid, String value);
    }
}
