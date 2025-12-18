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

import java.io.PrintWriter;
import java.util.Set;
import java.util.function.Predicate;

public interface DatabaseMigrationConfiguration
{
    boolean shouldInsertData();
    default void beforeMigration(){}
    DbScope getSourceScope();
    DbScope getTargetScope();
    @NotNull Set<String> getSkipSchemas();
    Predicate<String> getColumnNameFilter();
    @Nullable TableSelector getTableSelector(DbSchemaType schemaType, TableInfo sourceTable, TableInfo targetTable, Set<String> selectColumnNames, MigrationSchemaHandler schemaHandler, @Nullable MigrationTableHandler tableHandler);
    default void copyAttachments(DbSchema sourceSchema, DbSchema targetSchema, MigrationSchemaHandler schemaHandler){}
    default @Nullable Pair<FilePathWriter, Set<GUID>> initializeFilePathWriter()
    {
        return null;
    }
}
