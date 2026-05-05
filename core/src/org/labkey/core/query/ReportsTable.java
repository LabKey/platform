package org.labkey.core.query;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.DefaultQueryUpdateService;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.column.BuiltInColumnTypes;
import org.labkey.api.reports.ReportService;
import org.labkey.api.reports.report.ReportDescriptor;
import org.labkey.api.reports.report.ReportUrls;
import org.labkey.api.security.User;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.ContainerContext;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.writer.ContainerUser;

import java.util.List;
import java.util.Map;

public class ReportsTable extends FilteredTable<CoreQuerySchema>
{
    public ReportsTable(@NotNull CoreQuerySchema userSchema, ContainerFilter cf)
    {
        super(CoreSchema.getInstance().getTableInfoReport(), userSchema, cf);

        setName(CoreQuerySchema.REPORTS_TABLE_NAME);
        setDescription("Contains a row for each report in the database. Available only to administrators.");

        ReportUrls reportUrls = PageFlowUtil.urlProvider(ReportUrls.class);
        ActionURL baseUrl = reportUrls.urlReportDetails(userSchema.getContainer(), null);
        DetailsURL detailsURL = new DetailsURL(baseUrl, Map.of(ReportDescriptor.Prop.reportId.toString(), FieldKey.fromParts("RowId")));
        detailsURL.setContainerContext(new ContainerContext.FieldKeyContext(FieldKey.fromParts("ContainerId")));
        setDetailsURL(detailsURL);

        wrapAllColumns(true);

        var folderCol = getMutableColumnOrThrow(FieldKey.fromString("ContainerId"));
        folderCol.setLabel("Folder");
        folderCol.setConceptURI(BuiltInColumnTypes.CONTAINERID_CONCEPT_URI);

        getMutableColumnOrThrow("CreatedBy").setConceptURI(BuiltInColumnTypes.USERID_CONCEPT_URI);
        getMutableColumnOrThrow("ModifiedBy").setConceptURI(BuiltInColumnTypes.USERID_CONCEPT_URI);
        getMutableColumnOrThrow("ReportOwner").setConceptURI(BuiltInColumnTypes.USERID_CONCEPT_URI);

        ColumnInfo flagsCol = getRealTable().getColumn("Flags");
        var hiddenCol = new ExprColumn(this, "Hidden",
                new SQLFragment("(CASE WHEN (" + ExprColumn.STR_TABLE_ALIAS + ".Flags & " + ReportDescriptor.FLAG_HIDDEN + ") != 0")
                        .append(" THEN ").append(getSqlDialect().getBooleanTRUE())
                        .append(" ELSE ").append(getSqlDialect().getBooleanFALSE()).append(" END)"),
                JdbcType.BOOLEAN, flagsCol);
        addColumn(hiddenCol);

        var inheritableCol = new ExprColumn(this, "Inheritable",
                new SQLFragment("(CASE WHEN (" + ExprColumn.STR_TABLE_ALIAS + ".Flags & " + ReportDescriptor.FLAG_INHERITABLE + ") != 0")
                        .append(" THEN ").append(getSqlDialect().getBooleanTRUE())
                        .append(" ELSE ").append(getSqlDialect().getBooleanFALSE()).append(" END)"),
                JdbcType.BOOLEAN, flagsCol);
        addColumn(inheritableCol);

        setDefaultVisibleColumns(List.of(
                FieldKey.fromParts("ReportKey"),
                FieldKey.fromParts("ContainerId"),
                FieldKey.fromParts("Hidden"),
                FieldKey.fromParts("Inheritable"),
                FieldKey.fromParts("Created"),
                FieldKey.fromParts("CreatedBy"),
                FieldKey.fromParts("Modified"),
                FieldKey.fromParts("ModifiedBy"),
                FieldKey.fromParts("ReportOwner")
        ));
    }

    @Override
    protected String getContainerFilterColumn()
    {
        return "ContainerId";
    }

    @Override
    public boolean hasPermission(@NotNull UserPrincipal user, @NotNull Class<? extends Permission> perm)
    {
        return (perm.equals(ReadPermission.class) || perm.equals(DeletePermission.class)) && getContainer().hasPermission(user, AdminPermission.class);
    }

    @Override
    public QueryUpdateService getUpdateService()
    {
        return new ReportsUpdateService(this, CoreSchema.getInstance().getTableInfoReport());
    }

    protected static class ReportsUpdateService extends DefaultQueryUpdateService
    {
        public ReportsUpdateService(TableInfo queryTable, TableInfo dbTable)
        {
            super(queryTable, dbTable);
        }

        @Override
        protected Map<String, Object> deleteRow(User user, Container container, Map<String, Object> oldRowMap)
        {
            Integer id = (Integer) oldRowMap.get("rowId");
            if (id != null)
            {
                var r = ReportService.get().getReport(container, id);
                if (r != null)
                    ReportService.get().deleteReport(ContainerUser.create(container, user), r);
            }
            return oldRowMap;
        }
    }
}
