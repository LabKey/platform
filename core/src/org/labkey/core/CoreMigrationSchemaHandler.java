package org.labkey.core;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.attachments.AttachmentCache;
import org.labkey.api.attachments.AttachmentParentType;
import org.labkey.api.attachments.LookAndFeelResourceType;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.CompareType.CompareClause;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.PropertySchema;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter.AndClause;
import org.labkey.api.data.SimpleFilter.FilterClause;
import org.labkey.api.data.SimpleFilter.OrClause;
import org.labkey.api.data.SimpleFilter.SQLClause;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TestSchema;
import org.labkey.api.data.WrappedColumn;
import org.labkey.api.files.FileSystemAttachmentType;
import org.labkey.api.migration.DatabaseMigrationConfiguration;
import org.labkey.api.migration.DatabaseMigrationService;
import org.labkey.api.migration.DefaultMigrationSchemaHandler;
import org.labkey.api.migration.MigrationFilter;
import org.labkey.api.migration.MigrationTableHandler;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.query.FieldKey;
import org.labkey.api.reports.report.ReportType;
import org.labkey.api.security.AuthenticationLogoType;
import org.labkey.api.security.AvatarType;
import org.labkey.api.util.ConfigurationException;
import org.labkey.api.util.GUID;
import org.labkey.api.view.Portal;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

class CoreMigrationSchemaHandler extends DefaultMigrationSchemaHandler implements MigrationFilter
{
    static void register()
    {
        CoreMigrationSchemaHandler schemaHandler = new CoreMigrationSchemaHandler();
        DatabaseMigrationService.get().registerSchemaHandler(schemaHandler);
        DatabaseMigrationService.get().registerMigrationFilter(schemaHandler);
        DatabaseMigrationService.get().registerTableHandler(new MigrationTableHandler()
        {
            @Override
            public TableInfo getTableInfo()
            {
                return Portal.getTableInfoPortalWebParts();
            }

            @Override
            public ColumnInfo handleColumn(ColumnInfo col)
            {
                return "Properties".equals(col.getName()) ? new GuidReplacingColumn(col) : col;
            }
        });

        DatabaseMigrationService.get().registerSchemaHandler(new DefaultMigrationSchemaHandler(PropertySchema.getInstance().getSchema()){
            @Override
            public @Nullable FieldKey getContainerFieldKey(TableInfo sourceTable)
            {
                return sourceTable.getName().equals("PropertySets") ? FieldKey.fromParts("ObjectId") : super.getContainerFieldKey(sourceTable);
            }
        });

        DatabaseMigrationService.get().registerSchemaHandler(new DefaultMigrationSchemaHandler(TestSchema.getInstance().getSchema()){
            @Override
            public List<TableInfo> getTablesToCopy()
            {
                return List.of(); // Skip all test tables
            }
        });

        if (ModuleLoader.getInstance().getModule(DbScope.getLabKeyScope(), "vehicle") != null)
        {
            DatabaseMigrationService.get().registerSchemaHandler(new DefaultMigrationSchemaHandler(DbSchema.get("vehicle", DbSchemaType.Module))
            {
                @Override
                public List<TableInfo> getTablesToCopy()
                {
                    return List.of(); // Skip all vehicle tables
                }
            });
        }
    }

    private CoreMigrationSchemaHandler()
    {
        super(CoreSchema.getInstance().getSchema());
    }

    @Override
    public void beforeVerification()
    {
        super.beforeVerification();

        // Delete root and shared containers that were needed for bootstrapping
        Table.delete(CoreSchema.getInstance().getTableInfoContainers());
        DbScope targetScope = DbScope.getLabKeyScope();
        new SqlExecutor(targetScope).execute("ALTER SEQUENCE core.containers_rowid_seq RESTART"); // Reset Containers sequence

        // Delete Guests and Users groups that were needed for bootstrapping
        Table.delete(CoreSchema.getInstance().getTableInfoPrincipals());
    }

