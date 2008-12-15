/*
 * Copyright (c) 2008 LabKey Corporation
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
import org.labkey.api.attachments.Attachment;
import org.labkey.api.attachments.AttachmentParent;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.data.*;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.reports.ExternalScriptEngine;
import org.labkey.api.reports.report.r.ParamReplacement;
import org.labkey.api.reports.report.r.ParamReplacementSvc;
import org.labkey.api.reports.report.r.view.*;
import org.labkey.api.reports.report.view.RunReportView;
import org.labkey.api.reports.report.view.ReportUtil;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.*;

import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import java.io.*;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 * User: Karl Lum
 * Date: Dec 3, 2008
 * Time: 2:45:37 PM
 *
 * A Report implementation that uses a ExternalScriptEngine instance to execute the associated script. External
 * script engines are invoked by running an application in an external process. Information is exchanged between the
 * web server and application through the file system.
 */
public class ExternalScriptEngineReport extends ScriptEngineReport implements AttachmentParent
{
    public static final String TYPE = "ReportService.externalScriptEngineReport";

    public static final String FILE_PREFIX = "rpt";
    public static final String INPUT_FILE_TSV = "input_data";
    public static final String SUBSTITUTION_MAP = "substitutionMap.txt";
    public static final String REPORT_DIR = "Reports";
    public static final String CACHE_DIR = "cached";

    public static final String CONSOLE_OUTPUT = "console.txt";
    public static final String DATA_INPUT = "input_data.tsv";

    public static Pattern scriptPattern = Pattern.compile("\\$\\{(.*?)\\}");
    private File _tempFolder;
    private boolean _tempFolderPipeline;
    private static final Map<Integer, ActionURL> _cachedReportURLMap = new HashMap<Integer, ActionURL>();

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

    public String getType()
    {
        return TYPE;
    }

    public HttpView renderReport(ViewContext context) throws Exception
    {
        VBox view = new VBox();
        String script = getDescriptor().getProperty(RReportDescriptor.Prop.script);

/*
        if (validateConfiguration(getRExe(), getRCmd(), getTempFolder(), getRScriptHandler()) != null)
        {
            final String error = "The R program has not been configured to be used by the LabKey server yet, navigate to the <a href='" + PageFlowUtil.filter(PageFlowUtil.urlProvider(AdminUrls.class).getAdminConsoleURL()) + "'>admin console</a> to configure R.";
            view.addView(new HtmlView("<span class=\"labkey-error\">" + error + "</span>"));
            return view;
        }
*/

        List<String> errors = new ArrayList<String>();
        if (!validateScript(script, errors))
        {
            for (String error : errors)
                view.addView(new HtmlView("<span class=\"labkey-error\">" + error + "</span>"));
            return view;
        }

        List<ParamReplacement> outputSubst = new ArrayList<ParamReplacement>();
        if (!getCachedReport(context, outputSubst))
        {
            try {
                runScript(context, outputSubst, createInputDataFile(context));
            }
            catch (ScriptException e)
            {
                final String error1 = "Error executing command";
                final String error2 = PageFlowUtil.filter(e.getMessage());

                errors.add(error1);
                errors.add(error2);

                String err = "<font class=\"labkey-error\">" + error1 + "</font><pre>" + error2 + "</pre>";
                HttpView errView = new HtmlView(err);
                view.addView(errView);
            }
            cacheResults(context, outputSubst);
        }
        renderViews(this, view, outputSubst, false);

        return view;
    }

