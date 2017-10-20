/*
 * Copyright (c) 2007-2017 LabKey Corporation
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
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DataColumn;
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
import org.labkey.api.query.LookupForeignKey;
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

/**
 * User: jeckels
 * Date: Mar 28, 2007
 */
public class MothershipSchema extends UserSchema
{
    private static final String SCHEMA_NAME = "mothership";
    private static final String SCHEMA_DESCR = "Contains data about exceptions that have occurred on this server.";

    public static final String SERVER_INSTALLATIONS_TABLE_NAME = "ServerInstallations";
    public static final String SERVER_SESSIONS_TABLE_NAME = "ServerSessions";
    public static final String EXCEPTION_REPORT_TABLE_NAME = "ExceptionReport";
    public static final String EXCEPTION_REPORT_WITH_STACK_TABLE_NAME = "ExceptionReportWithStack";
    public static final String EXCEPTION_STACK_TRACE_TABLE_NAME = "ExceptionStackTrace";
    public static final String SOFTWARE_RELEASES_TABLE_NAME = "SoftwareReleases";

    private static Set<String> TABLE_NAMES = Collections.unmodifiableSet(new LinkedHashSet<>(
        Arrays.asList(
            SERVER_INSTALLATIONS_TABLE_NAME,
            SERVER_SESSIONS_TABLE_NAME,
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
            public QuerySchema createSchema(DefaultSchema schema, Module module)
            {
                return new MothershipSchema(schema.getUser(), schema.getContainer());
            }
        });
    }

    public Set<String> getTableNames()
    {
        return TABLE_NAMES;
    }


    public TableInfo createTable(String name)
    {
        if (name.equalsIgnoreCase(SERVER_INSTALLATIONS_TABLE_NAME))
        {
            return createServerInstallationTable();
        }
        else if (name.equalsIgnoreCase(SERVER_SESSIONS_TABLE_NAME))
        {
            return createServerSessionTable();
        }
        else if (name.equalsIgnoreCase(EXCEPTION_STACK_TRACE_TABLE_NAME))
        {
            return createExceptionStackTraceTable();
        }
        else if (name.equalsIgnoreCase(SOFTWARE_RELEASES_TABLE_NAME))
        {
            return createSoftwareReleasesTable();
        }
        else if (name.equalsIgnoreCase(EXCEPTION_REPORT_TABLE_NAME))
        {
            return createExceptionReportTable();
        }
        else if (name.equalsIgnoreCase(EXCEPTION_REPORT_WITH_STACK_TABLE_NAME))
        {
            return createExceptionReportTableWithStack();
        }
        return null;
    }

    public FilteredTable createSoftwareReleasesTable()
    {
        FilteredTable result = new FilteredTable<>(MothershipManager.get().getTableInfoSoftwareRelease(), this);
        result.wrapAllColumns(true);

        result.getColumn("SVNURL").setWidth("500");

        List<FieldKey> defaultCols = new ArrayList<>();
        defaultCols.add(FieldKey.fromParts("Description"));
        defaultCols.add(FieldKey.fromParts("SVNRevision"));
        defaultCols.add(FieldKey.fromParts("SVNURL"));
        result.setDefaultVisibleColumns(defaultCols);

        result.setDetailsURL(new DetailsURL(new ActionURL(MothershipController.ShowUpdateAction.class, getContainer()), Collections.singletonMap("softwareReleaseId", "SoftwareReleaseId")));

        return result;
    }

