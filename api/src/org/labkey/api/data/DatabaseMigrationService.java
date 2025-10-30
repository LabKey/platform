package org.labkey.api.data;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.DatabaseMigrationConfiguration.DefaultDatabaseMigrationConfiguration;
import org.labkey.api.data.DatabaseMigrationService.MigrationSchemaHandler.Sequence;
import org.labkey.api.data.SimpleFilter.AndClause;
import org.labkey.api.data.SimpleFilter.FilterClause;
import org.labkey.api.data.SimpleFilter.InClause;
import org.labkey.api.data.SimpleFilter.OrClause;
import org.labkey.api.data.SimpleFilter.SQLClause;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.query.TableSorter;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.ConfigurationException;
import org.labkey.api.util.GUID;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.vfs.FileLike;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public interface DatabaseMigrationService
{
    Logger LOG = LogHelper.getLogger(DatabaseMigrationService.class, "Information about database migration");

    record DataFilter(Set<GUID> containers, String column, FilterClause condition) {}

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
    default void registerSchemaHandler(MigrationSchemaHandler schemaHandler) {}
    default void registerTableHandler(MigrationTableHandler tableHandler) {}
    default void registerMigrationFilter(MigrationFilter filter) {}

    default @Nullable MigrationFilter getMigrationFilter(String propertyName)
    {
        return null;
    }

    default void copySourceTableToTargetTable(DatabaseMigrationConfiguration configuration, TableInfo sourceTable, TableInfo targetTable, DbSchemaType schemaType, Map<String, Sequence> schemaSequenceMap, MigrationSchemaHandler schemaHandler) {};

    interface MigrationSchemaHandler
    {
        record Sequence(String schemaName, String tableName, String columnName, long lastValue) {}

        // Marker for tables to declare themselves as site-wide (no container filtering)
        FieldKey SITE_WIDE_TABLE = FieldKey.fromParts("site-wide");

        // Dummy value returned from getContainerFieldKey() to ensure that custom getContainerClause() method is called
        FieldKey DUMMY_FIELD_KEY = FieldKey.fromParts("DUMMY");

        DbSchema getSchema();

        void beforeVerification();

        void beforeSchema();

        List<TableInfo> getTablesToCopy();

        FilterClause getTableFilter(TableInfo sourceTable, FieldKey containerFieldKey, Set<GUID> containers);

        FilterClause getContainerClause(TableInfo sourceTable, FieldKey containerFieldKey, Set<GUID> containers);

        @Nullable FieldKey getContainerFieldKey(TableInfo sourceTable);

        void addDomainDataFilter(OrClause orClause, DataFilter filter, TableInfo sourceTable, FieldKey fKey, Set<String> selectColumnNames);

        // Do any necessary clean up after the target table has been populated. notCopiedFilter selects all rows in the
        // source table that were NOT copied to the target table. (For example, they were filtered out due to container
        // and/or domain data filtering.)
        void afterTable(TableInfo sourceTable, TableInfo targetTable, SimpleFilter notCopiedFilter);

        void afterSchema(DatabaseMigrationConfiguration configuration, DbSchema sourceSchema, DbSchema targetSchema, Map<String, Map<String, Sequence>> sequenceMap);
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
        public FilterClause getTableFilter(TableInfo sourceTable, FieldKey containerFieldKey, Set<GUID> containers)
        {
            return getContainerClause(sourceTable, containerFieldKey, containers);
        }

        @Override
        public FilterClause getContainerClause(TableInfo sourceTable, FieldKey containerFieldKey, Set<GUID> containers)
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
        public void addDomainDataFilter(OrClause orClause, DataFilter filter, TableInfo sourceTable, FieldKey fKey, Set<String> selectColumnNames)
        {
            addDataFilter(orClause, filter, sourceTable, fKey, selectColumnNames);
        }

        // Add a filter and return true if the column exists directly on the table
        protected boolean addDataFilter(OrClause orClause, DataFilter filter, TableInfo sourceTable, FieldKey fKey, Set<String> selectColumnNames)
        {
            boolean columnExists = selectColumnNames.contains(filter.column());

            if (columnExists)
            {
                // Select all rows in this domain-filtered container that meet its criteria
                orClause.addClause(
                    new AndClause(
                        getContainerClause(sourceTable, fKey, filter.containers()),
                        filter.condition()
                    )
                );
            }

            return columnExists;
        }

        // Add a filter to select all rows where the object property with <propertyId> equals the filter value
        protected void addObjectPropertyFilter(OrClause orClause, DataFilter filter, TableInfo sourceTable, FieldKey fKey, int propertyId)
        {
            SQLFragment flagWhere = new SQLFragment("lsid IN (SELECT ObjectURI FROM exp.Object WHERE ObjectId IN (SELECT ObjectId FROM exp.ObjectProperty WHERE StringValue = ? AND PropertyId = ?))", filter.condition().getParamVals()[0], propertyId);

            orClause.addClause(
                new AndClause(
                    getContainerClause(sourceTable, fKey, filter.containers()),
                    new SQLClause(flagWhere)
                )
            );
        }

        // Special domain data filter method for provisioned tables that have a built-in Flag field (currently used by data classes)
        protected void addDomainDataFlagFilter(OrClause orClause, DataFilter filter, TableInfo sourceTable, FieldKey fKey, Set<String> selectColumnNames)
        {
            if (filter.column().equalsIgnoreCase("Flag"))
            {
                addObjectPropertyFilter(orClause, filter, sourceTable, fKey, getCommentPropertyId(sourceTable.getSchema().getScope()));
            }
            else
            {
                addDataFilter(orClause, filter, sourceTable, fKey, selectColumnNames);
            }
        }

        private Integer _commentPropertyId = null;

        protected synchronized int getCommentPropertyId(DbScope scope)
        {
            if (_commentPropertyId == null)
            {
                // Get the exp.PropertyDescriptor table from the source scope
                TableInfo propertyDescriptor = scope.getSchema("exp", DbSchemaType.Migration).getTable("PropertyDescriptor");
                // Select the PropertyId associated with built-in Flag fields ("urn:exp.labkey.org/#Comment")
                Integer propertyId = new TableSelector(propertyDescriptor, Collections.singleton("PropertyId"), new SimpleFilter(FieldKey.fromParts("PropertyURI"), "urn:exp.labkey.org/#Comment"), null).getObject(Integer.class);
                if (propertyId == null)
                    throw new RuntimeException("PropertyDescriptor for built-in Flag field not found");
                else
                    _commentPropertyId = propertyId;
            }

            return _commentPropertyId;
        }

        @Override
        public void afterTable(TableInfo sourceTable, TableInfo targetTable, SimpleFilter notCopiedFilter)
        {
        }

        @Override
        public void afterSchema(DatabaseMigrationConfiguration configuration, DbSchema sourceSchema, DbSchema targetSchema, Map<String, Map<String, Sequence>> sequenceMap)
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
        FilterClause getAdditionalFilterClause(Set<GUID> containers);
    }

    /**
     * A MigrationFilter adds support for the named filter property in the migration configuration file. If present,
     * saveFilter() is called with the container guid and property value. Modules can register these to present
     * module-specific filters.
     */
    interface MigrationFilter
    {
        String getName();
        // Implementations should validate guid nullity
        void saveFilter(@Nullable GUID guid, String value);
    }

    // Helper method that parses a data filter then adds it and its container to the provided collections, coalescing
    // cases where multiple containers specify the same filter
    static void addDataFilter(String filterName, List<DataFilter> dataFilters, Set<GUID> filteredContainers, GUID guid, String filter)
    {
        String[] filterParts = filter.split("=");
        if (filterParts.length != 2)
            throw new ConfigurationException("Bad " + filterName + " value; expected <columnName>=<value>: " + filter);

        if (!filteredContainers.add(guid))
            throw new ConfigurationException("Duplicate " + filterName + " entry for container " + guid);

        String column = filterParts[0];
        String value = filterParts[1];
        FilterClause clause = CompareType.EQUAL.createFilterClause(new FieldKey(null, column), value);
        // If another container is already using this filter clause, then simply add this guid to that domain filter.
        // Otherwise, add a new domain filter to the list.
        dataFilters.stream()
            .filter(df -> df.column().equals(column) && df.condition().equals(clause))
            .findFirst()
            .ifPresentOrElse(df -> df.containers().add(guid), () -> dataFilters.add(new DataFilter(new HashSet<>(Set.of(guid)), filterParts[0], clause)));
    }
}
