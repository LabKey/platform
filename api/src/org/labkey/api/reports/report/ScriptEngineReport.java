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
package org.labkey.api.reports.report;

import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.admin.FolderExportContext;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.Results;
import org.labkey.api.data.ResultsFactory;
import org.labkey.api.query.AliasManager;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.ValidationException;
import org.labkey.api.reports.LabKeyScriptEngineManager;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.ReportService;
import org.labkey.api.reports.report.r.ParamReplacement;
import org.labkey.api.reports.report.r.ParamReplacementSvc;
import org.labkey.api.reports.report.r.RReportDescriptor;
import org.labkey.api.reports.report.r.RReportJob;
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
import org.labkey.api.thumbnail.Thumbnail;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.VBox;
import org.labkey.api.view.ViewContext;
import org.labkey.api.writer.ContainerUser;

import javax.script.ScriptEngine;
import javax.script.ScriptException;
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
import java.util.regex.Pattern;

/**
 *
 * User: Karl Lum
 * Date: Dec 3, 2008
 * Time: 2:45:37 PM
 *
 * A Report implementation that uses a ScriptEngine instance to execute the associated script.
 *
 * NOTE: This tree of the ScriptReport hierarchy is a little funny.  There are three main branches below this class and
 * only one of them is actually uses a native javax.script.ScriptEngine.
 *
 *  1) InternalScriptEngineReport uses javax.script.ScriptEngine, but I'm not sure how to even create one of these...
 *  2) JavaScriptReport is a executes javascript code in the browser (so not a ScriptEngine)
 *  3) ExternalScriptEngineReport uses an external process to run the script, this process is wrapped in the javax.script.ScriptEngine interface (???)
 *
 *  See Also ScriptProcessReport which does not use javax.script.ScriptEngine.  It does however, try to use a lot of the
 *  patterns established here (and shared public static methods where possible).
*/
public abstract class ScriptEngineReport extends ScriptReport implements Report.ResultSetGenerator
{
    public static final String INPUT_FILE_TSV = "input_data";
    public static Pattern scriptPattern = Pattern.compile("\\$\\{(.*?)}");

    public static final String TYPE = "ReportService.scriptEngineReport";
    public static final String DATA_INPUT = "input_data.tsv";
    public static final String FILE_PREFIX = "rpt";
    public static final String SUBSTITUTION_MAP = "substitutionMap.txt";
    public static final String CONSOLE_OUTPUT = "console.txt";

    private static final Logger LOG = LogManager.getLogger(ScriptEngineReport.class);

    static
    {
        ParamReplacementSvc.get().registerHandler(new ConsoleOutput());
        ParamReplacementSvc.get().registerHandler(new TextOutput());
//        ParamReplacementSvc.get().registerHandler(new MarkdownOutput());
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

    @Override
    public String getType()
    {
        return TYPE;
    }

    @Override
    public String getDescriptorType()
    {
        return RReportDescriptor.TYPE;
    }

    public ScriptEngine getScriptEngine(Container c)
    {
        String extension = getDescriptor().getProperty(ScriptReportDescriptor.Prop.scriptExtension);
        LabKeyScriptEngineManager mgr = LabKeyScriptEngineManager.get();

        return mgr.getEngineByExtension(c, extension);
    }

    @Override
    public String getTypeDescription()
    {
        ScriptEngine engine = getScriptEngine(ContainerManager.getForId(getContainerId()));

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

    @Override
    public Results generateResults(ViewContext context, boolean allowAsyncQuery) throws Exception
    {
        return super._generateResults(context, allowAsyncQuery);
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
    public File createInputDataFile(@NotNull ViewContext context) throws SQLException, IOException, ValidationException
    {
        File resultFile = new File(getReportDir(context.getContainer().getId()), DATA_INPUT);
        ResultsFactory factory = ()-> {
            try
            {
                return generateResults(context, true);
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);
            }
        };
        return _createInputDataFile(context, factory, resultFile);
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

    @Override
    protected List<String> outputColumnNames(Results r)
    {
        assert null != r.getResultSet();
        CaseInsensitiveHashSet aliases = new CaseInsensitiveHashSet(); // output names
        Map<String, String> remap = new CaseInsensitiveHashMap<>();       // resultset name to output name

        // process the FieldKeys in order to be backward compatible
        for (Map.Entry<FieldKey, ColumnInfo> e : r.getFieldMap().entrySet())
        {
            ColumnInfo col = e.getValue();
            FieldKey fkey = e.getKey();
            assert fkey.equals(col.getFieldKey());

            String alias = oldLegalName(fkey);

            if (!aliases.add(alias))
            {
                int i;

                for (i = 1; !aliases.add(alias + i); i++)
                    ;

                alias = alias + i;
            }

            remap.put(col.getAlias(), alias);
        }

        try
        {
            ResultSetMetaData md = r.getResultSet().getMetaData();
            ArrayList<String> ret = new ArrayList<>(md.getColumnCount());
            // now go through the resultset

            for (int col = 1, count = md.getColumnCount(); col <= count; col++)
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
                            LOG.debug("ExecuteScript:  Added output parameter for: " + param.getName());
                        }
                        else
                        {
                            LOG.debug("ExecuteScript:  Could not add output parameter for: " + param.getName());
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

    public static HttpView<?> renderViews(ScriptEngineReport report, final VBox view, Collection<ParamReplacement> parameters, boolean deleteTempFiles) throws IOException
    {
        return handleParameters(report, parameters, new ParameterHandler<>()
        {
            @Override
            public boolean handleParameter(ViewContext context, Report report, ParamReplacement param, List<String> sectionNames)
            {
                // don't show headers if not all sections are being rendered
                if (!sectionNames.isEmpty())
                    param.setHeaderVisible(false);

                param.setReport(report);
                view.addView(param.getView(context));

                return true;
            }

            @Override
            public HttpView<?> cleanup(ScriptEngineReport report, ContainerUser context)
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
    }

    /**
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
            if (inputFile != null || isRStudio)
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

    protected String processInputReplacement(ScriptEngine engine, String script, @Nullable File inputFile, boolean isRStudio)
    {
        return ParamReplacementSvc.get().processInputReplacement(script, INPUT_FILE_TSV, inputFile == null ? null : inputFile.getAbsolutePath().replaceAll("\\\\", "/"), isRStudio, null);
    }

    protected String processOutputReplacements(ScriptEngine engine, String script, List<ParamReplacement> replacements, @NotNull ContainerUser context, boolean isRStudio) throws Exception
    {
        return ParamReplacementSvc.get().processParamReplacement(script, getReportDir(context.getContainer().getId()), null, replacements, isRStudio);
    }


    @Override
    public ScriptReportDescriptor getDescriptor()
    {
        return (ScriptReportDescriptor)super.getDescriptor();
    }


    @Override
    protected String getDefaultExtension(FolderExportContext context)
    {
        ScriptEngine engine = getScriptEngine(context.getContainer());
        if (engine != null)
            return engine.getFactory().getExtensions().get(0);
        return super.getDefaultExtension(context);
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

        @Override
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
        private final String _path;

        public TempFileCleanup(String path)
        {
            _path = path;
        }

        @Override
        protected void renderInternal(Object model, PrintWriter out)
        {
            FileUtil.deleteDir(new File(_path));
        }
    }
}
