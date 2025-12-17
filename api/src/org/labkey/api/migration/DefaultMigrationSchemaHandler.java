package org.labkey.api.migration;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.attachments.AttachmentParentType;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DatabaseTableType;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.ForeignKey;
import org.labkey.api.data.InClauseGenerator;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SimpleFilter.AndClause;
import org.labkey.api.data.SimpleFilter.FilterClause;
import org.labkey.api.data.SimpleFilter.InClause;
import org.labkey.api.data.SimpleFilter.OrClause;
import org.labkey.api.data.SimpleFilter.SQLClause;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.data.TempTableInClauseGenerator;
import org.labkey.api.migration.DatabaseMigrationService.DataFilter;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.query.TableSorter;
import org.labkey.api.util.ConfigurationException;
import org.labkey.api.util.GUID;
import org.labkey.api.util.JobRunner;
import org.labkey.api.util.StringUtilsLabKey;
import org.labkey.api.util.logging.LogHelper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class DefaultMigrationSchemaHandler implements MigrationSchemaHandler
{
    private static final Logger LOG = LogHelper.getLogger(DefaultMigrationSchemaHandler.class, "Migration shutdown status");

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
    public void copyAttachments(DatabaseMigrationConfiguration configuration, DbSchema sourceSchema, DbSchema targetSchema, Set<GUID> copyContainers)
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
                sourceSchema.getSqlDialect().appendInClauseSqlWithCustomInClauseGenerator(selectParents, entityIds, getTempTableInClauseGenerator(sourceSchema.getScope()));
                copyAttachments(configuration, sourceSchema, new SQLClause(selectParents), type);
            }
            else
            {
                throw new ConfigurationException("AttachmentType \"" + type.getUniqueName() + "\" is not configured to find parent EntityIds!");
            }
        });
    }

    // Creates a TempTableInClauseGenerator that targets the *source* temp schema instead of the default
    // DbSchema.getTemp(). Required for large IN clauses used against the source database.
    protected InClauseGenerator getTempTableInClauseGenerator(DbScope sourceScope)
    {
        return new TempTableInClauseGenerator(() -> sourceScope.getSchema("temp", DbSchemaType.Bare));
    }

    private static final Set<AttachmentParentType> SEEN = new HashSet<>();
    private static final JobRunner ATTACHMENT_JOB_RUNNER = new JobRunner("Attachment JobRunner", 1, () -> "Attachments");

    // Copy all core.Documents rows that match the provided filter clause
    protected final void copyAttachments(DatabaseMigrationConfiguration configuration, DbSchema sourceSchema, FilterClause filterClause, AttachmentParentType... type)
    {
        SEEN.addAll(Arrays.asList(type));
        String additionalMessage = " associated with " + Arrays.stream(type).map(t -> t.getClass().getSimpleName()).collect(Collectors.joining(", "));
        TableInfo sourceDocumentsTable = sourceSchema.getScope().getSchema("core", DbSchemaType.Migration).getTable("Documents");
        TableInfo targetDocumentsTable = CoreSchema.getInstance().getTableInfoDocuments();

        // Queue up the core.Documents transfers and let them run in the background
        ATTACHMENT_JOB_RUNNER.execute(() -> DatabaseMigrationService.get().copySourceTableToTargetTable(configuration, sourceDocumentsTable, targetDocumentsTable, DbSchemaType.Module, false, additionalMessage, new DefaultMigrationSchemaHandler(CoreSchema.getInstance().getSchema())
        {
            @Override
            public FilterClause getTableFilterClause(TableInfo sourceTable, Set<GUID> containers)
            {
                return filterClause;
            }
        }));
    }

    // Global (not schema- or configuration-specific) cleanup
    public static void afterMigration() throws InterruptedException
    {
        // Report any unseen attachment types
        Set<AttachmentParentType> unseen = new HashSet<>(AttachmentService.get().getAttachmentParentTypes());
        unseen.removeAll(SEEN);

        if (unseen.isEmpty())
            LOG.info("All AttachmentTypes have been seen");
        else
            LOG.error("These AttachmentTypes have not been seen: {}", unseen.stream().map(type -> type.getClass().getSimpleName()).collect(Collectors.joining(", ")));

        // Shut down the attachment JobRunner
        LOG.info("Waiting for attachments background transfer to complete");
        ATTACHMENT_JOB_RUNNER.shutdown();
        if (ATTACHMENT_JOB_RUNNER.awaitTermination(2, TimeUnit.HOURS))
            LOG.info("Attachments background transfer is complete");
        else
            LOG.error("Attachments background transfer did not complete after two hours! Giving up.");
    }

    @Override
    public @NotNull Collection<AttachmentParentType> getAttachmentTypes()
    {
        return List.of();
    }

    @Override
    public void afterSchema(DatabaseMigrationConfiguration configuration, DbSchema sourceSchema, DbSchema targetSchema)
    {
    }

    @Override
    public void afterMigration(DatabaseMigrationConfiguration configuration)
    {
    }

    @Override
    public void writeFilePaths(FilePathWriter writer, Set<GUID> guids)
    {
    }
}
