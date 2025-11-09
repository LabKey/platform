package org.labkey.api.data;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.attachments.AttachmentType;
import org.labkey.api.data.DatabaseMigrationConfiguration.DefaultDatabaseMigrationConfiguration;
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
import org.labkey.api.util.StringUtilsLabKey;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.vfs.FileLike;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
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
    default void registerMigrationFilter(MigrationFilter filter) {}

    default @Nullable MigrationFilter getMigrationFilter(String propertyName)
    {
        return null;
    }

    default void copySourceTableToTargetTable(DatabaseMigrationConfiguration configuration, TableInfo sourceTable, TableInfo targetTable, DbSchemaType schemaType, boolean updateSequences, String additionalLogMessage, MigrationSchemaHandler schemaHandler) {}
    default void updateSequences(TableInfo sourceTable, TableInfo targetTable) {}

    interface MigrationSchemaHandler
    {
        // Marker for tables to declare themselves as site-wide (no container filtering)
        FieldKey SITE_WIDE_TABLE = FieldKey.fromParts("site-wide");

        DbSchema getSchema();

        void beforeVerification();

        void beforeSchema();

        List<TableInfo> getTablesToCopy();

        // Create a filter clause that selects from all specified containers and (in some overrides) applies table-specific filters
        FilterClause getTableFilterClause(TableInfo sourceTable, Set<GUID> containers);

        // Create a filter clause that selects from all specified containers
        FilterClause getContainerClause(TableInfo sourceTable, Set<GUID> containers);

        // Return the FieldKey that can be used to filter this table by container. Special values SITE_WIDE_TABLE and
        // DUMMY_FIELD_KEY can be returned for special behaviors. DUMMY_FIELD_KEY ensures that the handler's custom
        // getContainerClause() is always called. SITE_WIDE_TABLE is used to select all rows.
        @Nullable FieldKey getContainerFieldKey(TableInfo sourceTable);

        // Create a filter clause that selects all rows from unfiltered containers plus filtered rows from the filtered containers
        FilterClause getDomainDataFilterClause(Set<GUID> copyContainers, Set<GUID> filteredContainers, List<DataFilter> domainFilters, TableInfo sourceTable, Set<String> selectColumnNames);

        void addDomainDataFilterClause(OrClause orClause, DataFilter filter, TableInfo sourceTable, Set<String> selectColumnNames);

        // Do any necessary clean up after the target table has been populated. notCopiedFilter selects all rows in the
        // source table that were NOT copied to the target table. (For example, rows in a global table not copied due to
        // container filtering or rows in a provisioned table not copied due to domain data filtering.)
        void afterTable(TableInfo sourceTable, TableInfo targetTable, SimpleFilter notCopiedFilter);

        void afterSchema(DatabaseMigrationConfiguration configuration, DbSchema sourceSchema, DbSchema targetSchema);

        // TODO: Return Collection<AttachmentType>, indicating which attachment types it handled?
        void copyAttachments(DatabaseMigrationConfiguration configuration, DbSchema sourceSchema, DbSchema targetSchema);

        @NotNull Collection<AttachmentType> getAttachmentTypes();
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
        public FilterClause getTableFilterClause(TableInfo sourceTable, Set<GUID> containers)
        {
            return getContainerClause(sourceTable, containers);
        }

        @Override
        public FilterClause getContainerClause(TableInfo sourceTable, Set<GUID> containers)
        {
            FieldKey containerFieldKey = getContainerFieldKey(sourceTable);

            if (containerFieldKey == SITE_WIDE_TABLE)
                return new SQLClause(new SQLFragment("TRUE"));

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
        public final FilterClause getDomainDataFilterClause(Set<GUID> copyContainers, Set<GUID> filteredContainers, List<DataFilter> domainFilters, TableInfo sourceTable, Set<String> selectColumnNames)
        {
            // Filtered case: remove the filtered containers from the unconditional container set
            Set<GUID> otherContainers = new HashSet<>(copyContainers);
            otherContainers.removeAll(filteredContainers);
            FilterClause ret = getContainerClause(sourceTable, otherContainers);

            OrClause orClause = new OrClause();

            // Delegate to the MigrationSchemaHandler to add domain-filtered containers back with their special filter applied
            domainFilters.forEach(filter -> addDomainDataFilterClause(orClause, filter, sourceTable, selectColumnNames));

            if (!orClause.getClauses().isEmpty())
            {
                orClause.addClause(ret);
                ret = orClause;
            }

            return ret;
        }

        @Override
        public void addDomainDataFilterClause(OrClause orClause, DataFilter filter, TableInfo sourceTable, Set<String> selectColumnNames)
        {
            addDataFilterClause(orClause, filter, sourceTable, selectColumnNames);
        }

        // Add a filter and return true if the column exists directly on the table
        protected boolean addDataFilterClause(OrClause orClause, DataFilter filter, TableInfo sourceTable, Set<String> selectColumnNames)
        {
            boolean columnExists = selectColumnNames.contains(filter.column());

            if (columnExists)
            {
                // Select all rows in this domain-filtered container that meet its criteria
                orClause.addClause(
                    new AndClause(
                        getContainerClause(sourceTable, filter.containers()),
                        filter.condition()
                    )
                );
            }

            return columnExists;
        }

        // Add a clause that selects all rows where the object property with <propertyId> equals the filter value. This
        // is only for provisioned tables that lack an ObjectId, MaterialId, or DataId column.
        protected void addObjectPropertyClause(OrClause orClause, DataFilter filter, TableInfo sourceTable, int propertyId)
        {
            SQLFragment flagWhere = new SQLFragment("lsid IN (SELECT ObjectURI FROM exp.Object o INNER JOIN exp.ObjectProperty op ON o.ObjectId = op.ObjectId WHERE StringValue = ? AND PropertyId = ?)", filter.condition().getParamVals()[0], propertyId);

            orClause.addClause(
                new AndClause(
                    getContainerClause(sourceTable, filter.containers()),
                    new SQLClause(flagWhere)
                )
            );
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

        protected String rowsNotCopied(int count)
        {
            return "   " + StringUtilsLabKey.pluralize(count, "row") + " not copied";
        }

        @Override
        public void afterTable(TableInfo sourceTable, TableInfo targetTable, SimpleFilter notCopiedFilter)
        {
        }

        @Override
        public void copyAttachments(DatabaseMigrationConfiguration configuration, DbSchema sourceSchema, DbSchema targetSchema)
        {
            // Now that the target tables in this schema have been populated, copy all associated attachments. By
            // default, use this handler's attachment types to select from the target tables all EntityIds that might be
            // attachment parents (this avoids re-running potentially expensive queries on the source tables). Use the
            // set of EntityIds to copy those attachments from the core.Documents table in the source database. Override
            // if special behavior is required, for example, AttachmentTypes that use documentNameColumn since that
            // requires querying and re-filtering the source tables instead.
            getAttachmentTypes().forEach(type -> {
                SQLFragment sql = type.getSelectParentEntityIdsSql();
                if (sql != null)
                {
                    Collection<String> entityIds = new SqlSelector(targetSchema, sql).getCollection(String.class);
                    SQLFragment selectParents = new SQLFragment("Parent");
                    // This query against the source database is likely to contain a large IN clause, so use an alternative InClauseGenerator
                    sourceSchema.getSqlDialect().appendInClauseSql(selectParents, entityIds, getTempTableInClauseGenerator(sourceSchema.getScope()));
                    copyAttachments(configuration, sourceSchema, new SQLClause(selectParents), type);
                }

                // TODO: Fix & test issues attachment copy - need to invoke after provisioned table
                // TODO: afterMigration() and update core.Documents' sequence
                // TODO: implement a bunch more AttachmentTypes
                // TODO: throw if some registered AttachmentType is not seen
                // TODO: fail if type.getSelectParentEntityIdsSql() returns null
            });
        }

        // Creates an TempTableInClauseGenerator that targets the *source* temp schema instead of the default
        // DbSchema.getTemp(). Required for large IN clauses against the source database.
        protected InClauseGenerator getTempTableInClauseGenerator(DbScope sourceScope)
        {
            return new TempTableInClauseGenerator(() -> sourceScope.getSchema("temp", DbSchemaType.Bare));
        }

        private static final Set<AttachmentType> SEEN = new HashSet<>();

        // Copy all core.Documents rows that match the provided filter clause
        protected void copyAttachments(DatabaseMigrationConfiguration configuration, DbSchema sourceSchema, FilterClause filterClause, AttachmentType type)
        {
            SEEN.add(type);
            String additionalMessage = " associated with " + type.getClass().getSimpleName();
            TableInfo sourceDocumentsTable = sourceSchema.getScope().getSchema("core", DbSchemaType.Migration).getTable("Documents");
            TableInfo targetDocumentsTable = CoreSchema.getInstance().getTableInfoDocuments();
            DatabaseMigrationService.get().copySourceTableToTargetTable(configuration, sourceDocumentsTable, targetDocumentsTable, DbSchemaType.Module, false, additionalMessage, new DefaultMigrationSchemaHandler(CoreSchema.getInstance().getSchema()){
                @Override
                public FilterClause getTableFilterClause(TableInfo sourceTable, Set<GUID> containers)
                {
                    return filterClause;
                }
            });
        }

        public static void logUnseenAttachmentTypes()
        {
            Set<AttachmentType> unseen = new HashSet<>(AttachmentService.get().getAttachmentTypes());
            unseen.removeAll(SEEN);

            LOG.info("These AttachmentTypes have not been seen: {}", unseen.stream().map(type -> type.getClass().getSimpleName()).collect(Collectors.joining(", ")));
        }

        @Override
        public @NotNull Collection<AttachmentType> getAttachmentTypes()
        {
            return List.of();
        }

        @Override
        public void afterSchema(DatabaseMigrationConfiguration configuration, DbSchema sourceSchema, DbSchema targetSchema)
        {
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
        // Implementations should validate guid nullity
        void saveFilter(@Nullable GUID guid, String value);
    }

    interface ExperimentDeleteService
    {
        static @NotNull ExperimentDeleteService get()
        {
            ExperimentDeleteService ret = ServiceRegistry.get().getService(ExperimentDeleteService.class);
            if (ret == null)
                throw new IllegalStateException("ExperimentDeleteService not found");
            return ret;
        }

        static void setInstance(ExperimentDeleteService impl)
        {
            ServiceRegistry.get().registerService(ExperimentDeleteService.class, impl);
        }

        /**
         * Deletes all rows from exp.Data, exp.Object, and related tables associated with the provided ObjectIds
         */
        void deleteDataRows(Collection<Long> objectIds);
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