    public FilteredTable createServerSessionTable()
    {
        FilteredTable result = new FilteredTable<>(MothershipManager.get().getTableInfoServerSession(), this);
        result.wrapAllColumns(true);
        result.setTitleColumn("RowId");

        result.getColumn("ServerInstallationId").setFk(new LookupForeignKey("ServerInstallationId")
        {
            public TableInfo getLookupTableInfo()
            {
                return createServerInstallationTable();
            }

            @Override
            public String getLookupSchemaName()
            {
                return SCHEMA_NAME;
            }

            @Override
            public String getLookupTableName()
            {
                return SERVER_INSTALLATIONS_TABLE_NAME;
            }
        });
        result.getColumn("ServerInstallationId").setLabel("Server");

        ColumnInfo earliestCol = result.getColumn("EarliestKnownTime");
        ColumnInfo latestCol = result.getColumn("LastKnownTime");

        ExprColumn durationCol = new ExprColumn(result, "Duration", new SQLFragment(MothershipManager.get().getDialect().getDateDiff(Calendar.DATE, "LastKnownTime", "EarliestKnownTime")), JdbcType.INTEGER, earliestCol, latestCol);
        result.addColumn(durationCol);

        ExprColumn exceptionCountCol = new ExprColumn(result, "ExceptionCount", new SQLFragment("(SELECT COUNT(*) FROM " + MothershipManager.get().getTableInfoExceptionReport() + " WHERE ServerSessionId = " + ExprColumn.STR_TABLE_ALIAS + ".ServerSessionId)"), JdbcType.INTEGER);
        exceptionCountCol.setFormat("#.#");
        result.addColumn(exceptionCountCol);

        List<FieldKey> defaultCols = new ArrayList<>();
        defaultCols.add(FieldKey.fromString("SVNRevision"));
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

    public TableInfo createServerInstallationTable()
    {
        FilteredTable<MothershipSchema> result = new MothershipTable(MothershipManager.get().getTableInfoServerInstallation(), this);
        result.setInsertURL(AbstractTableInfo.LINK_DISABLER);
        result.setImportURL(AbstractTableInfo.LINK_DISABLER);
        result.wrapAllColumns(true);

        ActionURL url = new ActionURL(MothershipController.ShowInstallationDetailAction.class, getContainer());
        url.addParameter("serverInstallationId","${ServerInstallationId}");
        result.getColumn("ServerHostName").setURL(StringExpressionFactory.createURL(url));

        SQLFragment firstPingSQL = new SQLFragment("(SELECT MIN(EarliestKnownTime) FROM ");
        firstPingSQL.append(MothershipManager.get().getTableInfoServerSession(), "ss");
        firstPingSQL.append(" WHERE ss.ServerInstallationId = ");
        firstPingSQL.append(ExprColumn.STR_TABLE_ALIAS);
        firstPingSQL.append(".ServerInstallationId)");
        result.addColumn(new ExprColumn(result, "FirstPing", firstPingSQL, JdbcType.TIMESTAMP));

        SQLFragment lastPing = new SQLFragment("(SELECT MAX(LastKnownTime) FROM ");
        lastPing.append(MothershipManager.get().getTableInfoServerSession(), "ss");
        lastPing.append(" WHERE ss.ServerInstallationId = ");
        lastPing.append(ExprColumn.STR_TABLE_ALIAS);
        lastPing.append(".ServerInstallationId)");
        result.addColumn(new ExprColumn(result, "LastPing", lastPing, JdbcType.TIMESTAMP));

        SQLFragment exceptionCount = new SQLFragment("(SELECT COUNT(*) FROM ");
        exceptionCount.append(MothershipManager.get().getTableInfoServerSession(), "ss");
        exceptionCount.append(",");
        exceptionCount.append(MothershipManager.get().getTableInfoExceptionReport(), "er");
        exceptionCount.append(" WHERE ss.ServerInstallationId = ");
        exceptionCount.append(ExprColumn.STR_TABLE_ALIAS);
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
        daysActive.append(ExprColumn.STR_TABLE_ALIAS);
        daysActive.append(".ServerInstallationId)");
        ExprColumn daysActiveColumn = new ExprColumn(result, "DaysActive", daysActive, JdbcType.INTEGER);
        daysActiveColumn.setFormat("#.#");
        result.addColumn(daysActiveColumn);

        SQLFragment versionCount = new SQLFragment("(SELECT COUNT(DISTINCT(softwarereleaseid)) FROM ");
        versionCount.append(MothershipManager.get().getTableInfoServerSession(), "ss");
        versionCount.append(" WHERE ss.ServerInstallationId = ");
        versionCount.append(ExprColumn.STR_TABLE_ALIAS);
        versionCount.append(".ServerInstallationId)");
        result.addColumn(new ExprColumn(result, "VersionCount", versionCount, JdbcType.INTEGER));

        SQLFragment currentVersion = new SQLFragment("(SELECT MAX(ServerSessionID) FROM ");
        currentVersion.append(MothershipManager.get().getTableInfoServerSession(), "ss");
        currentVersion.append(" WHERE ss.ServerInstallationId = ");
        currentVersion.append(ExprColumn.STR_TABLE_ALIAS);
        currentVersion.append(".ServerInstallationId)");
        ExprColumn currentVersionColumn = new ExprColumn(result, "MostRecentSession", currentVersion, JdbcType.INTEGER);
        currentVersionColumn.setFk(new LookupForeignKey("ServerSessionID")
        {
            public TableInfo getLookupTableInfo()
            {
                return createServerSessionTable();
            }

            @Override
            public String getLookupSchemaName()
            {
                return SCHEMA_NAME;
            }

            @Override
            public String getLookupTableName()
            {
                return SERVER_SESSIONS_TABLE_NAME;
            }
        });
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

    public FilteredTable createExceptionStackTraceTable()
    {
        FilteredTable<MothershipSchema> result = new MothershipTable(MothershipManager.get().getTableInfoExceptionStackTrace(), this);
        result.setUpdateURL(AbstractTableInfo.LINK_DISABLER);
        result.setInsertURL(AbstractTableInfo.LINK_DISABLER);
        result.setImportURL(AbstractTableInfo.LINK_DISABLER);
        result.wrapAllColumns(true);
        result.getColumn("StackTrace").setDisplayColumnFactory(StackTraceDisplayColumn::new);

        LookupForeignKey softwareReleaseFK = new LookupForeignKey("SoftwareReleaseId")
        {
            @Override
            public TableInfo getLookupTableInfo()
            {
                return getTable(MothershipSchema.SOFTWARE_RELEASES_TABLE_NAME);
            }
        };

        ExprColumn maxRevisionColumn = new ExprColumn(result, "MaxSVNRevision", getSoftwareReleaseSQL("DESC", false), JdbcType.VARCHAR);
        maxRevisionColumn.setDescription("Highest SVN revision (and corresponding SVN branch)");
        result.addColumn(maxRevisionColumn);

        ExprColumn maxRevisionLookupColumn = new ExprColumn(result, "MaxSVNRevisionLookup", getSoftwareReleaseSQL("DESC", true), JdbcType.INTEGER);
        maxRevisionLookupColumn.setFk(softwareReleaseFK);
        maxRevisionLookupColumn.setDescription("Highest SVN revision (and corresponding SVN branch). Slower, but full lookup to mothership.SoftwareRelease");
        result.addColumn(maxRevisionLookupColumn);

        ExprColumn minRevisionColumn = new ExprColumn(result, "MinSVNRevision", getSoftwareReleaseSQL("ASC", false), JdbcType.VARCHAR);
        minRevisionColumn.setDescription("Lowest SVN revision (and corresponding SVN branch)");
        result.addColumn(minRevisionColumn);

        ExprColumn minRevisionLookupColumn = new ExprColumn(result, "MinSVNRevisionLookup", getSoftwareReleaseSQL("ASC", true), JdbcType.INTEGER);
        minRevisionLookupColumn.setFk(softwareReleaseFK);
        minRevisionColumn.setDescription("Smallest SVN revision (and corresponding SVN branch). Slower, but full lookup to mothership.SoftwareRelease");
        result.addColumn(minRevisionLookupColumn);

        String path = MothershipManager.get().getIssuesContainer(getContainer());
        ActionURL issueURL = PageFlowUtil.urlProvider(IssuesUrls.class).getDetailsURL(ContainerManager.getForPath(path));
        issueURL.addParameter("issueId", "${BugNumber}");
        result.getColumn("BugNumber").setURL(StringExpressionFactory.createURL(issueURL));

        ActionURL stack = new ActionURL(MothershipController.ShowStackTraceDetailAction.class, getContainer());
        stack.addParameter("exceptionStackTraceId","${ExceptionStackTraceId}");
        result.getColumn("ExceptionStackTraceId").setURL(StringExpressionFactory.createURL(stack));
        result.getColumn("ExceptionStackTraceId").setLabel("Exception");
        result.getColumn("ExceptionStackTraceId").setFormat("'#'0");
        result.getColumn("ExceptionStackTraceId").setExcelFormatString("0");

        result.setTitleColumn("ExceptionStackTraceId");
        result.setDetailsURL(new DetailsURL(new ActionURL(MothershipController.ShowStackTraceDetailAction.class, getContainer()), Collections.singletonMap("exceptionStackTraceId", "ExceptionStackTraceId")));

        result.getColumn("ModifiedBy").setFk(new UserIdQueryForeignKey(getUser(), getContainer(), true));

        List<FieldKey> defaultCols = new ArrayList<>();
        defaultCols.add(FieldKey.fromParts("ExceptionStackTraceId"));
        defaultCols.add(FieldKey.fromParts("Instances"));
        defaultCols.add(FieldKey.fromParts("MaxSVNRevision"));
        defaultCols.add(FieldKey.fromParts("LastReport"));
        defaultCols.add(FieldKey.fromParts("BugNumber"));
        defaultCols.add(FieldKey.fromParts("AssignedTo"));
        defaultCols.add(FieldKey.fromParts("StackTrace"));
        defaultCols.add(FieldKey.fromParts("ModifiedBy"));
        defaultCols.add(FieldKey.fromParts("Modified"));
        result.setDefaultVisibleColumns(defaultCols);

        return result;
    }

    private SQLFragment getSoftwareReleaseSQL(String sort, boolean lookup)
    {
        // We want the SoftwareReleaseId from the row that has the min or max SVN revision value

        // Do a sort by SVNRevision 
        SQLFragment subselect = new SQLFragment("SELECT " + (lookup ? "ss.SoftwareReleaseId" : "sr.Description" ) + " FROM " +
                MothershipManager.get().getTableInfoExceptionReport() + " er, " +
                MothershipManager.get().getTableInfoSoftwareRelease() + " sr, " +
                MothershipManager.get().getTableInfoServerSession() + " ss " +
                " WHERE er.ExceptionStackTraceId = " + ExprColumn.STR_TABLE_ALIAS + ".ExceptionStackTraceId" +
                " AND ss.ServerSessionId = er.ServerSessionId AND ss.SoftwareReleaseId = sr.SoftwareReleaseId ORDER BY SVNRevision " + sort);

        // Then apply a limit so that we only get one row back
        SQLFragment result = new SQLFragment("(");
        result.append(getDbSchema().getSqlDialect().limitRows(subselect, 1));
        result.append(")");
        return result;
    }

    public FilteredTable createExceptionReportTableWithStack()
    {
        FilteredTable result = createExceptionReportTable();
        List<FieldKey> defaultCols = new ArrayList<>(result.getDefaultVisibleColumns());
        defaultCols.removeIf(fieldKey -> fieldKey.getParts().get(0).equals("ServerSessionId"));
        defaultCols.add(0, FieldKey.fromParts("ExceptionStackTraceId"));
        defaultCols.add(1, FieldKey.fromParts("ExceptionStackTraceId", "StackTrace"));
        result.setDefaultVisibleColumns(defaultCols);
        result.setName(EXCEPTION_REPORT_WITH_STACK_TABLE_NAME);
        return result;
    }

    public FilteredTable createExceptionReportTable()
    {
        FilteredTable result = new FilteredTable<>(MothershipManager.get().getTableInfoExceptionReport(), this);
        result.setDetailsURL(AbstractTableInfo.LINK_DISABLER);
        result.wrapAllColumns(true);
        result.getColumn("URL").setDisplayColumnFactory(colInfo ->
        {
            DataColumn result1 = new DataColumn(colInfo);
            result1.setURLExpression(StringExpressionFactory.create("${URL}", false));
            return result1;
        });
        result.getColumn("ReferrerURL").setDisplayColumnFactory(colInfo ->
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
        ColumnInfo stackTraceIdColumn = result.getColumn("ExceptionStackTraceId");
        stackTraceIdColumn.setLabel("Exception");
        stackTraceIdColumn.setURL(new DetailsURL(new ActionURL(MothershipController.ShowStackTraceDetailAction.class, getContainer()), "exceptionStackTraceId", FieldKey.fromParts("ExceptionStackTraceId")));
        stackTraceIdColumn.setFk(new LookupForeignKey("ExceptionStackTraceId")
        {
            public TableInfo getLookupTableInfo()
            {
                return createExceptionStackTraceTable();
            }

            @Override
            public String getLookupSchemaName()
            {
                return SCHEMA_NAME;
            }

            @Override
            public String getLookupTableName()
            {
                return EXCEPTION_STACK_TRACE_TABLE_NAME;
            }
        });

        result.getColumn("PageflowName").setLabel("Controller");
        result.getColumn("PageflowAction").setLabel("Action");

        result.getColumn("ServerSessionId").setURL(StringExpressionFactory.createURL("/mothership/showServerSessionDetail.view?serverSessionId=${ServerSessionId}"));
        result.getColumn("ServerSessionId").setLabel("Session");
        result.getColumn("ServerSessionId").setFormat("'#'0");
        result.getColumn("ServerSessionId").setExcelFormatString("0");
        LookupForeignKey fk = new LookupForeignKey("ServerSessionId")
        {
            public TableInfo getLookupTableInfo()
            {
                return createServerSessionTable();
            }

            @Override
            public String getLookupSchemaName()
            {
                return SCHEMA_NAME;
            }

            @Override
            public String getLookupTableName()
            {
                return SERVER_SESSIONS_TABLE_NAME;
            }
        };
        fk.setPrefixColumnCaption(false);
        result.getColumn("ServerSessionId").setFk(fk);

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
    public QueryView createView(ViewContext context, @NotNull QuerySettings settings, BindException errors)
    {
        if (EXCEPTION_STACK_TRACE_TABLE_NAME.equals(settings.getQueryName()))
        {
            return new ExceptionStackTraceQueryView(this, settings, errors);

        }
        return super.createView(context, settings, errors);
    }

    private static class MothershipTable extends FilteredTable<MothershipSchema>
    {
        public MothershipTable(TableInfo tableInfo, MothershipSchema schema)
        {
            super(tableInfo, schema);
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
            return new SimpleQueryUpdateService(new SimpleUserSchema.SimpleTable<>(this.getUserSchema(), this.getRealTable()).init(), this.getRealTable());
        }
    }
}
