/*
 * Copyright (c) 2008-2019 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.labkey.mothership.query;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.AbstractTableInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerForeignKey;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.ForeignKey;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.issues.IssuesUrls;
import org.labkey.api.module.Module;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.QueryForeignKey;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.SimpleQueryUpdateService;
import org.labkey.api.query.SimpleUserSchema;
import org.labkey.api.query.UserIdQueryForeignKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.StringExpressionFactory;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.labkey.mothership.MothershipController;
import org.labkey.mothership.MothershipManager;
import org.labkey.mothership.StackTraceDisplayColumn;
import org.springframework.validation.BindException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.labkey.api.query.ExprColumn.STR_TABLE_ALIAS;

/**
 * User: jeckels
 * Date: Mar 28, 2007
 */
public class MothershipSchema extends UserSchema
{
    private static final String SCHEMA_NAME = "mothership";
    private static final String SCHEMA_DESCR = "Contains data about exceptions that have occurred on this server.";

    public static final String SERVER_INSTALLATION_TABLE_NAME = "ServerInstallation";
    public static final String SERVER_SESSION_TABLE_NAME = "ServerSession";
    public static final String EXCEPTION_REPORT_TABLE_NAME = "ExceptionReport";
    public static final String EXCEPTION_REPORT_WITH_STACK_TABLE_NAME = "ExceptionReportWithStack";
    public static final String EXCEPTION_STACK_TRACE_TABLE_NAME = "ExceptionStackTrace";
    public static final String SOFTWARE_RELEASE_TABLE_NAME = "SoftwareRelease";

    private static final Set<String> TABLE_NAMES = Collections.unmodifiableSet(new LinkedHashSet<>(
        Arrays.asList(
            SERVER_INSTALLATION_TABLE_NAME,
            SERVER_SESSION_TABLE_NAME,
            SOFTWARE_RELEASE_TABLE_NAME,
            EXCEPTION_REPORT_TABLE_NAME,
            EXCEPTION_STACK_TRACE_TABLE_NAME,
            EXCEPTION_REPORT_WITH_STACK_TABLE_NAME)
    ));

    public MothershipSchema(User user, Container container)
    {
        super(SCHEMA_NAME, SCHEMA_DESCR, user, container, MothershipManager.get().getSchema());
    }

    static public void register(Module module)
    {
        DefaultSchema.registerProvider(SCHEMA_NAME, new DefaultSchema.SchemaProvider(module)
        {
            @Override
            public QuerySchema createSchema(DefaultSchema schema, Module module)
            {
                return new MothershipSchema(schema.getUser(), schema.getContainer());
            }
        });
    }

    @Override
    public Set<String> getTableNames()
    {
        return TABLE_NAMES;
    }


    @Override
    public TableInfo createTable(String name, ContainerFilter cf)
    {
        // Be backwards compatible with the prior plural name for existing queries
        if (name.equalsIgnoreCase(SERVER_INSTALLATION_TABLE_NAME) || name.equalsIgnoreCase(SERVER_INSTALLATION_TABLE_NAME + "s"))
        {
            return createServerInstallationTable(cf);
        }
        else if (name.equalsIgnoreCase(SERVER_SESSION_TABLE_NAME) || name.equalsIgnoreCase(SERVER_SESSION_TABLE_NAME + "s"))
        {
            return createServerSessionTable(cf);
        }
        else if (name.equalsIgnoreCase(SOFTWARE_RELEASE_TABLE_NAME) || name.equalsIgnoreCase(SOFTWARE_RELEASE_TABLE_NAME + "s"))
        {
            return createSoftwareReleasesTable(cf);
        }
        else if (name.equalsIgnoreCase(EXCEPTION_STACK_TRACE_TABLE_NAME))
        {
            return createExceptionStackTraceTable(cf);
        }
        else if (name.equalsIgnoreCase(EXCEPTION_REPORT_TABLE_NAME))
        {
            return createExceptionReportTable(cf);
        }
        else if (name.equalsIgnoreCase(EXCEPTION_REPORT_WITH_STACK_TABLE_NAME))
        {
            return createExceptionReportTableWithStack(cf);
        }
        return null;
    }

