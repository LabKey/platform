package org.labkey.api.migration;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.attachments.AttachmentParentType;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SimpleFilter.FilterClause;
import org.labkey.api.data.SimpleFilter.OrClause;
import org.labkey.api.data.TableInfo;
import org.labkey.api.migration.DatabaseMigrationService.DataFilter;
import org.labkey.api.query.FieldKey;
import org.labkey.api.util.GUID;

import java.io.PrintWriter;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface MigrationSchemaHandler
{
    // Marker for tables to declare themselves as site-wide (no container filtering)
    FieldKey SITE_WIDE_TABLE = FieldKey.fromParts("site-wide");

    DbSchema getSchema();

    void beforeVerification();

    void beforeSchema();

    List<TableInfo> getTablesToCopy();

    // Create a filter clause that selects from all specified containers and (in some overrides) applies table-specific filters
    FilterClause getTableFilterClause(TableInfo sourceTable, Set<GUID> containers);

    // Create a filter clause that selects from all specified containers
    FilterClause getContainerClause(TableInfo sourceTable, Set<GUID> containers);

    // Return the FieldKey that can be used to filter this table by container. Special values SITE_WIDE_TABLE and
    // DUMMY_FIELD_KEY can be returned for special behaviors. DUMMY_FIELD_KEY ensures that the handler's custom
    // getContainerClause() is always called. SITE_WIDE_TABLE is used to select all rows.
    @Nullable FieldKey getContainerFieldKey(TableInfo sourceTable);

    // Create a filter clause that selects all rows from unfiltered containers plus filtered rows from the filtered containers
    FilterClause getDomainDataFilterClause(Set<GUID> copyContainers, Set<GUID> filteredContainers, List<DataFilter> domainFilters, TableInfo sourceTable, Set<String> selectColumnNames);

    void addDomainDataFilterClause(OrClause orClause, DataFilter filter, TableInfo sourceTable, Set<String> selectColumnNames);

    // Do any necessary clean up after the target table has been populated. notCopiedFilter selects all rows in the
    // source table that were NOT copied to the target table. (For example, rows in a global table not copied due to
    // container filtering or rows in a provisioned table not copied due to domain data filtering.)
    void afterTable(TableInfo sourceTable, TableInfo targetTable, SimpleFilter notCopiedFilter);

    void afterSchema(DatabaseMigrationConfiguration configuration, DbSchema sourceSchema, DbSchema targetSchema);

    void copyAttachments(DatabaseMigrationConfiguration configuration, DbSchema sourceSchema, DbSchema targetSchema, Set<GUID> copyContainers);

    @NotNull Collection<AttachmentParentType> getAttachmentTypes();

    void afterMigration(DatabaseMigrationConfiguration configuration);

    void writeFilePaths(FilePathWriter writer, Set<GUID> guids);
}
