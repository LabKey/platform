package org.labkey.api.module;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.collections.CopyOnWriteCaseInsensitiveHashMap;
import org.labkey.api.collections.LabKeyCollectors;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DatabaseTableType;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SchemaTableInfo;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.api.StorageProvisioner;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.query.TableSorter;
import org.labkey.api.util.ConfigurationException;
import org.labkey.api.util.StringUtilsLabKey;
import org.labkey.api.util.logging.LogHelper;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// Handles SQL Server to PostgreSQL data migration
public class DatabaseMigration
{
    private static final Logger LOG = LogHelper.getLogger(DatabaseMigration.class, "Progress of SQL Server to PostgreSQL database migration");

    // If associated properties are set: clear schemas, verify empty schemas, and migrate data from the external SQL
    // Server data source into the just-created empty PostgreSQL schemas.
    public static void migrate(boolean shouldInsertData, @Nullable String migrationDataSource)
    {
        if (!shouldInsertData)
        {
            clearSchemas();
            verifyEmptySchemas();

            if (migrationDataSource != null)
                migrateDatabase(migrationDataSource);

            System.exit(0);
        }
    }

    // Clear containers needed for bootstrap
    private static void clearSchemas()
    {
        TableInfo containers = CoreSchema.getInstance().getTableInfoContainers();
        Table.delete(containers); // Now that we've bootstrapped, delete root and shared containers
        DbScope targetScope = DbScope.getLabKeyScope();
        new SqlExecutor(targetScope).execute("ALTER SEQUENCE core.containers_rowid_seq RESTART"); // Reset Containers sequence
    }

    // Verify that no data rows were inserted and no sequences were incremented
    private static void verifyEmptySchemas()
    {
        DbScope scope = DbScope.getLabKeyScope();

        Map<Boolean, List<DbSchema>> schemaMap = scope.getSchemaNames().stream()
            .map(name -> scope.getSchema(name, DbSchemaType.Unknown))
            .collect(Collectors.partitioningBy(schema -> schema.getModule() != null && schema.getModule().getSupportedDatabasesSet().contains(SupportedDatabase.mssql)));

        List<DbSchema> targetSchemas = schemaMap.get(true);
        List<String> tableWarnings = targetSchemas.stream()
            .flatMap(schema -> schema.getTableNames().stream()
                .map(schema::getTable)
                .filter(table -> table.getTableType() != DatabaseTableType.NOT_IN_DB)
            )
            .map(table -> {
                long rowCount = new TableSelector(table).getRowCount();
                if (rowCount > 0)
                    return table.getSelectName() + " has " + StringUtilsLabKey.pluralize(rowCount, "row");
                else
                    return null;
            })
            .filter(Objects::nonNull)
            .toList();

        if (!tableWarnings.isEmpty())
        {
            LOG.warn("{} rows", StringUtilsLabKey.pluralize(tableWarnings.size(), "table has", "tables have"));
            tableWarnings.forEach(LOG::warn);
        }

        List<String> schemasToIgnore = schemaMap.get(false).stream()
            .map(DbSchema::getName)
            .toList();
        String qs = StringUtils.join(Collections.nCopies(schemasToIgnore.size(), "?"), ", ");
        List<String> sequenceWarnings = new SqlSelector(scope, new SQLFragment(
            "SELECT schemaname || '.' || sequencename FROM pg_sequences WHERE last_value IS NOT NULL AND schemaname NOT IN (" + qs + ")",
            schemasToIgnore
        ))
            .stream(String.class)
            .toList();

        if (!sequenceWarnings.isEmpty())
        {
            LOG.warn("{} a value:", StringUtilsLabKey.pluralize(sequenceWarnings.size(), "sequence has", "sequences have"));
            sequenceWarnings.forEach(LOG::warn);
        }
    }

    private record Sequence(String schemaName, String tableName, String columnName, int lastValue) {}