    public FilteredTable<MothershipSchema> createSoftwareReleasesTable(ContainerFilter cf)
    {
        FilteredTable<MothershipSchema> result = new FilteredTable<>(MothershipManager.get().getTableInfoSoftwareRelease(), this, cf);
        result.wrapAllColumns(true);

        result.getMutableColumnOrThrow("Container").setFk(new ContainerForeignKey(this));

        SQLFragment descriptionSQL = new SQLFragment("CASE WHEN " +
                ExprColumn.STR_TABLE_ALIAS + ".VcsBranch IS NULL OR " + ExprColumn.STR_TABLE_ALIAS + ".BuildTime IS NULL THEN " +
                ExprColumn.STR_TABLE_ALIAS + ".BuildNumber ELSE ").
                append(result.getSqlDialect().concatenate(
                        ExprColumn.STR_TABLE_ALIAS + ".VcsBranch",
                        "', '",
                        "CAST(" + ExprColumn.STR_TABLE_ALIAS + ".BuildTime AS VARCHAR)",
                        "', '",
                        ExprColumn.STR_TABLE_ALIAS + ".BuildNumber")).append(" END");
        result.addColumn(new ExprColumn(result, "Description", descriptionSQL, JdbcType.VARCHAR));

        List<FieldKey> defaultCols = new ArrayList<>();
        defaultCols.add(FieldKey.fromParts("BuildNumber"));
        defaultCols.add(FieldKey.fromParts("VcsUrl"));
        defaultCols.add(FieldKey.fromParts("VcsRevision"));
        defaultCols.add(FieldKey.fromParts("VcsBranch"));
        defaultCols.add(FieldKey.fromParts("VcsTag"));
        defaultCols.add(FieldKey.fromParts("BuildTime"));
        defaultCols.add(FieldKey.fromParts("Description"));
        result.setDefaultVisibleColumns(defaultCols);

        result.setTitleColumn("Description");

        result.setDetailsURL(new DetailsURL(new ActionURL(MothershipController.ShowUpdateAction.class, getContainer()), Collections.singletonMap("softwareReleaseId", "SoftwareReleaseId")));

        return result;
    }

    public FilteredTable<MothershipSchema> createServerSessionTable(ContainerFilter cf)
    {
        FilteredTable<MothershipSchema> result = new FilteredTable<>(MothershipManager.get().getTableInfoServerSession(), this, cf);
        result.wrapAllColumns(true);
        result.setTitleColumn("RowId");

        result.getMutableColumnOrThrow("Container").setFk(new ContainerForeignKey(this));
        result.getMutableColumnOrThrow("SoftwareReleaseId").setFk(new QueryForeignKey.Builder(this, cf).table(SOFTWARE_RELEASE_TABLE_NAME));
        result.getMutableColumnOrThrow("OriginalServerSessionId").setFk(new QueryForeignKey.Builder(this, cf).table(SERVER_SESSION_TABLE_NAME));

        result.getMutableColumnOrThrow("ServerInstallationId").setFk(new QueryForeignKey.Builder(this, cf).table(SERVER_INSTALLATION_TABLE_NAME));
        result.getMutableColumnOrThrow("ServerInstallationId").setLabel("Server");

        var earliestCol = result.getColumn("EarliestKnownTime");
        var latestCol = result.getColumn("LastKnownTime");

        ExprColumn durationCol = new ExprColumn(result, "Duration", new SQLFragment(MothershipManager.get().getDialect().getDateDiff(Calendar.DATE, STR_TABLE_ALIAS + ".LastKnownTime", STR_TABLE_ALIAS + ".EarliestKnownTime")), JdbcType.INTEGER, earliestCol, latestCol);
        result.addColumn(durationCol);

        ExprColumn exceptionCountCol = new ExprColumn(result, "ExceptionCount", new SQLFragment("(SELECT COUNT(*) FROM " + MothershipManager.get().getTableInfoExceptionReport() + " WHERE ServerSessionId = " + STR_TABLE_ALIAS + ".ServerSessionId)"), JdbcType.INTEGER);
        exceptionCountCol.setFormat("#.#");
        result.addColumn(exceptionCountCol);

        List<FieldKey> defaultCols = new ArrayList<>();
        defaultCols.add(FieldKey.fromString("SoftwareReleaseId/Description"));
        defaultCols.add(FieldKey.fromString("Duration"));
        defaultCols.add(FieldKey.fromString("LastKnownTime"));
        defaultCols.add(FieldKey.fromString("DatabaseProductName"));
        defaultCols.add(FieldKey.fromString("RuntimeOS"));
        defaultCols.add(FieldKey.fromString("JavaVersion"));
        defaultCols.add(FieldKey.fromString("UserCount"));
        defaultCols.add(FieldKey.fromString("ActiveUserCount"));
        defaultCols.add(FieldKey.fromString("ContainerCount"));
        defaultCols.add(FieldKey.fromString("HeapSize"));
        defaultCols.add(FieldKey.fromString("ServletContainer"));
        result.setDefaultVisibleColumns(defaultCols);

        ActionURL base = new ActionURL(MothershipController.ShowServerSessionDetailAction.class, getContainer());
        result.setDetailsURL(new DetailsURL(base, Collections.singletonMap("serverSessionId", "ServerSessionId")));

        return result;
    }