    @Override
    public void beforeSchema()
    {
        new SqlExecutor(getSchema()).execute("ALTER TABLE core.Containers DROP CONSTRAINT FK_Containers_Containers");
        new SqlExecutor(getSchema()).execute("ALTER TABLE core.ViewCategory DROP CONSTRAINT FK_ViewCategory_Parent");
    }

    @Override
    public List<TableInfo> getTablesToCopy()
    {
        List<TableInfo> tablesToCopy = super.getTablesToCopy();
        tablesToCopy.remove(CoreSchema.getInstance().getTableInfoModules());
        tablesToCopy.remove(CoreSchema.getInstance().getTableInfoSqlScripts());
        tablesToCopy.remove(CoreSchema.getInstance().getTableInfoUpgradeSteps());
        tablesToCopy.remove(CoreSchema.getInstance().getTableInfoDocuments());

        return tablesToCopy;
    }

    @Override
    public @Nullable FieldKey getContainerFieldKey(TableInfo sourceTable)
    {
        return switch (sourceTable.getName())
        {
            case "ContainerAliases" -> FieldKey.fromParts("ContainerRowId", "EntityId");
            case "Containers" -> FieldKey.fromParts("EntityId");
            case "Report" -> FieldKey.fromParts("ContainerId");
            // Note: DataStates is not really site-wide, but there seem to be exp.Materials referencing DataStates with conflicting containers
            case "APIKeys", "AuthenticationConfigurations", "DataStates", "EmailOptions", "Logins", "ReportEngines",
                 "ShortURL", "UsersData" -> SITE_WIDE_TABLE;
            default -> super.getContainerFieldKey(sourceTable);
        };
    }

    @Override
    public FilterClause getTableFilterClause(TableInfo sourceTable, Set<GUID> containers)
    {
        FilterClause filterClause = getContainerClause(sourceTable, containers);
        String tableName = sourceTable.getName();

        if ("Principals".equals(tableName) || "Members".equals(tableName))
        {
            if (_groupFilterCondition != null)
            {
                SQLFragment groupFilterFragment = new SQLFragment();

                if ("Principals".equals(tableName))
                {
                    groupFilterFragment
                        .append("Type <> 'g' OR (type = 'g' AND UserId ")
                        .append(_groupFilterCondition)
                        .append(")");
                }
                else
                {
                    groupFilterFragment
                        .append("GroupId ")
                        .append(_groupFilterCondition);
                }

                filterClause = new AndClause(filterClause, new SQLClause(groupFilterFragment));
            }
        }

        if ("RoleAssignments".equals(tableName) && _groupFilterCondition != null)
        {
            SQLFragment groupFilterFragment = new SQLFragment("UserId IN (SELECT UserId FROM core.Principals WHERE Type <> 'g' OR (type = 'g' AND UserId ")
                .append(_groupFilterCondition)
                .append("))");
            filterClause = new AndClause(filterClause, new SQLClause(groupFilterFragment));
        }

        return filterClause;
    }

    @Override
    public FilterClause getContainerClause(TableInfo sourceTable, Set<GUID> containers)
    {
        FilterClause containerClause = super.getContainerClause(sourceTable, containers);
        String tableName = sourceTable.getName();

        if ("Principals".equals(tableName) || "Members".equals(tableName))
        {
            // Users and root groups have container == null, so add that as an OR clause
            OrClause orClause = new OrClause();
            orClause.addClause(containerClause);
            orClause.addClause(new CompareClause(getContainerFieldKey(sourceTable), CompareType.ISBLANK, null));
            containerClause = orClause;
        }

        return containerClause;
    }

    @Override
    public void afterSchema(DatabaseMigrationConfiguration configuration, DbSchema sourceSchema, DbSchema targetSchema)
    {
        new SqlExecutor(getSchema()).execute("ALTER TABLE core.Containers ADD CONSTRAINT FK_Containers_Containers FOREIGN KEY (Parent) REFERENCES core.Containers(EntityId)");
        new SqlExecutor(getSchema()).execute("ALTER TABLE core.ViewCategory ADD CONSTRAINT FK_ViewCategory_Parent FOREIGN KEY (Parent) REFERENCES core.ViewCategory(RowId)");
    }

