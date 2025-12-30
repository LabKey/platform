package org.labkey.api.migration;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;

import java.util.Set;

public class DefaultDatabaseMigrationConfiguration implements DatabaseMigrationConfiguration
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
    public TableSelector getTableSelector(DbSchemaType schemaType, TableInfo sourceTable, TableInfo targetTable, Set<String> selectColumnNames, MigrationSchemaHandler schemaHandler, @Nullable MigrationTableHandler tableHandler)
    {
        return null;
    }
}
