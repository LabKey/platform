/*
 * Copyright (c) 2008-2017 LabKey Corporation
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
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.admin.ImportContext;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.ColumnHeaderType;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.Results;
import org.labkey.api.data.ResultsImpl;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TSVGridWriter;
import org.labkey.api.data.Table;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.query.AliasManager;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryView;
import org.labkey.api.reader.Readers;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.ReportService;
import org.labkey.api.reports.report.r.ParamReplacement;
import org.labkey.api.reports.report.r.ParamReplacementSvc;
import org.labkey.api.reports.report.r.view.ConsoleOutput;
import org.labkey.api.reports.report.r.view.FileOutput;
import org.labkey.api.reports.report.r.view.HrefOutput;
import org.labkey.api.reports.report.r.view.HtmlOutput;
import org.labkey.api.reports.report.r.view.ImageOutput;
import org.labkey.api.reports.report.r.view.JpgOutput;
import org.labkey.api.reports.report.r.view.JsonOutput;
import org.labkey.api.reports.report.r.view.KnitrOutput;
import org.labkey.api.reports.report.r.view.PdfOutput;
import org.labkey.api.reports.report.r.view.PngOutput;
import org.labkey.api.reports.report.r.view.PostscriptOutput;
import org.labkey.api.reports.report.r.view.ROutputView;
import org.labkey.api.reports.report.r.view.SvgOutput;
import org.labkey.api.reports.report.r.view.TextOutput;
import org.labkey.api.reports.report.r.view.TsvOutput;
import org.labkey.api.reports.report.view.ReportUtil;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.thumbnail.Thumbnail;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.ResultSetUtil;
import org.labkey.api.view.DataView;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.VBox;
import org.labkey.api.view.ViewContext;
import org.labkey.api.writer.ContainerUser;
import org.labkey.api.writer.VirtualFile;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 * User: Karl Lum
 * Date: Dec 3, 2008
 * Time: 2:45:37 PM
 *
 * A Report implementation that uses a ScriptEngine instance to execute the associated script.
*/
public abstract class ScriptEngineReport extends ScriptReport implements Report.ResultSetGenerator
{
    public static final String INPUT_FILE_TSV = "input_data";
    public static Pattern scriptPattern = Pattern.compile("\\$\\{(.*?)\\}");

    public static final String TYPE = "ReportService.scriptEngineReport";
    public static final String DATA_INPUT = "input_data.tsv";
    public static final String REPORT_DIR = "reports_temp";
    public static final String FILE_PREFIX = "rpt";
    public static final String SUBSTITUTION_MAP = "substitutionMap.txt";
    public static final String CONSOLE_OUTPUT = "console.txt";

    private static Logger _log = Logger.getLogger(ScriptEngineReport.class);

    static
    {
        ParamReplacementSvc.get().registerHandler(new ConsoleOutput());
        ParamReplacementSvc.get().registerHandler(new TextOutput());
        ParamReplacementSvc.get().registerHandler(new HtmlOutput());
        ParamReplacementSvc.get().registerHandler(new SvgOutput());
        ParamReplacementSvc.get().registerHandler(new TsvOutput());
        ParamReplacementSvc.get().registerHandler(new ImageOutput());
        ParamReplacementSvc.get().registerHandler(new JpgOutput());
        ParamReplacementSvc.get().registerHandler(new PngOutput());
        ParamReplacementSvc.get().registerHandler(new PdfOutput());
        ParamReplacementSvc.get().registerHandler(new FileOutput());
        ParamReplacementSvc.get().registerHandler(new PostscriptOutput());
        ParamReplacementSvc.get().registerHandler(new JsonOutput());
        ParamReplacementSvc.get().registerHandler(new HrefOutput());
        ParamReplacementSvc.get().registerHandler(new KnitrOutput());
    }

    public String getType()
    {
        return TYPE;
    }

    public String getDescriptorType()
    {
        return RReportDescriptor.TYPE;
    }

    public ScriptEngine getScriptEngine()
    {
        String extension = getDescriptor().getProperty(ScriptReportDescriptor.Prop.scriptExtension);
        ScriptEngineManager mgr = ServiceRegistry.get().getService(ScriptEngineManager.class);

        return mgr.getEngineByExtension(extension);
    }