    private static void migrateDatabase(String migrationDataSource)
    {
        LOG.info("Starting database migration");

        DbScope targetScope = DbScope.getLabKeyScope();
        DbScope sourceScope = DbScope.getDbScope(migrationDataSource);
        if (null == sourceScope)
            throw new ConfigurationException("Migration data source not found: " + migrationDataSource);
        if (!sourceScope.getSqlDialect().isSqlServer())
            throw new ConfigurationException("Migration data source is not SQL Server: " + migrationDataSource);

        // Verify that all sequences in the target schema have an increment of 1, since that's an assumption below
        Collection<String> sequencesNonOneIncrement = new SqlSelector(targetScope, new SQLFragment("SELECT schemaname || '.' || sequencename || ': ' || increment_by FROM pg_sequences WHERE increment_by != 1")).getCollection(String.class);
        if (!sequencesNonOneIncrement.isEmpty())
        {
            throw new IllegalStateException(StringUtilsLabKey.pluralize(sequencesNonOneIncrement.size(), "sequence has", "sequences have") + " an increment other than 1: " + sequencesNonOneIncrement);
        }

        // Select the SQL Server sequences with non-null last value. We'll use the results to set PostgreSQL sequences after copying data.
        String sequenceQuery = """
            SELECT
                OBJECT_SCHEMA_NAME(tables.object_id, db_id()) AS SchemaName,
                tables.name AS TableName,
                identity_columns.name AS ColumnName,
                identity_columns.seed_value,
                identity_columns.increment_value,
                identity_columns.last_value
            FROM
                sys.tables tables
            JOIN
                sys.identity_columns identity_columns ON tables.object_id = identity_columns.object_id
            WHERE last_value IS NOT NULL""";
        Map<String, Map<String, Sequence>> sequenceMap = new HashMap<>();
        new SqlSelector(sourceScope, sequenceQuery).forEach(rs -> {
            Sequence sequence = new Sequence(rs.getString("SchemaName"), rs.getString("TableName"), rs.getString("ColumnName"), rs.getInt("last_value"));
            Map<String, Sequence> schemaMap = sequenceMap.computeIfAbsent(sequence.schemaName(), s -> new HashMap<>());
            schemaMap.put(sequence.tableName(), sequence);
        });

        // Get the target module schemas in module order, which helps with foreign key relationships
        List<DbSchema> targetModuleSchemas = ModuleLoader.getInstance().getModules().stream()
            .flatMap(module -> module.getSchemaNames().stream().filter(name -> !module.getProvisionedSchemaNames().contains(name)))
            .map(name -> targetScope.getSchema(name, DbSchemaType.Module))
            .toList();

        // Migrate all data in the module schemas
        migrateSchemas(migrationDataSource, sourceScope, targetScope, targetModuleSchemas, (schema, handler) -> handler.getTablesToCopy(schema), sequenceMap);

        // Create all provisioned tables listed in exp.DomainDescriptor
        PropertyService svc = PropertyService.get();
        StorageProvisioner provisioner = StorageProvisioner.get();
        new SqlSelector(targetScope, "SELECT Container, DomainURI, Name FROM exp.DomainDescriptor WHERE StorageSchemaName IS NOT NULL").forEach(rs -> {
            Container c = ContainerManager.getForId(rs.getString("Container"));
            if (c != null)
            {
                String domainURI = rs.getString("DomainURI");
                String name = rs.getString("Name");
                Domain d = svc.ensureDomain(c, null, domainURI, name);
                provisioner.createStorageTable(d, d.getDomainKind(), targetScope);
            }
        });

        // Get the target provisioned schemas
        List<DbSchema> targetProvisionedSchemas = ModuleLoader.getInstance().getModules().stream()
            .flatMap(module -> module.getSchemaNames().stream().filter(name -> module.getProvisionedSchemaNames().contains(name)))
            .map(name -> targetScope.getSchema(name, DbSchemaType.Bare))
            .toList();

        // Migrate all data in the provisioned schemas
        migrateSchemas(migrationDataSource, sourceScope, targetScope, targetProvisionedSchemas, (schema, handler) -> getTables(schema), sequenceMap);

        LOG.info("Database migration is complete");
    }

