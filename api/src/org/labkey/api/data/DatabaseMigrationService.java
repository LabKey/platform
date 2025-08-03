package org.labkey.api.data;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.query.TableSorter;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.logging.LogHelper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
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

    // By default, no-op implementation that simply logs
    default void migrate(boolean shouldInsertData, @Nullable String migrationDataSource)
    {
        LOG.warn("Database migration service is not present; database migration is a premium feature.");
    }

    // By default, no-op implementation
    default void registerHandler(DbSchema schema, MigrationHandler handler) {}

    interface MigrationHandler
    {
        void beforeSchema(DbSchema targetSchema);

        List<TableInfo> getTablesToCopy(DbSchema targetSchema);

        void afterSchema(DbSchema targetSchema);
    }

    class DefaultMigrationHandler implements MigrationHandler
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
}