    public String getTypeDescription()
    {
        ScriptEngine engine = getScriptEngine();

        if (engine != null)
        {
            return engine.getFactory().getLanguageName();
        }

        return "Script Engine Report";        
        //throw new RuntimeException("No Script Engine is available for this Report");
    }

    @Override
    public boolean supportsPipeline()
    {
        return true;
    }

    public Results generateResults(ViewContext context, boolean allowAsyncQuery) throws Exception
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

            if (null == rgn.getResultSet(ctx))
                return null;

            return new ResultsImpl(ctx);
        }

        return null;
    }

    protected boolean validateScript(String text, List<String> errors)
    {
        if (StringUtils.isEmpty(text))
        {
            errors.add("Empty script, a script must be provided.");
            return false;
        }

        return ParamReplacementSvc.get().isScriptWithValidReplacements(text, errors);
    }

    /*
     * Create the .tsv associated with the data grid for this report.
     */
    public File createInputDataFile(@NotNull ViewContext context) throws Exception
    {
        File resultFile = new File(getReportDir(context.getContainer().getId()), DATA_INPUT);

        if (context != null)
        {
            Results r = null;
            try
            {
                r = generateResults(context, true);
                if (r != null && r.getResultSet() != null)
                {
                    TSVGridWriter tsv = createGridWriter(r);
                    tsv.write(resultFile);
                }
            }
            finally
            {
                ResultSetUtil.close(r);
            }
        }

        return resultFile;
    }

    /**
     *
     * @param executingContainerId id of the container in which the report is running
     * @return directory, which has been created, to contain the generated report
     *
     * Note: This method used to stash results in members (_tempFolder and _tempFolderPipeline), but that no longer works
     * now that we cache reports between threads (e.g., Thread.currentThread().getId() is part of the path).
     */
    public File getReportDir(@NotNull String executingContainerId)
    {
        boolean isPipeline = BooleanUtils.toBoolean(getDescriptor().getProperty(ScriptReportDescriptor.Prop.runInBackground));
        return getReportDir(executingContainerId, isPipeline);
    }

    protected File getReportDir(@NotNull String executingContainerId, boolean isPipeline)
    {

        File tempRoot = getTempRoot(getDescriptor());
        String reportId = FileUtil.makeLegalName(String.valueOf(getDescriptor().getReportId())).replaceAll(" ", "_");

        File tempFolder;

        if (isPipeline)
        {
            String identifier = RReportJob.getJobIdentifier();
            if (identifier != null)
                tempFolder = new File(tempRoot.getAbsolutePath() + File.separator + executingContainerId + File.separator + "Report_" + reportId, identifier);
            else
                tempFolder = new File(tempRoot, executingContainerId + File.separator + "Report_" + reportId);
        }
        else
            tempFolder = new File(tempRoot.getAbsolutePath() + File.separator + executingContainerId + File.separator + "Report_" + reportId, String.valueOf(Thread.currentThread().getId()));

        if (!tempFolder.exists())
            tempFolder.mkdirs();

        return tempFolder;
    }

    public void deleteReportDir(@NotNull ContainerUser context)
    {
        boolean isPipeline = BooleanUtils.toBoolean(getDescriptor().getProperty(ScriptReportDescriptor.Prop.runInBackground));

        File dir = getReportDir(context.getContainer().getId());

        if (!isPipeline)
            dir = dir.getParentFile();

        FileUtil.deleteDir(dir);
    }

    /**
     * Invoked from a maintenance task, clean up temporary report files and folders that are of a
     * certain age.
     * @param log
     */
    public static void scheduledFileCleanup(Logger log)
    {
        final long cutoff = System.currentTimeMillis() - (1000 * 3600 * 24);
        File root = getTempRoot(ReportService.get().createDescriptorInstance(RReportDescriptor.TYPE));

        for (File file : root.listFiles())
        {
            if (file.isDirectory())
            {
                log.info("Deleting temporary report folder: " + file.getPath());
                deleteReportDir(file, cutoff);
            }
            else
            {
                // shouldn't be loose files here, so delete anyway
                file.delete();
            }
        }

        // now delete any downloadable files (images and pdf's) that are moved up into the temp folder
        ROutputView.cleanUpTemp(cutoff);
    }

    /**
     * Delete any thread specific subfolders if they are older than the
     * specified cutoff, and if there are no thread subfolders, delete the parent.
     * @param dir
     * @param cutoff
     */
    protected static void deleteReportDir(File dir, long cutoff)
    {
        if (dir.isDirectory())
        {
            boolean empty = true;
            for (File child : dir.listFiles())
            {
                if (child.lastModified() < cutoff)
                {
                    FileUtil.deleteDir(child);
                }
                else
                    empty = false;
            }
            // delete the parent if there are no subfolders
            if (empty)
                FileUtil.deleteDir(dir);
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

    protected TSVGridWriter createGridWriter(Results r) throws SQLException
    {
        ResultSetMetaData md = r.getMetaData();
        List<String> outputColumnNames = outputColumnNames(r);
        List<DisplayColumn> dataColumns = new ArrayList<>();

        for (int i = 0; i < md.getColumnCount(); i++)
        {
            int sqlColumn = i + 1;
            dataColumns.add(new NADisplayColumn(outputColumnNames.get(i), new ColumnInfo(md, sqlColumn)));
        }

        TSVGridWriter tsv = new TSVGridWriter(r, dataColumns);
        tsv.setColumnHeaderType(ColumnHeaderType.Name); // CONSIDER: Use FieldKey instead

        return tsv;
    }


    protected List<String> outputColumnNames(Results r) throws SQLException
    {
        assert null != r.getResultSet();
        CaseInsensitiveHashSet aliases = new CaseInsensitiveHashSet(); // output names
        Map<String, String> remap = new CaseInsensitiveHashMap<>();       // resultset name to output name
                
        // process the FieldKeys in order to be backward compatible
        for (Map.Entry<FieldKey,ColumnInfo> e : r.getFieldMap().entrySet())
        {
            ColumnInfo col = e.getValue();
            FieldKey fkey = e.getKey();
            assert fkey.equals(col.getFieldKey());

            String alias = oldLegalName(fkey);

            if (!aliases.add(alias))
            {
                int i;

                for (i=1; !aliases.add(alias+i); i++)
                    ;

                alias = alias + i;
            }

            remap.put(col.getAlias(), alias);
        }

        ResultSetMetaData md = r.getResultSet().getMetaData();
        ArrayList<String> ret = new ArrayList<>(md.getColumnCount());
        // now go through the resultset

        for (int col=1, count=md.getColumnCount(); col<=count; col++)
        {
            String name = md.getColumnName(col);
            String alias = remap.get(name);

            if (null != alias)
            {
                ret.add(alias);
                continue;
            }

            alias = ColumnInfo.propNameFromName(name).toLowerCase();

            if (!aliases.add(alias))
            {
                int i;
                for (i=1; !aliases.add(alias+i); i++)
                    ;
                alias = alias + i;
            }

            ret.add(alias);
        }

        return ret;
    }
    

    private String oldLegalName(FieldKey fkey)
    {
        String r = AliasManager.makeLegalName(StringUtils.join(fkey.getParts(),"_"), null, false, false);
        return ColumnInfo.propNameFromName(r).toLowerCase();
    }

    /**
     * used to render output parameters for script execution without rendering the output into HTML
     */
    public static List<ScriptOutput> renderParameters(ScriptEngineReport report, final List<ScriptOutput> scriptOutputs, List<ParamReplacement> parameters, boolean deleteTempFiles) throws IOException
    {
        return handleParameters(report, parameters, new ParameterHandler<List<ScriptOutput>>()
        {
            @Override
            public boolean handleParameter(ViewContext context, Report report, ParamReplacement param, List<String> sectionNames)
            {
                param.setReport(report);
                try
                {
                    for (File file : param.getFiles())
                    {
                        // Even if there is a script error we keep processing the output
                        // parameters.  If the parameter files don't exist then don't create a
                        // parameter object.
                        //
                        ScriptOutput output = param.renderAsScriptOutput(file);
                        if (null != output)
                        {
                            scriptOutputs.add(output);
                            _log.debug("ExecuteScript:  Added output parameter for: " + param.getName());
                        }
                        else
                        {
                            _log.debug("ExecuteScript:  Could not add output parameter for: " + param.getName());
                        }
                    }
                }
                catch(Exception e)
                {
                    throw new RuntimeException(e);
                }
                return true;
            }

            @Override
            public List<ScriptOutput> cleanup(ScriptEngineReport report, ContainerUser context)
            {
                if (report.shouldCleanup())
                    FileUtil.deleteDir(new File(report.getReportDir(context.getContainer().getId()).getAbsolutePath()));

                return scriptOutputs;
            }
        });
    }

    public static HttpView renderViews(ScriptEngineReport report, final VBox view, Collection<ParamReplacement> parameters, boolean deleteTempFiles) throws IOException
    {
        return handleParameters(report, parameters, new ParameterHandler<HttpView>()
        {
            @Override
            public boolean handleParameter(ViewContext context, Report report, ParamReplacement param, List<String> sectionNames)
            {
                // don't show headers if not all sections are being rendered
                if (!sectionNames.isEmpty())
                    param.setHeaderVisible(false);

                param.setReport(report);
                view.addView(param.render(context));

                return true;
            }

            @Override
            public HttpView cleanup(ScriptEngineReport report, ContainerUser context)
            {
                if (report.shouldCleanup())
                    view.addView(new TempFileCleanup(report.getReportDir(context.getContainer().getId()).getAbsolutePath()));

                return view;
            }
        });
    }

    public boolean shouldCleanup()
    {
        return !BooleanUtils.toBoolean(getDescriptor().getProperty(ScriptReportDescriptor.Prop.runInBackground));
    }


    public Thumbnail getThumbnail(List<ParamReplacement> parameters) throws IOException
    {
        return handleParameters(this, parameters, new ParameterHandler<Thumbnail>(){
            private Thumbnail _thumbnail = null;

            @Override
            public boolean handleParameter(ViewContext context, Report report, ParamReplacement param, List<String> sectionNames) throws IOException
            {
                _thumbnail = param.renderThumbnail(context);

                // Return true (keep iterating) if we can't render this output as a thumbnail
                return null == _thumbnail;
            }

            @Override
            public Thumbnail cleanup(ScriptEngineReport report, ContainerUser context)
            {
                // TODO: Delete file?
                return _thumbnail;
            }
        });
    }


    private static <K> K handleParameters(ScriptEngineReport report, Collection<ParamReplacement> parameters, ParameterHandler<K> handler) throws IOException
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
                boolean keepGoing = handler.handleParameter(context, report, param, sectionNames);

                if (!keepGoing)
                    break;
            }
        }

        return handler.cleanup(report, context);
    }


    private interface ParameterHandler<K>
    {
        boolean handleParameter(ViewContext context, Report report, ParamReplacement param, List<String> sectionNames) throws IOException;
        K cleanup(ScriptEngineReport report, ContainerUser context);
    }


    protected static boolean isViewable(ParamReplacement param, List<String> sectionNames)
    {
        for (File data : param.getFiles())
        {
            if (data.exists())
            {
                if (!sectionNames.isEmpty())
                    return sectionNames.contains(param.getName());
                return true;
            }
        }
        return false;
    }

    protected String createScript(ScriptEngine engine, ViewContext context, List<ParamReplacement> outputSubst, File inputDataTsv, Map<String, Object> inputParameters) throws Exception
    {
        return createScript(engine, context, outputSubst, inputDataTsv, inputParameters, false);
    }
    /**
     * Create the script to be executed by the scripting engine
     * @param outputSubst
     * @return
     * @throws Exception
     */
    protected String createScript(ScriptEngine engine, ViewContext context, List<ParamReplacement> outputSubst, File inputDataTsv, Map<String, Object> inputParameters, boolean isRStudio) throws Exception
    {
        return processScript(engine, context, getDescriptor().getProperty(ScriptReportDescriptor.Prop.script), inputDataTsv, outputSubst, inputParameters, true, isRStudio);
    }

    public abstract String runScript(ViewContext context, List<ParamReplacement> outputSubst, File inputDataTsv, Map<String, Object> inputParameters) throws ScriptException;


    protected String processScript(ScriptEngine engine, ViewContext context, String script, File inputFile, List<ParamReplacement> outputSubst, Map<String, Object> inputParameters, boolean includeProlog) throws Exception
    {
        return processScript(engine, context, script, inputFile, outputSubst, inputParameters, includeProlog, false);
    }    /**
     * Takes a script source, adds a prolog, processes any input and output replacement parameters
     * @param script
     * @param inputFile
     * @param outputSubst
     * @param inputParameters - client-passed params that get injected into the prolog of the report script
     * @throws Exception
     */
    protected String processScript(ScriptEngine engine, ViewContext context, String script, File inputFile, List<ParamReplacement> outputSubst, Map<String, Object> inputParameters, boolean includeProlog, boolean isRStudio) throws Exception
    {
         if (!StringUtils.isEmpty(script) && isRStudio)
            script = ParamReplacementSvc.get().transformInlineReplacements(script); // transform old inline syntax to comment syntax
        if (includeProlog && (!StringUtils.isEmpty(script) || isRStudio))
            script = concatScriptProlog(engine, context, script == null ? "" : script, inputFile, inputParameters, isRStudio);
        if (!StringUtils.isEmpty(script))
        {
            if (inputFile != null)
                script = processInputReplacement(engine, script, inputFile, isRStudio);
            script = processOutputReplacements(engine, script, outputSubst, context, isRStudio);
        }
        return script;
    }

    protected String getScriptProlog(ScriptEngine engine, ViewContext context, File inputFile, Map<String, Object> inputParameters)
    {
        return null;
    }

    protected String concatScriptProlog(ScriptEngine engine, ViewContext context, String script, File inputFile, Map<String, Object> inputParameters)
    {
        return concatScriptProlog(engine, context, script, inputFile, inputParameters, false);
    }

    protected String concatScriptProlog(ScriptEngine engine, ViewContext context, String script, File inputFile, Map<String, Object> inputParameters, boolean isRStudio)
    {
        return StringUtils.defaultString(getScriptProlog(engine, context, inputFile, inputParameters)) + script;
    }

    protected String processInputReplacement(ScriptEngine engine, String script, File inputFile, boolean isRStudio) throws Exception
    {
        return ParamReplacementSvc.get().processInputReplacement(script, INPUT_FILE_TSV, inputFile.getAbsolutePath().replaceAll("\\\\", "/"), isRStudio);
    }

    protected String processOutputReplacements(ScriptEngine engine, String script, List<ParamReplacement> replacements, @NotNull ContainerUser context, boolean isRStudio) throws Exception
    {
        return ParamReplacementSvc.get().processParamReplacement(script, getReportDir(context.getContainer().getId()), null, replacements, isRStudio);
    }

    @Override
    public void serializeToFolder(ImportContext ctx, VirtualFile directory) throws IOException
    {
        ReportDescriptor descriptor = getDescriptor();

        if (descriptor.getReportId() != null)
        {
            // for script based reports, write the script portion to a separate file to facilitate script modifications
            String scriptFileName = getSerializedScriptFileName(ctx);

            try (PrintWriter writer = directory.getPrintWriter(scriptFileName))
            {
                writer.write(descriptor.getProperty(ScriptReportDescriptor.Prop.script));
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
    protected String getSerializedScriptFileName(ImportContext context)
    {
        ScriptEngine engine = getScriptEngine();
        String extension = "script";
        String reportName;

        if (engine != null)
            extension = engine.getFactory().getExtensions().get(0);

        if (context != null)
        {
            ReportNameContext rnc = (ReportNameContext) context.getContext(ReportNameContext.class);
            reportName = rnc.getSerializedName();
        }
        else
        {
            reportName = ReportUtil.getSerializedName(getDescriptor());
        }

        return FileUtil.makeLegalName(String.format("%s.%s", reportName, extension));
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

    public static class NADisplayColumn extends DataColumn
    {
        public NADisplayColumn(ColumnInfo col)
        {
            super(col);
            this.setName(col.getPropertyName());
        }

        public NADisplayColumn(String name, ColumnInfo col)
        {
            super(col);
            this.setName(name);
        }

        @Override
        public String getName()
        {
            return _name;
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
