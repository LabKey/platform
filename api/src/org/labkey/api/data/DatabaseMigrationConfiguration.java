package org.labkey.api.data;

import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

public interface DatabaseMigrationConfiguration
{
    boolean shouldInsertData();
    DbScope getSourceScope();
    DbScope getTargetScope();
    DbScope getMetadataScope();
    Predicate<String> getColumnNameFilter();

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
        public DbScope getMetadataScope()
        {
            return null;
        }

        @Override
        public Predicate<String> getColumnNameFilter()
        {
            return null;
        }
    }
}
