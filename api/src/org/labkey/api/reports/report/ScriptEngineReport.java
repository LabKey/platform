/*
 * Copyright (c) 2008-2009 LabKey Corporation
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

import org.labkey.api.reports.Report;
import org.labkey.api.reports.report.view.ReportQueryView;
import org.labkey.api.reports.report.r.ParamReplacementSvc;
import org.labkey.api.reports.report.r.ParamReplacement;
import org.labkey.api.reports.report.r.view.*;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.DataView;
import org.labkey.api.view.VBox;
import org.labkey.api.query.*;
import org.labkey.api.data.*;
import org.labkey.api.util.FileUtil;
import org.labkey.api.writer.VirtualFile;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.BooleanUtils;
import org.apache.commons.collections15.map.CaseInsensitiveMap;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.ResultSetMetaData;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.*;

/**
 *
 * User: Karl Lum
 * Date: Dec 3, 2008
 * Time: 2:45:37 PM
 *
 * A Report implementation that uses a ScriptEngine instance to execute the associated script.
*/
public abstract class ScriptEngineReport extends AbstractReport implements Report.ResultSetGenerator
{
    public static final String INPUT_FILE_TSV = "input_data";
    public static Pattern scriptPattern = Pattern.compile("\\$\\{(.*?)\\}");

    public static final String TYPE = "ReportService.scriptEngineReport";
    public static final String DATA_INPUT = "input_data.tsv";
    public static final String REPORT_DIR = "reports_temp";
    public static final String FILE_PREFIX = "rpt";
    public static final String SUBSTITUTION_MAP = "substitutionMap.txt";
    public static final String CONSOLE_OUTPUT = "console.txt";

    private File _tempFolder;
    private boolean _tempFolderPipeline;

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

    public String getDescriptorType()
    {
        return RReportDescriptor.TYPE;
    }

