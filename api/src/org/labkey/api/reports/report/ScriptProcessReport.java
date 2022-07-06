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
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.admin.FolderExportContext;
import org.labkey.api.data.Container;
import org.labkey.api.data.Results;
import org.labkey.api.data.ResultsFactory;
import org.labkey.api.query.ValidationException;
import org.labkey.api.reader.Readers;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.report.r.ParamReplacement;
import org.labkey.api.reports.report.r.ParamReplacementSvc;
import org.labkey.api.security.SessionApiKeyManager;
import org.labkey.api.security.User;
import org.labkey.api.thumbnail.Thumbnail;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;
import org.labkey.api.writer.ContainerUser;

import javax.script.ScriptEngine;
import javax.script.ScriptException;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.labkey.api.reports.report.ScriptEngineReport.DATA_INPUT;
import static org.labkey.api.reports.report.ScriptEngineReport.INPUT_FILE_TSV;

/**
 *
 * User: Matthew Bellew
 * Date: May 2022
 *
 * A Report implementation that uses an external process or script interpreter to execute the associated script.
 *
 *  See Also ScriptEngineReport.
 *
 *  NOTE: this code was forked from ScriptEngineReport, and removing code duplication between this class and
 *  ScriptEngineReport and ExternalScriptEngineReport is a work in progress.
*/
public abstract class ScriptProcessReport extends ScriptReport implements Report.ResultSetGenerator
{
    private static final Logger LOG = LogHelper.getLogger(ScriptProcessReport.class, "External script interpreter report");

    final String reportType;
    final String defaultDescriptorType;
    final ReportContext reportContext = new ReportContext();
    private File workingDirectory;


    /* this is where we gather the context that will be passed to the script runner as a json file */
    public static class ReportContext
    {
        User user;
        Container container;
        String apiKey;
        JSONObject queryConfig;
        JSONObject reportParameters = new JSONObject();

        public JSONObject toJSON()
        {
            return null;
        }

        public static ReportContext from(ViewContext vc, Report r)
        {
            ReportContext rc = new ReportContext();
            rc.user = vc.getUser();
            rc.container = vc.getContainer();
            rc.apiKey = SessionApiKeyManager.get().getApiKey(vc.getRequest(), r.getClass().getName());
            return rc;
        }
    }


    ScriptProcessReport(String reportType, String defaultDescriptorType)
    {
        this.reportType = reportType;
        this.defaultDescriptorType = defaultDescriptorType;
    }


    @Override
    public String getDescriptorType()
    {
        return defaultDescriptorType;
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
        if (null == workingDirectory)
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

            workingDirectory = tempFolder;
        }
        return workingDirectory;
    }


    public void deleteReportDir(@NotNull ContainerUser context)
    {
        boolean isPipeline = BooleanUtils.toBoolean(getDescriptor().getProperty(ScriptReportDescriptor.Prop.runInBackground));

        File dir = getReportDir(context.getContainer().getId());

        if (!isPipeline)
            dir = dir.getParentFile();

        FileUtil.deleteDir(dir);
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
            public Thumbnail cleanup(ScriptProcessReport report, ContainerUser context)
            {
                // TODO: Delete file?
                return _thumbnail;
            }
        });
    }


    private static <K> K handleParameters(ScriptProcessReport report, Collection<ParamReplacement> parameters, ParameterHandler<K> handler) throws IOException
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
        K cleanup(ScriptProcessReport report, ContainerUser context);
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
        return super.getDescriptor();
    }


    protected String getSerializedScriptFileName()
    {
        return getSerializedScriptFileName(null);
    }


    protected String getSerializedScriptFileName(FolderExportContext context)
    {
        String extension = "script";
        String reportName;

        ReportNameContext rnc = context.getContext(ReportNameContext.class);
        reportName = rnc.getSerializedName();

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

}