    public String runScript(ViewContext context, List<ParamReplacement> outputSubst, File inputDataTsv) throws ScriptException
    {
        ScriptEngine engine = getScriptEngine();
        if (engine != null)
        {
            try
            {
                Bindings bindings = engine.getBindings(ScriptContext.ENGINE_SCOPE);

                bindings.put(ExternalScriptEngine.WORKING_DIRECTORY, getReportDir().getAbsolutePath());
                String output = engine.eval(createScript(context, outputSubst, inputDataTsv)).toString();

                // render the output into the console
                if (!StringUtils.isEmpty(output))
                {
                    File console = new File(getReportDir(), CONSOLE_OUTPUT);
                    PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(console)));
                    pw.write(output);
                    pw.close();

                    ParamReplacement param = ParamReplacementSvc.get().getHandlerInstance(ConsoleOutput.ID);
                    param.setName("console");
                    param.setFile(console);

                    outputSubst.add(param);
                }
                return output;
            }
            catch(Exception e)
            {
                throw new ScriptException(e);
            }
        }
        throw new ScriptException("A script engine implementation was not found for the specified report");
    }

    private boolean validateScript(String text, List<String> errors)
    {
        if (StringUtils.isEmpty(text))
        {
            errors.add("Empty script, a script must be provided.");
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

    public static void renderViews(ExternalScriptEngineReport report, VBox view, List<ParamReplacement> parameters, boolean deleteTempFiles)
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

    protected void cacheResults(ViewContext context, List<ParamReplacement> replacements)
    {
        if (getDescriptor().getReportId() != -1 &&
            BooleanUtils.toBoolean(getDescriptor().getProperty(ReportDescriptor.Prop.cached)))
        {
            synchronized(_cachedReportURLMap)
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
                    _cachedReportURLMap.put(getDescriptor().getReportId(), getCacheURL(context.getActionURL()));
                }
                catch (Exception e)
                {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    public void clearCache()
    {
        File cacheDir = getCacheDir();
        if (cacheDir.exists())
            FileUtil.deleteDir(cacheDir);
    }

    protected boolean getCachedReport(ViewContext context, List<ParamReplacement> replacements)
    {
        if (getDescriptor().getReportId() != -1 &&
            BooleanUtils.toBoolean(getDescriptor().getProperty(ReportDescriptor.Prop.cached)))
        {
            synchronized(_cachedReportURLMap)
            {
                if (urlDirty(context.getActionURL()))
                {
                    clearCache();
                    return false;
                }
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
        }
        return false;
    }

    private ActionURL getCacheURL(ActionURL url)
    {
        return url.clone().deleteParameter(RunReportView.CACHE_PARAM).
                deleteParameter(RunReportView.TAB_PARAM);
    }

    /**
     * Detect whether the URL params have changed since this cached report was last rendered.
     */
    private boolean urlDirty(ActionURL url)
    {
        ActionURL cachedURL = _cachedReportURLMap.get(getDescriptor().getReportId());
        if (cachedURL != null)
        {
            Map cur = PageFlowUtil.mapFromQueryString(getCacheURL(url).getQueryString());
            Map prev = PageFlowUtil.mapFromQueryString(cachedURL.getQueryString());

            return !cur.equals(prev);
        }
        return true;
    }

    /**
     * Called before this report is saved or updated
     * @param context
     */
    public void beforeSave(ViewContext context)
    {
        super.beforeSave(context);
        clearCache();
    }

    /**
     * Called before this report is deleted
     * @param context
     */
    public void beforeDelete(ViewContext context)
    {
        try {
            // clean up any temp files
            clearCache();
            deleteReportDir();
            AttachmentService.get().deleteAttachments(this);
            super.beforeDelete(context);
        }
        catch (SQLException se)
        {
            throw new RuntimeException(se);
        }
    }

    public static String validateConfiguration(String programPath, String command, String tempFolder, String scriptHandler)
    {
        return null;
    }

    protected File getTempRoot()
    {
        File tempRoot;
        String tempFolderName = null;// = getTempFolder();
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

    /**
     * Create the script to be executed by the scripting engine
     * @param outputSubst
     * @return
     * @throws Exception
     */
    protected String createScript(ViewContext context, List<ParamReplacement> outputSubst, File inputDataTsv) throws Exception
    {
        return processScript(context, getDescriptor().getProperty(RReportDescriptor.Prop.script), inputDataTsv, outputSubst);
    }

    /**
     * Takes a script source, adds a prolog, processes any input and output replacement parameters
     * @param script
     * @param inputFile
     * @param outputSubst
     * @throws Exception
     */
    protected String processScript(ViewContext context, String script, File inputFile, List<ParamReplacement> outputSubst) throws Exception
    {
        if (!StringUtils.isEmpty(script))
        {
            script = StringUtils.defaultString(getScriptProlog(context)) + script;

            script = processInputReplacement(script, inputFile);
            script = processOutputReplacements(script, outputSubst);
      }
        return script;
    }

    protected String getScriptProlog(ViewContext context)
    {
        return null;
    }

    /*
     * Create the .tsv associated with the data grid for this report.
     */
    public File createInputDataFile(ViewContext context) throws Exception
    {
        if (context != null)
        {
            File resultFile = new File(getReportDir(), DATA_INPUT);

            ResultSet rs = generateResultSet(context);
            TSVGridWriter tsv = createGridWriter(rs);
            tsv.write(resultFile);

            return resultFile;
        }
        return null;
    }

    protected String processInputReplacement(String script, File inputFile) throws Exception
    {
        Matcher m = scriptPattern.matcher(script);
        String inputFileName = inputFile.getAbsolutePath();
        inputFileName = inputFileName.replaceAll("\\\\", "/");

        while (m.find())
        {
            String value = m.group(1);

            if (INPUT_FILE_TSV.equals(value))
            {
                script = m.replaceFirst(inputFileName);
                m = scriptPattern.matcher(script);
            }
        }
        return script;
    }

    protected String processOutputReplacements(String script, List<ParamReplacement> replacements) throws Exception
    {
        return ParamReplacementSvc.get().processParamReplacement(script, getReportDir(), replacements);
    }

    protected TSVGridWriter createGridWriter(ResultSet rs) throws SQLException
    {
        ResultSetMetaData md = rs.getMetaData();
        ColumnInfo cols[] = new ColumnInfo[md.getColumnCount()];

        List<DisplayColumn> dataColumns = new ArrayList();
        for (int i = 0; i < cols.length; i++)
        {
            int sqlColumn = i + 1;
            dataColumns.add(new NADisplayColumn( new ColumnInfo(md, sqlColumn)));
        }
        TSVGridWriter tsv = new TSVGridWriter(rs, dataColumns);
        tsv.setColumnHeaderType(TSVGridWriter.ColumnHeaderType.propertyName);

        return tsv;
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

    public HttpView renderDataView(ViewContext context) throws Exception
    {
        return createQueryView(context, getDescriptor());
    }

    public ActionURL getRunReportURL(ViewContext context)
    {
        return ReportUtil.getRunReportURL(context, this);
    }

    public static class NADisplayColumn extends DataColumn
    {
        public NADisplayColumn(ColumnInfo col)
        {
            super(col);
        }

        public String getTsvFormattedValue(RenderContext ctx)
        {
            String value = super.getTsvFormattedValue(ctx);

            if (StringUtils.isEmpty(value))
                return "NA";
            return value;
        }
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