    private static void migrateSchemas(String migrationDataSource, DbScope sourceScope, DbScope targetScope, List<DbSchema> targetSchemas, BiFunction<DbSchema, MigrationHandler, List<TableInfo>> tableProducer, Map<String, Map<String, Sequence>> sequenceMap)
    {
        for (DbSchema targetSchema : targetSchemas)
        {
            DbSchema sourceSchema = sourceScope.getSchema(targetSchema.getName(), DbSchemaType.Bare);
            if (!sourceSchema.existsInDatabase())
            {
                LOG.warn("{} has no schema named '{}'", migrationDataSource, targetSchema.getName());
            }
            else
            {
                MigrationHandler handler = getHandler(targetSchema);
                handler.beforeSchema(targetSchema);

                Set<String> sourceTableNames = getTables(sourceSchema).stream().map(TableInfo::getName).collect(LabKeyCollectors.toCaseInsensitiveHashSet());
                Set<String> targetTableNames = getTables(targetSchema).stream().map(TableInfo::getName).collect(LabKeyCollectors.toCaseInsensitiveHashSet());
                Set<String> sourceTableNamesCopy = new CaseInsensitiveHashSet(sourceTableNames);
                sourceTableNames.removeAll(targetTableNames);
                targetTableNames.removeAll(sourceTableNamesCopy);
                if (!sourceTableNames.isEmpty() || !targetTableNames.isEmpty())
                    LOG.warn("Table differences in {} schema: {} and {}", sourceSchema.getName(), sourceTableNames, targetTableNames);

                Map<String, Sequence> schemaSequenceMap = sequenceMap.getOrDefault(sourceSchema.getName(), Map.of());

                for (TableInfo targetTable : tableProducer.apply(targetSchema, handler))
                {
                    String targetTableName = targetTable.getName();
                    SchemaTableInfo sourceTable = sourceSchema.getTable(targetTableName);

                    if (sourceTable == null)
                    {
                        LOG.warn("Source schema has no table named '{}'", targetTableName);
                    }
                    else
                    {
                        // Inspect target table to determine column names to select from source table
                        Set<String> selectColumnNames = targetTable.getColumns().stream()
                            .filter(column -> column.getWrappedColumnName() == null) // Ignore wrapped columns
                            .map(ColumnInfo::getName)
                            .filter(name -> !name.equals("_ts"))
                            .collect(Collectors.toSet());

                        TableSelector sourceSelector = new TableSelector(sourceTable, selectColumnNames).setJdbcCaching(false);

                        try (Stream<Map<String, Object>> mapStream = sourceSelector.uncachedMapStream(); Connection conn = targetScope.getConnection())
                        {
                            Collection<ColumnInfo> sourceColumns = sourceSelector.getSelectedColumns();
                            // Map the selected source columns to the target columns so we get the right order and casing for INSERT, etc.
                            Collection<ColumnInfo> targetColumns = sourceColumns.stream()
                                .map(sourceCol -> targetTable.getColumn(sourceCol.getName()))
                                .toList();
                            String q = StringUtils.join(Collections.nCopies(sourceColumns.size(), "?"), ", ");
                            SQLFragment sql = new SQLFragment("INSERT INTO ")
                                .append(targetTable)
                                .append("(");

                            String sep = "";
                            for (ColumnInfo targetColumn : targetColumns)
                            {
                                sql.append(sep)
                                    .appendIdentifier(targetColumn.getSelectIdentifier());
                                sep = ", ";
                            }

                            sql.append(") VALUES (")
                                .append(q)
                                .append(")");

                            PreparedStatement statement = conn.prepareStatement(sql.getRawSQL());

                            mapStream.forEach(map -> {
                                try
                                {
                                    int i = 1;

                                    for (ColumnInfo col : sourceColumns)
                                        statement.setObject(i++, col.getValue(map));

                                    statement.execute();
                                }
                                catch (SQLException e)
                                {
                                    throw new RuntimeException("Exception while migrating data from " + sourceTable, e);
                                }
                            });

                            Sequence sequence = schemaSequenceMap.get(targetTable.getName());
                            if (sequence != null)
                            {
                                ColumnInfo targetColumn = targetTable.getColumn(sequence.columnName());
                                String sequenceName = new SqlSelector(targetSchema, "SELECT pg_get_serial_sequence(?, ?)", targetSchema.getName() + "." + targetTable.getName(), targetColumn.getSelectIdentifier().getId())
                                    .getObject(String.class);
                                new SqlExecutor(targetScope).execute("SELECT setval(?, ?)", sequenceName, sequence.lastValue());
                            }
                        }
                        catch (Exception e)
                        {
                            LOG.error("Exception: ", e);
                        }
                    }
                }

                handler.afterSchema(targetSchema);
            }
        }
    }

    private static List<TableInfo> getTables(DbSchema schema)
    {
        return new ArrayList<>(schema.getTableNames().stream()
            .map(schema::getTable)
            .filter(table -> table.getTableType() == DatabaseTableType.TABLE)
            .toList());
    }

    public interface MigrationHandler
    {
        void beforeSchema(DbSchema targetSchema);

        List<TableInfo> getTablesToCopy(DbSchema targetSchema);

        void afterSchema(DbSchema targetSchema);
    }

    public static class DefaultMigrationHandler implements MigrationHandler
    {
        @Override
        public void beforeSchema(DbSchema targetSchema)
        {
        }

        @Override
        public List<TableInfo> getTablesToCopy(DbSchema targetSchema)
        {
            Set<TableInfo> sortedTables = new LinkedHashSet<>(TableSorter.sort(targetSchema, true));

            Set<TableInfo> allTables = targetSchema.getTableNames().stream()
                .map(targetSchema::getTable)
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
        public void afterSchema(DbSchema targetSchema)
        {
        }
    }

    private static final Map<String, MigrationHandler> MIGRATION_HANDLERS = new CopyOnWriteCaseInsensitiveHashMap<>();
    private static final MigrationHandler DEFAULT_MIGRATION_HANDLER = new DefaultMigrationHandler();

    public static void registerHandler(DbSchema schema, MigrationHandler handler)
    {
        MIGRATION_HANDLERS.put(schema.getName(), handler);
    }

    private static MigrationHandler getHandler(DbSchema schema)
    {
        MigrationHandler handler = MIGRATION_HANDLERS.get(schema.getName());
        return handler != null ? handler : DEFAULT_MIGRATION_HANDLER;
    }
}