    public TableInfo createServerInstallationTable(ContainerFilter cf)
    {
        FilteredTable<MothershipSchema> result = new MothershipTable(MothershipManager.get().getTableInfoServerInstallation(), this, cf);
        result.setInsertURL(AbstractTableInfo.LINK_DISABLER);
        result.setImportURL(AbstractTableInfo.LINK_DISABLER);
        result.wrapAllColumns(true);

        ActionURL url = new ActionURL(MothershipController.ShowInstallationDetailAction.class, getContainer());
        url.addParameter("serverInstallationId","${ServerInstallationId}");
        result.getMutableColumnOrThrow("ServerHostName").setURL(StringExpressionFactory.createURL(url));

        result.getMutableColumnOrThrow("Container").setFk(new ContainerForeignKey(this));

        result.setTitleColumn("ServerHostName");

        SQLFragment firstPingSQL = new SQLFragment("(SELECT MIN(EarliestKnownTime) FROM ");
        firstPingSQL.append(MothershipManager.get().getTableInfoServerSession(), "ss");
        firstPingSQL.append(" WHERE ss.ServerInstallationId = ");
        firstPingSQL.append(STR_TABLE_ALIAS);
        firstPingSQL.append(".ServerInstallationId)");
        result.addColumn(new ExprColumn(result, "FirstPing", firstPingSQL, JdbcType.TIMESTAMP));

        SQLFragment lastPing = new SQLFragment("(SELECT MAX(LastKnownTime) FROM ");
        lastPing.append(MothershipManager.get().getTableInfoServerSession(), "ss");
        lastPing.append(" WHERE ss.ServerInstallationId = ");
        lastPing.append(STR_TABLE_ALIAS);
        lastPing.append(".ServerInstallationId)");
        result.addColumn(new ExprColumn(result, "LastPing", lastPing, JdbcType.TIMESTAMP));

        SQLFragment exceptionCount = new SQLFragment("(SELECT COUNT(*) FROM ");
        exceptionCount.append(MothershipManager.get().getTableInfoServerSession(), "ss");
        exceptionCount.append(",");
        exceptionCount.append(MothershipManager.get().getTableInfoExceptionReport(), "er");
        exceptionCount.append(" WHERE ss.ServerInstallationId = ");
        exceptionCount.append(STR_TABLE_ALIAS);
        exceptionCount.append(".ServerInstallationId AND ss.serversessionid = er.serversessionid)");
        ExprColumn exceptionCountCol = new ExprColumn(result, "ExceptionCount", exceptionCount, JdbcType.INTEGER);
        exceptionCountCol.setFormat("#.#");
        result.addColumn(exceptionCountCol);

        SqlDialect dialect = MothershipManager.get().getSchema().getSqlDialect();

        SQLFragment daysActive = new SQLFragment("(SELECT ");
        daysActive.append(dialect.getDateDiff(Calendar.DATE, "MAX(LastKnownTime)", "MIN(EarliestKnownTime)"));
        daysActive.append(" FROM ");
        daysActive.append(MothershipManager.get().getTableInfoServerSession(), "ss");
        daysActive.append(" WHERE ss.ServerInstallationId = ");
        daysActive.append(STR_TABLE_ALIAS);
        daysActive.append(".ServerInstallationId)");
        ExprColumn daysActiveColumn = new ExprColumn(result, "DaysActive", daysActive, JdbcType.INTEGER);
        daysActiveColumn.setFormat("#.#");
        result.addColumn(daysActiveColumn);

        SQLFragment versionCount = new SQLFragment("(SELECT COUNT(DISTINCT(softwarereleaseid)) FROM ");
        versionCount.append(MothershipManager.get().getTableInfoServerSession(), "ss");
        versionCount.append(" WHERE ss.ServerInstallationId = ");
        versionCount.append(STR_TABLE_ALIAS);
        versionCount.append(".ServerInstallationId)");
        result.addColumn(new ExprColumn(result, "VersionCount", versionCount, JdbcType.INTEGER));

        SQLFragment currentVersion = new SQLFragment("(SELECT MAX(ServerSessionID) FROM ");
        currentVersion.append(MothershipManager.get().getTableInfoServerSession(), "ss");
        currentVersion.append(" WHERE ss.ServerInstallationId = ");
        currentVersion.append(STR_TABLE_ALIAS);
        currentVersion.append(".ServerInstallationId)");
        ExprColumn currentVersionColumn = new ExprColumn(result, "MostRecentSession", currentVersion, JdbcType.INTEGER);
        currentVersionColumn.setFk(new QueryForeignKey.Builder(this, null).schema(this).table(SERVER_SESSION_TABLE_NAME).build());
        result.addColumn(currentVersionColumn);

        List<FieldKey> defaultCols = new ArrayList<>();
        defaultCols.add(FieldKey.fromString("ServerHostName"));
        defaultCols.add(FieldKey.fromString("ServerIP"));
        defaultCols.add(FieldKey.fromString("Note"));
        defaultCols.add(FieldKey.fromString("DaysActive"));
        defaultCols.add(FieldKey.fromString("LastPing"));
        defaultCols.add(FieldKey.fromString("ExceptionCount"));
        defaultCols.add(FieldKey.fromString("VersionCount"));
        defaultCols.add(FieldKey.fromString("MostRecentSession/SoftwareReleaseId"));
        defaultCols.add(FieldKey.fromString("UsedInstaller"));
        result.setDefaultVisibleColumns(defaultCols);

        return result;
    }

