package org.labkey.api.migration;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.SimpleFilter.FilterClause;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.FieldKey;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.ConfigurationException;
import org.labkey.api.util.GUID;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.vfs.FileLike;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

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

    // Register a contributor that runs during migration before a schema's tables are processed.
    // Useful for modules that need to register table handlers for a schema owned by another module.
    default void registerSchemaContributor(String schemaName, Consumer<DbSchema> contributor) {}

    default @Nullable MigrationFilter getMigrationFilter(String propertyName)
    {
        return null;
    }

    default void copySourceTableToTargetTable(DatabaseMigrationConfiguration configuration, TableInfo sourceTable, TableInfo targetTable, DbSchemaType schemaType, boolean updateSequences, @Nullable String additionalLogMessage, MigrationSchemaHandler schemaHandler) {}
    default void copyAttachments(DatabaseMigrationConfiguration configuration, @Nullable FilterClause filterClause, @Nullable String additionalLogMessage) {}
    default void updateSequences(TableInfo sourceTable, TableInfo targetTable) {}

    // Helper method that parses a data filter then adds it and its container to the provided collections, coalescing
    // cases where multiple containers specify the same filter
    static void addDataFilter(String filterName, List<DataFilter> dataFilters, Set<GUID> filteredContainers, @NotNull GUID guid, String filter)
    {
        String[] filterParts = filter.split("=");
        if (filterParts.length != 2)
            throw new ConfigurationException("Bad " + filterName + " value; expected <columnName>=<value>: " + filter);

        if (!filteredContainers.add(guid))
            throw new ConfigurationException("Duplicate " + filterName + " entry for container " + guid);

        String column = filterParts[0];
        String value = filterParts[1];
        FilterClause clause = CompareType.EQUAL.createFilterClause(new FieldKey(null, column), value);
        // If another container is already using this filter clause, then simply add this guid to that filter.
        // Otherwise, add a new data filter to the list.
        dataFilters.stream()
            .filter(df -> df.column().equals(column) && df.condition().equals(clause))
            .findFirst()
            .ifPresentOrElse(df -> df.containers().add(guid), () -> dataFilters.add(new DataFilter(new HashSet<>(Set.of(guid)), filterParts[0], clause)));
    }
}
