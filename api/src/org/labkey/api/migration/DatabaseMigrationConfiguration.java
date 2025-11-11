package org.labkey.api.migration;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;

import java.util.Set;
import java.util.function.Predicate;

public interface DatabaseMigrationConfiguration
{
    boolean shouldInsertData();
    default void beforeMigration(){};
    DbScope getSourceScope();
    DbScope getTargetScope();
    @NotNull Set<String> getSkipSchemas();
    Predicate<String> getColumnNameFilter();
    @Nullable TableSelector getTableSelector(DbSchemaType schemaType, TableInfo sourceTable, TableInfo targetTable, Set<String> selectColumnNames, MigrationSchemaHandler schemaHandler);
    default void afterMigration(){};

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
        public TableSelector getTableSelector(DbSchemaType schemaType, TableInfo sourceTable, TableInfo targetTable, Set<String> selectColumnNames, MigrationSchemaHandler schemaHandler)
        {
            return null;
        }
    }
}