    public FilteredTable<MothershipSchema> createExceptionStackTraceTable(ContainerFilter cf)
    {
        FilteredTable<MothershipSchema> result = new MothershipTable(MothershipManager.get().getTableInfoExceptionStackTrace(), this, cf);
        result.setUpdateURL(AbstractTableInfo.LINK_DISABLER);
        result.setInsertURL(AbstractTableInfo.LINK_DISABLER);
        result.setImportURL(AbstractTableInfo.LINK_DISABLER);
        result.wrapAllColumns(true);
        result.getMutableColumnOrThrow("StackTrace").setDisplayColumnFactory(StackTraceDisplayColumn::new);

        String path = MothershipManager.get().getIssuesContainer(getContainer());
        ActionURL issueURL = PageFlowUtil.urlProvider(IssuesUrls.class).getDetailsURL(ContainerManager.getForPath(path));
        issueURL.addParameter("issueId", "${BugNumber}");
        result.getMutableColumnOrThrow("BugNumber").setURL(StringExpressionFactory.createURL(issueURL));

        result.setTitleColumn("ExceptionStackTraceId");
        DetailsURL url = new DetailsURL(new ActionURL(MothershipController.ShowStackTraceDetailAction.class, getContainer()), Collections.singletonMap("exceptionStackTraceId", "ExceptionStackTraceId"));
        result.setDetailsURL(url);

        result.getMutableColumnOrThrow("ExceptionStackTraceId").setURL(url);
        result.getMutableColumnOrThrow("ExceptionStackTraceId").setLabel("Exception");
        result.getMutableColumnOrThrow("ExceptionStackTraceId").setFormat("'#'0");
        result.getMutableColumnOrThrow("ExceptionStackTraceId").setExcelFormatString("0");

        result.getMutableColumnOrThrow("Container").setFk(new ContainerForeignKey(this, cf));
        result.getMutableColumnOrThrow("AssignedTo").setFk(new UserIdQueryForeignKey(this, true));
        result.getMutableColumnOrThrow("ModifiedBy").setFk(new UserIdQueryForeignKey(this, true));

        List<FieldKey> defaultCols = new ArrayList<>();
        defaultCols.add(FieldKey.fromParts("ExceptionStackTraceId"));
        defaultCols.add(FieldKey.fromParts("Instances"));
        defaultCols.add(FieldKey.fromParts("LastReport"));
        defaultCols.add(FieldKey.fromParts("BugNumber"));
        defaultCols.add(FieldKey.fromParts("AssignedTo"));
        defaultCols.add(FieldKey.fromParts("StackTrace"));
        defaultCols.add(FieldKey.fromParts("ModifiedBy"));
        defaultCols.add(FieldKey.fromParts("Modified"));
        result.setDefaultVisibleColumns(defaultCols);

        return result;
    }

