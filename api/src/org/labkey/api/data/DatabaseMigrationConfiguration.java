package org.labkey.api.data;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.DatabaseMigrationService.MigrationHandler;

import java.util.Set;
import java.util.function.Predicate;

public interface DatabaseMigrationConfiguration
{
    boolean shouldInsertData();
    DbScope getSourceScope();
    DbScope getTargetScope();
    @NotNull Set<String> getSkipSchemas();
    Predicate<String> getColumnNameFilter();
    @Nullable TableSelector getTableSelector(TableInfo sourceTable, Set<String> selectColumnNames, MigrationHandler handler);

    class DefaultDatabaseMigrationConfiguration implements DatabaseMigrationConfiguration
    {
        @Override
        public boolean shouldInsertData()
        {
            return true;
        }

        @Override
        public @Nullable DbScope getSourceScope()
        {
            return null;
        }

        @Override
        public DbScope getTargetScope()
        {
            return null;
        }

        @Override
        public @NotNull Set<String> getSkipSchemas()
        {
            return Set.of();
        }

        @Override
        public Predicate<String> getColumnNameFilter()
        {
            return null;
        }

        @Override
        public TableSelector getTableSelector(TableInfo sourceTable, Set<String> selectColumnNames, MigrationHandler handler)
        {
            return null;
        }
    }
}
