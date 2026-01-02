package org.labkey.api.migration;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.util.GUID;
import org.labkey.api.util.Pair;

import java.util.Set;

public interface DatabaseMigrationConfiguration
{
    boolean shouldInsertData();
    default void beforeMigration(){}
    DbScope getSourceScope();
    DbScope getTargetScope();
    @NotNull Set<String> getSkipSchemas();
    @Nullable TableSelector getTableSelector(DbSchemaType schemaType, TableInfo sourceTable, TableInfo targetTable, Set<String> selectColumnNames, MigrationSchemaHandler schemaHandler, @Nullable MigrationTableHandler tableHandler);
    default void copySchemaAttachments(DbSchema sourceSchema, DbSchema targetSchema, MigrationSchemaHandler schemaHandler){}
    default void afterMigration(){}
    default @Nullable Pair<FilePathWriter, Set<GUID>> initializeFilePathWriter()
    {
        return null;
    }
}
