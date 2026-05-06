package org.labkey.core.query;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.DefaultQueryUpdateService;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.query.column.BuiltInColumnTypes;
import org.labkey.api.reports.ReportService;
import org.labkey.api.reports.report.QueryReport;
import org.labkey.api.reports.report.ReportDescriptor;
import org.labkey.api.reports.report.ReportUrls;
import org.labkey.api.security.User;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.ContainerContext;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.TestContext;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.writer.ContainerUser;

import java.util.List;
import java.util.Map;

import static org.labkey.api.util.JunitUtil.deleteTestContainer;

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

    public static class TestCase extends Assert
    {
        private static final Logger LOG = LogHelper.getLogger(ReportsTable.class, "Integration tests for the ReportsTable");
        private static User _user;
        private static Container _container;

        @BeforeClass
        public static void setup() throws Exception
        {
            _container = JunitUtil.getTestContainer();
            _user = TestContext.get().getUser();
        }

        @AfterClass
        public static void cleanup()
        {
            deleteTestContainer();
            _container = null;
            _user = null;
        }

        @Test
        public void testReportsTableAdminOnlyAccess()
        {
            LOG.info("Validate Core.Reports is admin only");

            var schema = QueryService.get().getUserSchema(User.getAdminServiceUser(), _container, CoreQuerySchema.NAME);
            assertNotNull("Expected admin access to the " + CoreQuerySchema.REPORTS_TABLE_NAME + " table", schema.getTable(CoreQuerySchema.REPORTS_TABLE_NAME));

            schema = QueryService.get().getUserSchema(User.getSearchUser(), _container, CoreQuerySchema.NAME);
            assertNull("Expected admin access to the " + CoreQuerySchema.REPORTS_TABLE_NAME + " table", schema.getTable(CoreQuerySchema.REPORTS_TABLE_NAME));
        }

        private QueryUpdateService ensureUpdateService(String tableName)
        {
            var schema = QueryService.get().getUserSchema(_user, _container, CoreQuerySchema.NAME);
            var table = schema.getTable(tableName);
            var qus = table.getUpdateService();
            assertNotNull("Expected update service for " + tableName, qus);

            return qus;
        }

        @Test
        public void testReportsApiAccess() throws Exception
        {
            var qus = ensureUpdateService(CoreQuerySchema.REPORTS_TABLE_NAME);

            try
            {
                BatchValidationException errors = new BatchValidationException();
                Map<String, Object> row = CaseInsensitiveHashMap.of(
                        "reportKey", "foo/bar",
                        "hidden", true
                );
                qus.insertRows(_user, _container, List.of(row), errors, null, null);
                assertFalse("Insert should not be allowed", errors.hasErrors());
            }
            catch (UnauthorizedException e)
            {
                // expected
            }

            // Save a report through the service
            var queryReport = ReportService.get().createReportInstance(QueryReport.TYPE);
            var descriptor = queryReport.getDescriptor();
            descriptor.setReportName("custom query report");

            var identifier = ReportService.get().saveReportEx(ContainerUser.create(_container, _user), "reportKey", queryReport, true);
            assertTrue("Unable to save a query report", identifier.getRowId() != 0);

            var savedReport = identifier.getReport(ContainerUser.create(_container, _user));
            assertNotNull("Unable to retrieve a saved report", savedReport);

            BatchValidationException errors = new BatchValidationException();
            Map<String, Object> row = CaseInsensitiveHashMap.of(
                    "rowId", savedReport.getDescriptor().getReportId().getRowId(),
                    "flags", 2
            );

            try
            {
                qus.updateRows(_user, _container, List.of(row), null, errors, null, null);
                assertFalse("Update should not be allowed", errors.hasErrors());
            }
            catch (UnauthorizedException e)
            {
                // expected, delete the query
                qus.deleteRows(_user, _container, List.of(row), null, null);
            }
        }
    }
}
