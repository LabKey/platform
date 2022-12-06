/*
 * Copyright (c) 2011-2019 LabKey Corporation
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

package org.labkey.api.reports.report;

import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.admin.FolderExportContext;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.BaseColumnInfo;
import org.labkey.api.data.BooleanFormat;
import org.labkey.api.data.ColumnHeaderType;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.Results;
import org.labkey.api.data.ResultsFactory;
import org.labkey.api.data.ResultsImpl;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.StashingResultsFactory;
import org.labkey.api.data.TSVGridWriter;
import org.labkey.api.data.Table;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.query.QueryParam;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.SimpleValidationError;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.ValidationError;
import org.labkey.api.query.ValidationException;
import org.labkey.api.reader.Readers;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.permissions.ShareReportPermission;
import org.labkey.api.reports.report.view.AjaxRunScriptReportView;
import org.labkey.api.reports.report.view.AjaxScriptReportView.Mode;
import org.labkey.api.reports.report.view.ReportQueryView;
import org.labkey.api.reports.report.view.ReportUtil;
import org.labkey.api.reports.report.view.ScriptReportBean;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AnalystPermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DataView;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.TabStripView;
import org.labkey.api.view.ViewContext;
import org.labkey.api.writer.ContainerUser;
import org.labkey.api.writer.VirtualFile;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/*
* User: adam
* Date: Dec 21, 2010
* Time: 7:57:11 PM
*
* This is a simple base class that represents reports that are defined by a text file (editable or static module file).
* The subclass ScriptEngineReport is the base class for reports that use a ScriptEngine to interpret/execute this file.
*/
public abstract class ScriptReport extends AbstractReport
{
    public static final String TAB_SOURCE = "Source";
    public static final String REPORT_DIR = "reports_temp";

    /**
     * Create the query view used to generate the result set that this report operates on.
     */
    @Nullable
    protected QueryView createQueryView(ViewContext context, ReportDescriptor descriptor)
    {
        final String schemaName = descriptor.getProperty(QueryParam.schemaName.toString());
        final String queryName = descriptor.getProperty(QueryParam.queryName.toString());
        final String viewName = descriptor.getProperty(QueryParam.viewName.toString());
        final String dataRegionName = descriptor.getProperty(QueryParam.dataRegionName.toString());

        if (context != null && schemaName != null)
        {
            UserSchema base = QueryService.get().getUserSchema(context.getUser(), context.getContainer(), schemaName);

            if (base != null)
            {
                QuerySettings settings = base.getSettings(context, dataRegionName);
                settings.setSchemaName(schemaName);
                settings.setQueryName(queryName);
                settings.setViewName(viewName);
                // need to reset the report id since we want to render the data grid, not the report
                settings.setReportId(null);

                UserSchema schema = base.createView(context, settings).getSchema();
                return new ReportQueryView(schema, settings);
            }
        }

        return null;
    }


    /* Helper for subclasses that want to implement  Report.ResultSetGenerator */
    public Results _generateResults(ViewContext context, boolean allowAsyncQuery) throws Exception
    {
        ReportDescriptor descriptor = getDescriptor();
        QueryView view = createQueryView(context, descriptor);
        validateQueryView(view);

        if (view != null)
        {
            view.getSettings().setMaxRows(Table.ALL_ROWS);
            DataView dataView = view.createDataView();
            DataRegion rgn = dataView.getDataRegion();
            RenderContext ctx = dataView.getRenderContext();
            rgn.setAllowAsync(false);

            // temporary code until we add a more generic way to specify a filter or grouping on the chart
            final String filterParam = descriptor.getProperty(ReportDescriptor.Prop.filterParam);

            if (!StringUtils.isEmpty(filterParam))
            {
                final String filterValue = (String)context.get(filterParam);

                if (filterValue != null)
                {
                    SimpleFilter filter = new SimpleFilter();
                    filter.addCondition(filterParam, filterValue, CompareType.EQUAL);

                    ctx.setBaseFilter(filter);
                }
            }

            if (null == rgn.getResults(ctx))
                return null;

            return new ResultsImpl(ctx);
        }

        return null;
    }