    @Override
    public Collection<AttachmentParentType> copyAttachments(DatabaseMigrationConfiguration configuration, DbSchema sourceSchema, DbSchema targetSchema, @Nullable Set<GUID> copyContainers)
    {
        // Default handling for core's standard attachment parent types
        Collection<AttachmentParentType> ret = new LinkedList<>(super.copyAttachments(configuration, sourceSchema, targetSchema, copyContainers));

        // Special handling for LookAndFeelResourceType, which must select from the source database. Keep in sync with
        // LookAndFeelResourceType.addWhereSql().
        SQLFragment sql = new SQLFragment("Parent");

        if (copyContainers != null)
        {
            // Subset of containers
            sql.appendInClause(copyContainers, sourceSchema.getSqlDialect());
        }
        else
        {
            // All containers
            sql.append(" IN (SELECT EntityId FROM core.Containers)");
        }

        sql.append(" AND (DocumentName IN (?, ?) OR ")
            .add(AttachmentCache.FAVICON_FILE_NAME)
            .add(AttachmentCache.STYLESHEET_FILE_NAME)
            .append("DocumentName LIKE '" + AttachmentCache.LOGO_FILE_NAME_PREFIX + "%' OR ")
            .append("DocumentName LIKE '" + AttachmentCache.MOBILE_LOGO_FILE_NAME_PREFIX + "%')");
        ret.addAll(copyAttachments(configuration, new SQLClause(sql), LookAndFeelResourceType.get()));

        return ret;
    }

    @Override
    public @NotNull Collection<AttachmentParentType> getAttachmentTypes()
    {
        return List.of(
            AuthenticationLogoType.get(),
            AvatarType.get(),
            FileSystemAttachmentType.get(),
            ReportType.get()
        );
    }

    @Override
    public void afterMigration(DatabaseMigrationConfiguration configuration)
    {
        // Now that all schemas have copied their attachments into core.Documents, update that table's sequence
        DatabaseMigrationService.get().updateSequences(configuration.getSourceScope().getSchema("core", DbSchemaType.Migration).getTable("Documents"), CoreSchema.getInstance().getTableInfoDocuments());
    }

    // MigrationFilter implementation below

    private SQLFragment _groupFilterCondition = null;

    @Override
    public String getName()
    {
        return "GroupFilter";
    }

    @Override
    public void saveFilter(@Nullable GUID guid, String groupFilter)
    {
        if (guid != null)
            throw new ConfigurationException("GroupFilter is applied globally; you cannot specify a GUID");

        _groupFilterCondition = new SQLFragment(groupFilter);
    }

    // Replace the first contained GUID in each value with a lowercase version
    private static final class GuidReplacingColumn extends WrappedColumn
    {
        public GuidReplacingColumn(ColumnInfo col)
        {
            super(col, col.getName());
        }

        @Override
        public SQLFragment getValueSql(String tableAlias)
        {
            SQLFragment columnAlias = super.getValueSql(tableAlias);

            //noinspection StringConcatenationInsideStringBufferAppend - SQLFragment flips out about unmatched quotes, so we're forced to use string concatenation
            return new SQLFragment("(SELECT CASE WHEN idx > 0 THEN SUBSTRING(Properties, 1, idx - 1) + LOWER(SUBSTRING(Properties, idx, 36)) + SUBSTRING(Properties, idx + 36, LEN(Properties) - idx - 35) ELSE Properties END")
                .append(" FROM (SELECT CAST(")
                .append(columnAlias)
                .append(" AS VARCHAR(MAX)) AS Properties, PATINDEX('%" + GUID.SQL_LIKE_GUID_PATTERN + "%', ")
                .append(columnAlias)
                .append(") AS idx) AS Properties)");
        }
    }
}
