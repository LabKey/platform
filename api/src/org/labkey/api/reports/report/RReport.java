/*
 * Copyright (c) 2007-2008 LabKey Corporation
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

import org.apache.commons.lang.BooleanUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.NumberUtils;
import org.labkey.api.admin.AdminUrls;
import org.labkey.api.attachments.Attachment;
import org.labkey.api.attachments.AttachmentParent;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.data.*;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.query.*;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.report.r.ParamReplacement;
import org.labkey.api.reports.report.r.ParamReplacementSvc;
import org.labkey.api.reports.report.r.view.*;
import org.labkey.api.reports.report.view.ReportUtil;
import org.labkey.api.reports.report.view.ReportQueryView;
import org.labkey.api.reports.report.view.RunRReportView;
import org.labkey.api.security.UserManager;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.DateUtil;
import org.labkey.api.view.*;

import java.io.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RReport extends AbstractReport implements AttachmentParent, Report.ResultSetGenerator
{
    public static final String TYPE = "ReportService.rReport";
    //private static Logger _log = Logger.getLogger(RReport.class);
    public static Pattern scriptPattern = Pattern.compile("\\$\\{(.*?)\\}");
    private File _tempFolder;
    private boolean _tempFolderPipeline;

    public static final String FILE_PREFIX = "rpt";
    public static final String REPORT_DIR = "Reports";
    public static final String CACHE_DIR = "cached";

    public static final String SUBSTITUTION_MAP = "substitutionMap.txt";

    public static final String INPUT_FILE_TSV = "input_data";

    public static final String DATA_FILE_SUFFIX = "Data.tsv";

    public static final String DEFAULT_R_CMD = "CMD BATCH --slave";

    private static final String R_EXE = "RReport.RExe";
    private static final String R_CMD = "RReport.RCmd";
    private static final String R_TEMP_FOLDER = "RReport.TempFolder";
    private static final String R_EDIT_PERMISSIONS = "RReport.EditPermissions";
    private static final String R_SCRIPT_HANDLER = "RReport.ScriptHandler";

    static {
        ParamReplacementSvc.get().registerHandler(new ConsoleOutput());
        ParamReplacementSvc.get().registerHandler(new TextOutput());
        ParamReplacementSvc.get().registerHandler(new HtmlOutput());
        ParamReplacementSvc.get().registerHandler(new TsvOutput());
        ParamReplacementSvc.get().registerHandler(new ImageOutput());
        ParamReplacementSvc.get().registerHandler(new PdfOutput());
        ParamReplacementSvc.get().registerHandler(new FileOutput());
        ParamReplacementSvc.get().registerHandler(new PostscriptOutput());
    }

    public enum reportFile {
        script,
        inputData,
        console,
        output,
        substitutionMap,
        includedScript,
    }

    private static String getProp(String prop, String defaultValue)
    {
        Map<String, String> map = UserManager.getUserPreferences(false);
        if (map != null)
        {
            String ret = map.get(prop);
            if (!StringUtils.isEmpty(ret))
                return ret;
        }
        return defaultValue;
    }

    private static void setProp(String prop, String value)
    {
        Map<String, String> map = UserManager.getUserPreferences(true);
        map.put(prop, value);
        PropertyManager.saveProperties(map);
    }

    public static String getRExe() {return getProp(R_EXE, null);}
    public static void setRExe(String programPath) {setProp(R_EXE, programPath);}
    public static String getRCmd() {return getProp(R_CMD, DEFAULT_R_CMD);}
    public static void setRCmd(String command) {setProp(R_CMD, command);}
    public static String getTempFolder() {return getProp(R_TEMP_FOLDER, null);}
    public static void setTempFolder(String folder) {setProp(R_TEMP_FOLDER, folder);}
    public static int getEditPermissions() {return NumberUtils.toInt(getProp(R_EDIT_PERMISSIONS, Integer.toString(org.labkey.api.security.SecurityManager.PermissionSet.ADMIN.getPermissions()))); }
    public static void setEditPermissions(int permissions) {setProp(R_EDIT_PERMISSIONS, Integer.toString(permissions));}
    public static String getRScriptHandler(){return getProp(R_SCRIPT_HANDLER, DefaultScriptRunner.ID);}
    public static void setRScriptHandler(String scriptHandler){setProp(R_SCRIPT_HANDLER, scriptHandler);}

    public static boolean isValidConfiguration()
    {
        return validateConfiguration(getRExe(), getRCmd(), getTempFolder(), getRScriptHandler()) == null;
    }

    public static String validateConfiguration(String programPath, String command, String tempFolder, String scriptHandler)
    {
        if (StringUtils.isEmpty(programPath))
            return "The R program location cannot be empty";

        File rexe = new File(programPath);
        if (!rexe.exists())
            return "The R program location: '" + programPath + "' does not exist";
        if (rexe.isDirectory())
            return "Please specify the entire path to the R executable, not just the directory (e.g., 'c:/Program Files/R/R-2.7.1/bin/R.exe)";

        if (StringUtils.isEmpty(command))
            return "The R command cannot be empty";

        if (!StringUtils.isEmpty(tempFolder))
        {
            File temp = new File(tempFolder);
            if (!temp.exists())
                return "The temp folder: '" + tempFolder + "' does not exist";
        }

        if (RServeScriptRunner.ID.equals(scriptHandler))
        {
            File rserve = RServeScriptRunner.findRServeExe();
            if (rserve == null || !rserve.exists())
                return "Unable to locate RServe, please verify that RServe is installed correctly";
        }
        return null;
    }

    public static RScriptRunner createScriptRunner(RReport report, ViewContext context)
    {
        final String id = getRScriptHandler();
        if (DefaultScriptRunner.ID.equals(id))
            return new DefaultScriptRunner(report, context);
        else if (RServeScriptRunner.ID.equals(id))
            return new RServeScriptRunner(report, context);
        return null;
    }

    public String getType()
    {
        return TYPE;
    }

    public String getTypeDescription()
    {
        return "R View";
    }

    public String getDescriptorType()
    {
        return RReportDescriptor.TYPE;
    }

    public HttpView renderReport(ViewContext viewContext) throws Exception
    {
        VBox view = new VBox();
        String script = getDescriptor().getProperty(RReportDescriptor.Prop.script);

        if (validateConfiguration(getRExe(), getRCmd(), getTempFolder(), getRScriptHandler()) != null)
        {
            final String error = "The R program has not been configured to be used by the LabKey server yet, navigate to the <a href='" + PageFlowUtil.filter(PageFlowUtil.urlProvider(AdminUrls.class).getAdminConsoleURL()) + "'>admin console</a> to configure R.";
            view.addView(new HtmlView("<span class=\"labkey-error\">" + error + "</span>"));
            return view;
        }

        List<String> errors = new ArrayList<String>();
        if (!validateScript(script, errors))
        {
            for (String error : errors)
                view.addView(new HtmlView("<span class=\"labkey-error\">" + error + "</span>"));
            return view;
        }

        List<ParamReplacement> outputSubst = new ArrayList<ParamReplacement>();
        if (!getCachedReport(outputSubst))
        {
            RScriptRunner runner = createScriptRunner(this, viewContext);
            runner.runScript(view, outputSubst);
            cacheResults(outputSubst);
        }
        renderViews(this, view, outputSubst, false);

        return view;
    }

    protected void cacheResults(List<ParamReplacement> replacements)
    {
        if (getDescriptor().getReportId() != -1 &&
            BooleanUtils.toBoolean(getDescriptor().getProperty(ReportDescriptor.Prop.cached)))
        {
            File cacheDir = getCacheDir();
            try {
                File mapFile = new File(cacheDir, SUBSTITUTION_MAP);
                for (ParamReplacement param : replacements)
                {
                    File src = param.getFile();
                    File dst = new File(cacheDir, src.getName());

                    if (dst.createNewFile())
                    {
                        FileUtil.copyFile(src, dst);
                        if (param.getId().equals(ConsoleOutput.ID))
                        {
                            BufferedWriter bw = null;
                            try {
                                bw = new BufferedWriter(new FileWriter(dst, true));
                                bw.write("\nLast cached update : " + DateUtil.formatDateTime() + "\n");
                            }
                            finally
                            {
                                if (bw != null)
                                    try {bw.close();} catch (IOException ioe) {}
                            }
                        }
                        param.setFile(dst);
                    }
                }
                ParamReplacementSvc.get().toFile(replacements, mapFile);
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);
            }
        }
    }

    public void clearCache()
    {
        File cacheDir = getCacheDir();
        if (cacheDir.exists())
            FileUtil.deleteDir(cacheDir);
    }

    protected boolean getCachedReport(List<ParamReplacement> replacements)
    {
        if (getDescriptor().getReportId() != -1 &&
            BooleanUtils.toBoolean(getDescriptor().getProperty(ReportDescriptor.Prop.cached)))
        {
            File cacheDir = getCacheDir();
            try {
                for (ParamReplacement param : ParamReplacementSvc.get().fromFile(new File(cacheDir, SUBSTITUTION_MAP)))
                {
                    replacements.add(param);
                }
                return !replacements.isEmpty();
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);
            }
        }
        return false;
    }

    public void beforeSave(ViewContext context)
    {
        super.beforeSave(context);
        clearCache();
    }

    public void beforeDelete(ViewContext context)
    {
        try {
            // clean up any temp files
            deleteReportDir();
            AttachmentService.get().deleteAttachments(this);
            super.beforeDelete(context);
        }
        catch (SQLException se)
        {
            throw new RuntimeException(se);
        }
    }

    public static void renderViews(RReport report, VBox view, List<ParamReplacement> parameters, boolean deleteTempFiles)
    {
        String sections = (String)HttpView.currentContext().get(renderParam.showSection.name());
        List<String> sectionNames = Collections.emptyList();
        if (sections != null)
            sectionNames = Arrays.asList(sections.split("&"));

        ViewContext context = HttpView.currentContext();
        for (ParamReplacement param : parameters)
        {
            if (isViewable(param, sectionNames))
            {
                // don't show headers if not all sections are being rendered
                if (!sectionNames.isEmpty())
                    param.setHeaderVisible(false);
                param.setReport(report);
                view.addView(param.render(context));
            }
        }
        if (!BooleanUtils.toBoolean(report.getDescriptor().getProperty(RReportDescriptor.Prop.runInBackground)))
            view.addView(new TempFileCleanup(report.getReportDir().getAbsolutePath()));
    }

    private static boolean isViewable(ParamReplacement param, List<String> sectionNames)
    {
        File data = param.getFile();
        if (data.exists())
        {
            if (!sectionNames.isEmpty())
                return sectionNames.contains(param.getName());
            return true;
        }
        return false;
    }

    public HttpView renderDataView(ViewContext context) throws Exception
    {
        return createQueryView(context, getDescriptor());
    }

    public HttpView getRunReportView(ViewContext context) throws Exception
    {
        return new RunRReportView(this);
    }

    protected QueryView createQueryView(ViewContext context, ReportDescriptor descriptor) throws Exception
    {
        final String schemaName = descriptor.getProperty(QueryParam.schemaName.toString());
        final String queryName = descriptor.getProperty(QueryParam.queryName.toString());
        final String viewName = descriptor.getProperty(QueryParam.viewName.toString());
        final String dataRegionName = descriptor.getProperty(QueryParam.dataRegionName.toString());

        if (context != null && schemaName != null)
        {
            UserSchema base = (UserSchema) DefaultSchema.get(context.getUser(), context.getContainer()).getSchema(schemaName);
            QuerySettings settings = base.getSettings(context, dataRegionName);
            settings.setSchemaName(schemaName);
            settings.setQueryName(queryName);
            settings.setViewName(viewName);
            // need to reset the report id since we want to render the data grid, not the report
            settings.setReportId(-1);

            UserSchema schema = base.createView(context, settings).getSchema();
            return new ReportQueryView(schema, settings);
        }
        return null;
    }

    private boolean validateScript(String text, List<String> errors)
    {
        if (StringUtils.isEmpty(text))
        {
            errors.add("An R script must be provided.");
            return false;
        }

        Matcher m = scriptPattern.matcher(text);
        while (m.find())
        {
            String value = m.group(1);
            if (!isValidReplacement(value))
            {
                errors.add("Invalid template, the replacement parameter: " + value + " is unknown.");
                return false;
            }

        }
        return true;
    }

    private boolean isValidReplacement(String value)
    {
        if (INPUT_FILE_TSV.equals(value)) return true;

        return ParamReplacementSvc.get().getHandler(value) != null;
    }

    public static File getFile(RReport report, RReport.reportFile type, String name)
    {
        switch (type)
        {
            case script:
                return new File(report.getReportDir(), "script.R");
            case inputData:
                return new File(report.getReportDir(), RReport.FILE_PREFIX  + RReport.DATA_FILE_SUFFIX );
            case console:
                return new File(report.getReportDir(), "script.Rout");
            case output:
                return new File(report.getReportDir(), "output.out");
            case substitutionMap:
                return new File(report.getReportDir(), RReport.SUBSTITUTION_MAP);
            case includedScript:
                return new File(report.getReportDir(), name);
        }
        return null;
    }

    /*
     * Create the .tsv associated with the data grid for this report.
     */
    public File createInputDataFile(ViewContext context) throws Exception
    {
        return DefaultScriptRunner.createInputDataFile(this, context);
    }

    protected File getTempRoot()
    {
        File tempRoot;
        String tempFolderName = getTempFolder();
        boolean isPipeline = BooleanUtils.toBoolean(getDescriptor().getProperty(RReportDescriptor.Prop.runInBackground));

        if (StringUtils.isEmpty(tempFolderName))
        {
            try {
                if (isPipeline && getDescriptor().getContainerId() != null)
                {
                    Container c = ContainerManager.getForId(getDescriptor().getContainerId());
                    PipeRoot root = PipelineService.get().findPipelineRoot(c);
                    tempRoot = root.resolvePath(REPORT_DIR);
                    if (!tempRoot.exists())
                        tempRoot.mkdirs();
                }
                else
                {
                    File tempDir = new File(System.getProperty("java.io.tmpdir"));
                    tempRoot = new File(tempDir, REPORT_DIR);
                    if (!tempRoot.exists())
                        tempRoot.mkdirs();
                }
            }
            catch (Exception e)
            {
                throw new RuntimeException("Error setting up temp directory", e);
            }
        }
        else
        {
            tempRoot = new File(tempFolderName);
        }
        return tempRoot;
    }

    protected File getCacheDir()
    {
        File cacheDir = new File(getTempRoot(), "Report_" + getDescriptor().getReportId() + File.separator + CACHE_DIR);
        if (!cacheDir.exists())
            cacheDir.mkdirs();

        return cacheDir;
    }

    public File getReportDir()
    {
        boolean isPipeline = BooleanUtils.toBoolean(getDescriptor().getProperty(RReportDescriptor.Prop.runInBackground));
        if (_tempFolder == null || _tempFolderPipeline != isPipeline)
        {
            File tempRoot = getTempRoot();
            if (isPipeline)
                _tempFolder = new File(tempRoot, "Report_" + getDescriptor().getReportId());
            else
                _tempFolder = new File(tempRoot.getAbsolutePath() + File.separator + "Report_" + getDescriptor().getReportId(), String.valueOf(Thread.currentThread().getId()));

            _tempFolderPipeline = isPipeline;
            if (!_tempFolder.exists())
                _tempFolder.mkdirs();
        }
        return _tempFolder;
    }

    public void deleteReportDir()
    {
        boolean isPipeline = BooleanUtils.toBoolean(getDescriptor().getProperty(RReportDescriptor.Prop.runInBackground));
        try {
            File dir = getReportDir();
            if (!isPipeline)
                dir = dir.getParentFile();

            FileUtil.deleteDir(dir);
        }
        finally
        {
            _tempFolder = null;
        }
    }

    public String getEntityId()
    {
        return getDescriptor().getEntityId();
    }

    public String getContainerId()
    {
        return getDescriptor().getContainerId();
    }

    public void setAttachments(Collection<Attachment> attachments)
    {
    }

    public ResultSet generateResultSet(ViewContext context) throws Exception
    {
        ReportDescriptor descriptor = getDescriptor();
        QueryView view = createQueryView(context, descriptor);
        if (view != null)
        {
            DataView dataView = view.createDataView();
            DataRegion rgn = dataView.getDataRegion();
            rgn.setMaxRows(0);
            RenderContext ctx = dataView.getRenderContext();

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
            return rgn.getResultSet(ctx);
        }
        return null;
    }

    public ActionURL getRunReportURL(ViewContext context)
    {
        return ReportUtil.getRunReportURL(context, this);
    }

    public ActionURL getEditReportURL(ViewContext context)
    {
        if (getDescriptor().canEdit(context))
        {
            return ReportUtil.getRunReportURL(context, this).addParameter(TabStripView.TAB_PARAM, RunRReportView.TAB_SOURCE);
        }
        return null;
    }

    protected static class TempFileCleanup extends HttpView
    {
        private String _path;

        public TempFileCleanup(String path)
        {
            _path = path;
        }

        protected void renderInternal(Object model, PrintWriter out) throws Exception
        {
            FileUtil.deleteDir(new File(_path));
        }
    }
}