    /*
     * Create the .tsv associated with the data grid for this report.
     */
    public File _createInputDataFile(@NotNull ViewContext context, ResultsFactory factory, File resultFile) throws SQLException, IOException, ValidationException
    {
        try (StashingResultsFactory srf = new StashingResultsFactory(factory))
        {
            Results results = srf.get();
            if (results != null && results.getResultSet() != null)
            {
                List<String> outputColumnNames = outputColumnNames(results);
                ResultSetMetaData md = results.getMetaData();
                List<DisplayColumn> dataColumns = new ArrayList<>();

                for (int i = 0; i < md.getColumnCount(); i++)
                {
                    int sqlColumn = i + 1;
                    dataColumns.add(new ScriptEngineReport.NADisplayColumn(outputColumnNames.get(i), new BaseColumnInfo(md, sqlColumn)));
                }

                // TSVGridWriter closes the Results at render time
                try (TSVGridWriter tsv = new TSVGridWriter(srf, dataColumns))
                {
                    tsv.setColumnHeaderType(ColumnHeaderType.Name); // CONSIDER: Use FieldKey instead
                    tsv.write(resultFile);
                }
            }
        }
        catch (RuntimeException e)
        {
            Throwable cause = e.getCause();

            if (cause instanceof ValidationException)
                throw (ValidationException)cause;

            throw e;
        }

        return resultFile;
    }


    /* default results name mapping (ScriptEngineReport has different name handling */
    protected List<String> outputColumnNames(Results r)
    {
        assert null != r.getResultSet();
        CaseInsensitiveHashSet aliases = new CaseInsensitiveHashSet(); // output names
        try
        {
            int count = r.getMetaData().getColumnCount();
            ArrayList<String> ret = new ArrayList<>(count);
            for (int col = 1; col <= count; col++)
            {
                String alias = r.getColumn(col).getPropertyName();
                if (!aliases.add(alias))
                {
                    int i;
                    for (i = 1; !aliases.add(alias + i); i++)
                        ;
                    alias = alias + i;
                }
                ret.add(alias);
            }
            return ret;
        }
        catch (SQLException sqlx)
        {
            throw UnexpectedException.wrap(sqlx);
        }
    }


    public static File getTempRoot(ReportDescriptor descriptor)
    {
        File tempRoot;
        boolean isPipeline = BooleanUtils.toBoolean(descriptor.getProperty(ScriptReportDescriptor.Prop.runInBackground));

        try
        {
            if (isPipeline && descriptor.getContainerId() != null)
            {
                Container c = ContainerManager.getForId(descriptor.getContainerId());
                PipeRoot root = PipelineService.get().findPipelineRoot(c);
                tempRoot = root.resolvePath(REPORT_DIR);

                if (!tempRoot.exists())
                    tempRoot.mkdirs();
            }
            else
            {
                tempRoot = getDefaultTempRoot();

                if (!tempRoot.exists())
                    tempRoot.mkdirs();
            }
        }
        catch (Exception e)
        {
            throw new RuntimeException("Error setting up temp directory", e);
        }

        return tempRoot;
    }

    @NotNull
    public static File getDefaultTempRoot()
    {
        File tempDir = new File(System.getProperty("java.io.tmpdir"));
        return new File(tempDir, REPORT_DIR);
    }


    public abstract boolean supportsPipeline();

    public String getDownloadDataHelpMessage()
    {
        return "You can download the data via this link to help with the development of your script.";
    }

    // At the moment, only R reports support shared scripts
    public List<Report> getAvailableSharedScripts(ViewContext context, ScriptReportBean bean)
    {
        return Collections.emptyList();
    }

    public @Nullable String getEditAreaSyntax()
    {
        return null;
    }

    // When creating a new script report, populate the editarea with this text
    public String getDefaultScript()
    {
        return "";
    }

    public boolean hasClientDependencies()
    {
        return false;
    }

    public @Nullable String getDesignerHelpHtml()
    {
        return null;
    }

    @Override
    public ActionURL getEditReportURL(ViewContext context)
    {
        if (canEdit(context.getUser(), context.getContainer()))
        {
            return ReportUtil.getRunReportURL(context, this, true).addParameter(TabStripView.TAB_PARAM, TAB_SOURCE);
        }
        return null;
    }