    public ScriptEngine getScriptEngine()
    {
        String extension = getDescriptor().getProperty(RReportDescriptor.Prop.scriptExtension);
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

    /**
     * Create the query view used to generate the result set that this report operates on.
     */
    protected QueryView createQueryView(ViewContext context, ReportDescriptor descriptor) throws Exception
    {
        final String schemaName = descriptor.getProperty(QueryParam.schemaName.toString());
        final String queryName = descriptor.getProperty(QueryParam.queryName.toString());
        final String viewName = descriptor.getProperty(QueryParam.viewName.toString());
        final String dataRegionName = descriptor.getProperty(QueryParam.dataRegionName.toString());

        if (context != null && schemaName != null)
        {
            UserSchema base = (UserSchema) DefaultSchema.get(context.getUser(), context.getContainer()).getSchema(schemaName);
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

    public Results generateResults(ViewContext context) throws Exception
    {
        ReportDescriptor descriptor = getDescriptor();
        QueryView view = createQueryView(context, descriptor);
        if (view != null)
        {
            view.getSettings().setMaxRows(Table.ALL_ROWS);
            DataView dataView = view.createDataView();
            DataRegion rgn = dataView.getDataRegion();
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
            if (null == rgn.getResultSet(ctx))
                return null;
            return new Results(ctx);
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

    protected boolean isValidReplacement(String value)
    {
        if (INPUT_FILE_TSV.equals(value)) return true;

        return ParamReplacementSvc.get().getHandler(value) != null;
    }

    /*
     * Create the .tsv associated with the data grid for this report.
     */
    public File createInputDataFile(ViewContext context) throws Exception
    {
        File resultFile = new File(getReportDir(), DATA_INPUT);

        if (context != null)
        {
            Results r = generateResults(context);
            if (r != null && r.rs != null)
            {
                TSVGridWriter tsv = createGridWriter(r);
                tsv.write(resultFile);
            }
        }
        return resultFile;
    }

    public File getReportDir()
    {
        boolean isPipeline = BooleanUtils.toBoolean(getDescriptor().getProperty(RReportDescriptor.Prop.runInBackground));
        if (_tempFolder == null || _tempFolderPipeline != isPipeline)
        {
            File tempRoot = getTempRoot();
            String reportId = FileUtil.makeLegalName(String.valueOf(getDescriptor().getReportId())).replaceAll(" ", "_");
            if (isPipeline)
                _tempFolder = new File(tempRoot, "Report_" + reportId);
            else
                _tempFolder = new File(tempRoot.getAbsolutePath() + File.separator + "Report_" + reportId, String.valueOf(Thread.currentThread().getId()));

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


    protected TSVGridWriter createGridWriter(Results r) throws SQLException
    {
        ResultSet rs = r.rs;
        ResultSetMetaData md = rs.getMetaData();
        ColumnInfo cols[] = new ColumnInfo[md.getColumnCount()];
        List<String> outputColumnNames = outputColumnNames(r);
        List<DisplayColumn> dataColumns = new ArrayList<DisplayColumn>();
        for (int i = 0; i < cols.length; i++)
        {
            int sqlColumn = i + 1;
            dataColumns.add(new NADisplayColumn(outputColumnNames.get(i), new ColumnInfo(md, sqlColumn)));
        }
        TSVGridWriter tsv = new TSVGridWriter(rs, dataColumns);
        tsv.setColumnHeaderType(TSVGridWriter.ColumnHeaderType.propertyName);

        return tsv;
    }


    protected List<String> outputColumnNames(Results r) throws SQLException
    {
        assert null != r.rs;
        CaseInsensitiveHashSet aliases = new CaseInsensitiveHashSet(); // output names
        Map<String,String> remap = new CaseInsensitiveMap<String>();       // resultset name to output name
                
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
                for (i=1; !aliases.add(alias+i) ;i++)
                    ;
                alias = alias+i;
            }
            remap.put(col.getAlias(), alias);
        }

        ArrayList<String> ret = new ArrayList<String>(r.rs.getMetaData().getColumnCount());
        // now go through the resultset
        ResultSetMetaData md = r.rs.getMetaData();
        for (int col=1, count=md.getColumnCount() ; col<=count ; col++)
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
                for (i=1; !aliases.add(alias+i) ;i++)
                    ;
                alias = alias+i;
            }
            ret.add(alias);
        }
        return ret;
    }
    

    private String oldLegalName(FieldKey fkey)
    {
        String r = AliasManager.makeLegalName(StringUtils.join(fkey.getParts(),"_"), null, false);
//        if (r.length() > 40)
//            r = r.substring(0,40);
        return ColumnInfo.propNameFromName(r).toLowerCase();
    }


    public static void renderViews(ScriptEngineReport report, VBox view, List<ParamReplacement> parameters, boolean deleteTempFiles)
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

    protected static boolean isViewable(ParamReplacement param, List<String> sectionNames)
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
            script = StringUtils.defaultString(getScriptProlog(context, inputFile)) + script;

            script = processInputReplacement(script, inputFile);
            script = processOutputReplacements(script, outputSubst);
      }
        return script;
    }

    protected String getScriptProlog(ViewContext context, File inputFile)
    {
        return null;
    }

    protected String processInputReplacement(String script, File inputFile) throws Exception
    {
        return ParamReplacementSvc.get().processInputReplacement(script, INPUT_FILE_TSV, inputFile.getAbsolutePath().replaceAll("\\\\", "/"));
    }

    protected String processOutputReplacements(String script, List<ParamReplacement> replacements) throws Exception
    {
        return ParamReplacementSvc.get().processParamReplacement(script, getReportDir(), replacements);
    }

    @Override
    public void serializeToFolder(VirtualFile directory) throws IOException
    {
        ReportDescriptor descriptor = getDescriptor();
        if (descriptor.getReportId() != null)
        {
            // for script based reports, write the script portion to a separate file to facilitate script modifications
            String scriptFileName = getSerializedScriptFileName();
            PrintWriter writer = null;
            try
            {
                writer = directory.getPrintWriter(scriptFileName);
                writer.write(descriptor.getProperty(RReportDescriptor.Prop.script));
            }
            finally
            {
                if (writer != null)
                    writer.close();
            }

            super.serializeToFolder(directory);
        }
        else
            throw new IllegalArgumentException("Cannot serialize a report that hasn't been saved yet");
    }

    protected String getSerializedScriptFileName()
    {
        ScriptEngine engine = getScriptEngine();
        String extension = "script";
        ReportDescriptor descriptor = getDescriptor();

        if (engine != null)
            extension = engine.getFactory().getExtensions().get(0);

        if (descriptor.getReportId() != null)
            return FileUtil.makeLegalName(String.format("%s.%s.%s", descriptor.getReportName(), descriptor.getReportId(), extension));
        else
            return FileUtil.makeLegalName(String.format("%s.%s", descriptor.getReportName(), extension));
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
                BufferedReader br = null;

                try {
                    StringBuilder sb = new StringBuilder();
                    br = new BufferedReader(new FileReader(scriptFile));
                    String l;
                    while ((l = br.readLine()) != null)
                    {
                        sb.append(l);
                        sb.append('\n');
                    }
                    getDescriptor().setProperty(RReportDescriptor.Prop.script, sb.toString());
                }
                finally
                {
                    if (br != null)
                        try {br.close();} catch(IOException ioe) {}
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

/*
participantvisit_pre_1_pre1init
*/