    public FilteredTable<MothershipSchema> createExceptionReportTableWithStack(ContainerFilter cf)
    {
        FilteredTable<MothershipSchema> result = createExceptionReportTable(cf);
        List<FieldKey> defaultCols = new ArrayList<>(result.getDefaultVisibleColumns());
        defaultCols.removeIf(fieldKey -> fieldKey.getParts().get(0).equals("ServerSessionId"));
        defaultCols.add(0, FieldKey.fromParts("ExceptionStackTraceId"));
        defaultCols.add(1, FieldKey.fromParts("ExceptionStackTraceId", "StackTrace"));
        result.setDefaultVisibleColumns(defaultCols);
        result.setName(EXCEPTION_REPORT_WITH_STACK_TABLE_NAME);
        return result;
    }

    public FilteredTable<MothershipSchema> createExceptionReportTable(ContainerFilter cf)
    {
        FilteredTable<MothershipSchema> result = new FilteredTable<>(MothershipManager.get().getTableInfoExceptionReport(), this, cf);
        result.setDetailsURL(AbstractTableInfo.LINK_DISABLER);
        result.wrapAllColumns(true);
        result.getMutableColumnOrThrow("URL").setDisplayColumnFactory(colInfo ->
        {
            DataColumn result1 = new DataColumn(colInfo);
            result1.setURLExpression(StringExpressionFactory.create("${URL}", false));
            return result1;
        });
        result.getMutableColumnOrThrow("ReferrerURL").setDisplayColumnFactory(colInfo ->
        {
            DataColumn result12 = new DataColumn(colInfo);
            result12.setURLExpression(StringExpressionFactory.create("${ReferrerURL}", false));
            return result12;
        });

        // Container column is on another table so join to it to filter appropriately
        SQLFragment containerSQL = new SQLFragment("ExceptionStackTraceId IN (SELECT ExceptionStackTraceId FROM ");
        containerSQL.append(MothershipManager.get().getTableInfoExceptionStackTrace(), "st");
        containerSQL.append(" WHERE st.Container = ?)");
        containerSQL.add(getContainer().getId());
        result.addCondition(containerSQL);

        // Decorate the stack trace id column and make it a lookup
        var stackTraceIdColumn = result.getMutableColumnOrThrow("ExceptionStackTraceId");
        stackTraceIdColumn.setLabel("Exception");
        stackTraceIdColumn.setURL(new DetailsURL(new ActionURL(MothershipController.ShowStackTraceDetailAction.class, getContainer()), "exceptionStackTraceId", FieldKey.fromParts("ExceptionStackTraceId")));
        stackTraceIdColumn.setFk(new QueryForeignKey.Builder(this, null).schema(this).table(EXCEPTION_STACK_TRACE_TABLE_NAME).build());

        result.getMutableColumnOrThrow("PageflowName").setLabel("Controller");
        result.getMutableColumnOrThrow("PageflowAction").setLabel("Action");

        result.getMutableColumnOrThrow("ServerSessionId").setURL(StringExpressionFactory.createURL("/mothership/showServerSessionDetail.view?serverSessionId=${ServerSessionId}"));
        result.getMutableColumnOrThrow("ServerSessionId").setLabel("Session");
        result.getMutableColumnOrThrow("ServerSessionId").setFormat("'#'0");
        result.getMutableColumnOrThrow("ServerSessionId").setExcelFormatString("0");
        ForeignKey fk = new QueryForeignKey.Builder(this, null).schema(this).table(SERVER_SESSION_TABLE_NAME).build();
        result.getMutableColumnOrThrow("ServerSessionId").setFk(fk);

        List<FieldKey> defaultCols = new ArrayList<>();
        defaultCols.add(FieldKey.fromParts("ServerSessionId"));
        defaultCols.add(FieldKey.fromParts("Created"));
        defaultCols.add(FieldKey.fromParts("ServerSessionId", "SoftwareReleaseId"));
        defaultCols.add(FieldKey.fromParts("PageflowName"));
        defaultCols.add(FieldKey.fromParts("PageflowAction"));
        defaultCols.add(FieldKey.fromParts("Username"));
        defaultCols.add(FieldKey.fromParts("ExceptionMessage"));
        defaultCols.add(FieldKey.fromParts("URL"));
        defaultCols.add(FieldKey.fromParts("ReferrerURL"));
        defaultCols.add(FieldKey.fromParts("SQLState"));
        defaultCols.add(FieldKey.fromParts("ServerSessionId", "DatabaseProductName"));
        defaultCols.add(FieldKey.fromParts("ServerSessionId", "DatabaseProductVersion"));
        defaultCols.add(FieldKey.fromParts("Browser"));
        defaultCols.add(FieldKey.fromParts("ServerSessionId", "RuntimeOS"));
        defaultCols.add(FieldKey.fromParts("ServerSessionId", "JavaVersion"));
        defaultCols.add(FieldKey.fromParts("ServerSessionId", "HeapSize"));
        defaultCols.add(FieldKey.fromParts("ErrorCode"));
        result.setDefaultVisibleColumns(defaultCols);

        return result;
    }

    @Override
    public @NotNull QueryView createView(ViewContext context, @NotNull QuerySettings settings, @Nullable BindException errors)
    {
        if (EXCEPTION_STACK_TRACE_TABLE_NAME.equals(settings.getQueryName()))
        {
            return new ExceptionStackTraceQueryView(this, settings, errors);

        }
        return super.createView(context, settings, errors);
    }

    private static class MothershipTable extends FilteredTable<MothershipSchema>
    {
        public MothershipTable(TableInfo tableInfo, MothershipSchema schema, ContainerFilter cf)
        {
            super(tableInfo, schema, cf);
        }

        @Override
        public boolean hasPermission(@NotNull UserPrincipal user, @NotNull Class<? extends Permission> perm)
        {
            return getContainer().hasPermission(user, perm) && !DeletePermission.class.equals(perm);
        }

        @Nullable
        @Override
        public QueryUpdateService getUpdateService()
        {
            return new SimpleQueryUpdateService(new SimpleUserSchema.SimpleTable<>(this.getUserSchema(), this.getRealTable(), null).init(), this.getRealTable());
        }
    }
}