    @Override
    public HttpView getRunReportView(ViewContext context) throws Exception
    {
        String tabId = (String) context.get("tabId");

        if (null == tabId)
            tabId = context.getActionURL().getParameter("tabId");

        String webpartString = (String) context.get(Report.renderParam.reportWebPart.name());
        boolean webpart = (null != webpartString && BooleanFormat.getInstance().parseObject(webpartString));

        // Module-based reports are always read-only, but we still allow viewing the report source in the source tab.
        // if tab == "Source" then use update mode, which lets developers edit the source
        // otherwise, if we're a webpart then use view mode
        // otherwise, use viewAndUpdate, which means show the view tab first, but let developers edit the source
        Mode mode = (TAB_SOURCE.equals(tabId) ? Mode.update : (webpart ? Mode.view : Mode.viewAndUpdate));

        return new AjaxRunScriptReportView(this, mode);
    }

    @Override
    public boolean canEdit(User user, Container container, List<ValidationError> errors)
    {
        // HACK: We shouldn't be using canEdit permissions to check during view. Rather we should new up
        // a report and check using standard security.
        if (errors.isEmpty() && getDescriptor().isModuleBased())
        {
            return true;
        }

        super.canEdit(user, container, errors);
        if (!errors.isEmpty())
            return false;

        if (!user.isTrustedAnalyst())
        {
            errors.add(new SimpleValidationError("You must be either a PlatformDeveloper or TrustedAnalyst to update a script report."));
        }
        else if (isPrivate() || getDescriptor().hasCustomAccess())
        {
            if (!container.hasPermission(user, InsertPermission.class))
                errors.add(new SimpleValidationError("You must be in the Author role to update a private or custom script report."));
        }

        return errors.isEmpty();
    }

    @Override
    public boolean canShare(User user, Container container, List<ValidationError> errors)
    {
        super.canShare(user, container, errors);

        if (errors.isEmpty())
        {
            if (isPrivate())
            {
                if (user.hasRootPermission(AnalystPermission.class))
                {
                    if (!container.hasPermission(user, ShareReportPermission.class))
                        errors.add(new SimpleValidationError("You must be in the Author role to share a private script report."));
                }
                else
                    errors.add(new SimpleValidationError("You must be in the Developers groups to share a private script report."));
            }
        }
        return errors.isEmpty();
    }


    @Override
    public boolean hasContentModified(ContainerUser context)
    {
        // Content modified if change to the "script" config property
        return hasDescriptorPropertyChanged(context.getUser(), ScriptReportDescriptor.Prop.script.name());
    }


    @Override
    public ScriptReportDescriptor getDescriptor()
    {
        return (ScriptReportDescriptor)super.getDescriptor();
    }


    @Override
    public void serializeToFolder(FolderExportContext ctx, VirtualFile directory) throws IOException
    {
        ScriptReportDescriptor descriptor = getDescriptor();

        if (descriptor.getReportId() != null)
        {
            // for script based reports, write the script portion to a separate file to facilitate script modifications
            String scriptFileName = getSerializedScriptFileName(ctx);

            try (PrintWriter writer = directory.getPrintWriter(scriptFileName))
            {
                String script = StringUtils.defaultString(descriptor.getProperty(ScriptReportDescriptor.Prop.script));
                writer.write(script);
            }

            super.serializeToFolder(ctx, directory);
        }
        else
            throw new IllegalArgumentException("Cannot serialize a report that hasn't been saved yet");
    }


    protected String getSerializedScriptFileName()
    {
        return getSerializedScriptFileName(null);
    }


    protected String getSerializedScriptFileName(FolderExportContext context)
    {
        String extension = getDefaultExtension(context);
        ReportNameContext rnc = context.getContext(ReportNameContext.class);
        String reportName = rnc.getSerializedName();

        return FileUtil.makeLegalName(String.format("%s.%s", reportName, extension));
    }


    protected String getDefaultExtension(FolderExportContext context)
    {
        return "script";
    }


    @Override
    public void afterDeserializeFromFile(File reportFile) throws IOException
    {
        if (reportFile.exists())
        {
            // check to see if there is a separate script file on the disk, a separate
            // script file takes precedence over any meta-data based script.

            File scriptFile = new File(reportFile.getParent(), getSerializedScriptFileName());

            if (scriptFile.exists())
            {
                StringBuilder sb = new StringBuilder();

                try (BufferedReader br = Readers.getReader(scriptFile))
                {
                    String l;

                    while ((l = br.readLine()) != null)
                    {
                        sb.append(l);
                        sb.append('\n');
                    }

                    getDescriptor().setProperty(ScriptReportDescriptor.Prop.script, sb.toString());
                }
            }
        }
    }

}
