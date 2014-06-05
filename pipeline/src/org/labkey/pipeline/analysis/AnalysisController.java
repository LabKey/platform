/*
 * Copyright (c) 2008-2014 LabKey Corporation
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
package org.labkey.pipeline.analysis;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.io.input.ReaderInputStream;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.action.ApiAction;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.ApiUsageException;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.data.Container;
import org.labkey.api.pipeline.ParamParser;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineStatusFile;
import org.labkey.api.pipeline.PipelineUrls;
import org.labkey.api.pipeline.PipelineValidationException;
import org.labkey.api.pipeline.TaskFactory;
import org.labkey.api.pipeline.TaskId;
import org.labkey.api.pipeline.TaskPipeline;
import org.labkey.api.pipeline.browse.PipelinePathForm;
import org.labkey.api.pipeline.file.AbstractFileAnalysisJob;
import org.labkey.api.pipeline.file.AbstractFileAnalysisProtocol;
import org.labkey.api.pipeline.file.AbstractFileAnalysisProtocolFactory;
import org.labkey.api.pipeline.file.AbstractFileAnalysisProvider;
import org.labkey.api.pipeline.file.FileAnalysisTaskPipeline;
import org.labkey.api.security.RequiresPermissionClass;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.DotRunner;
import org.labkey.api.util.FileType;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.NetworkDrive;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * <code>AnalysisController</code>
 */
public class AnalysisController extends SpringActionController
{
    private static DefaultActionResolver _resolver = new DefaultActionResolver(AnalysisController.class);
    private static final Logger LOG = Logger.getLogger(AnalysisController.class);

    public AnalysisController()
    {
        super();
        setActionResolver(_resolver);
    }

    public static ActionURL urlAnalyze(Container container, TaskId tid, String path)
    {
        return new ActionURL(AnalyzeAction.class, container)
                .addParameter(AnalyzeForm.Params.taskId, tid.toString())
                .addParameter(AnalyzeForm.Params.path, path);
    }

    @RequiresPermissionClass(InsertPermission.class)
    public class AnalyzeAction extends SimpleViewAction<AnalyzeForm>
    {
        private TaskPipeline _taskPipeline;

        public ModelAndView getView(AnalyzeForm analyzeForm, BindException errors) throws Exception
        {
            try
            {
                if (analyzeForm.getTaskId() == null || "".equals(analyzeForm.getTaskId()))
                    throw new NotFoundException("taskId required");

                _taskPipeline = PipelineJobService.get().getTaskPipeline(new TaskId(analyzeForm.getTaskId()));
                if (_taskPipeline == null)
                    throw new NotFoundException("Task pipeline not found: " + analyzeForm.getTaskId());

                return new JspView<>("/org/labkey/pipeline/analysis/analyze.jsp", PageFlowUtil.urlProvider(PipelineUrls.class).urlReferer(getContainer()));
            }
            catch (ClassNotFoundException e)
            {
                throw new NotFoundException("Could not find task pipeline: " + analyzeForm.getTaskId());
            }
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild(_taskPipeline.getDescription());
        }
    }

    private AbstractFileAnalysisProtocol getProtocol(PipeRoot root, File dirData, AbstractFileAnalysisProtocolFactory factory, String protocolName)
    {
        try
        {
            File protocolFile = factory.getParametersFile(dirData, protocolName, root);
            AbstractFileAnalysisProtocol result;
            if (NetworkDrive.exists(protocolFile))
            {
                result = factory.loadInstance(protocolFile);

                // Don't allow the instance file to override the protocol name.
                result.setName(protocolName);
            }
            else
            {
                result = factory.load(root, protocolName);
            }
            return result;
        }
        catch (IOException e)
        {
            return null;
        }
    }

    private TaskPipeline getTaskPipeline(String taskIdString)
    {
        try
        {
            TaskId taskId = new TaskId(taskIdString);
            TaskPipeline pipeline = PipelineJobService.get().getTaskPipeline(taskId);
            if (pipeline == null)
                throw new NotFoundException("The pipeline '" + taskId + "' was not found.");
            return pipeline;
        }
        catch (ClassNotFoundException e)
        {
            throw new NotFoundException("No pipeline found: " + e.getMessage());
        }
    }

    private AbstractFileAnalysisProtocolFactory getProtocolFactory(TaskPipeline taskPipeline)
    {
        AbstractFileAnalysisProvider provider = (AbstractFileAnalysisProvider)
                PipelineService.get().getPipelineProvider(FileAnalysisPipelineProvider.name);
        if (provider == null)
            throw new NotFoundException("No pipeline provider found for task pipeline: " + taskPipeline);

        if (!(taskPipeline instanceof FileAnalysisTaskPipeline))
            throw new NotFoundException("Task pipeline is not a FileAnalysisTaskPipeline: " + taskPipeline);

        FileAnalysisTaskPipeline fatp = (FileAnalysisTaskPipeline)taskPipeline;
        //noinspection unchecked
        return provider.getProtocolFactory(fatp);
    }

    /**
     * Called from LABKEY.Pipeline.startAnalysis()
     */
    @RequiresPermissionClass(InsertPermission.class)
    public class StartAnalysisAction extends AbstractAnalysisApiAction
    {
        protected ApiResponse execute(AnalyzeForm form, PipeRoot root, File dirData, AbstractFileAnalysisProtocolFactory factory) throws IOException, PipelineValidationException
        {
            try
            {
                TaskPipeline taskPipeline = getTaskPipeline(form.getTaskId());
                if (form.getProtocolName() == null)
                {
                    throw new IllegalArgumentException("Must specify a protocol name");
                }

                AbstractFileAnalysisProtocol protocol = getProtocol(root, dirData, factory, form.getProtocolName());
                if (protocol == null)
                {
                    String xml;
                    if (form.getConfigureXml() != null)
                    {
                        if (form.getConfigureJson() != null)
                        {
                            throw new IllegalArgumentException("The parameters should be defined as XML or JSON, not both");
                        }
                        xml = form.getConfigureXml();
                    }
                    else
                    {
                        if (form.getConfigureJson() == null)
                        {
                            throw new IllegalArgumentException("Parameters must be defined, either as XML or JSON");
                        }
                        ParamParser parser = PipelineJobService.get().createParamParser();
                        JSONObject o = new JSONObject(form.getConfigureJson());
                        Map<String, String> params = new HashMap<>();
                        for (Map.Entry<String, Object> entry : o.entrySet())
                        {
                            params.put(entry.getKey(), entry.getValue() == null ? null : entry.getValue().toString());
                        }
                        xml = parser.getXMLFromMap(params);
                    }

                    protocol = getProtocolFactory(taskPipeline).createProtocolInstance(
                            form.getProtocolName(),
                            form.getProtocolDescription(),
                            xml);

                    protocol.setEmail(getUser().getEmail());
                    protocol.validateToSave(root);
                    if (form.isSaveProtocol())
                    {
                        protocol.saveDefinition(root);
                        PipelineService.get().rememberLastProtocolSetting(protocol.getFactory(),
                                getContainer(), getUser(), protocol.getName());
                    }
                }
                else
                {
                    if (form.getConfigureXml() != null || form.getConfigureJson() != null)
                    {
                        throw new IllegalArgumentException("Cannot redefine an existing protocol");
                    }
                    PipelineService.get().rememberLastProtocolSetting(protocol.getFactory(),
                            getContainer(), getUser(), protocol.getName());
                }

                protocol.getFactory().ensureDefaultParameters(root);

                File fileParameters = protocol.getParametersFile(dirData, root);
                // Make sure configure.xml file exists for the job when it runs.
                if (fileParameters != null && !fileParameters.exists())
                {
                    protocol.setEmail(getUser().getEmail());
                    protocol.saveInstance(fileParameters, getContainer());
                }

                Boolean allowNonExistentFiles = form.isAllowNonExistentFiles() != null ? form.isAllowNonExistentFiles() : false;
                List<File> filesInputList = form.getValidatedFiles(getContainer(), allowNonExistentFiles);

                if (form.isActiveJobs())
                {
                    throw new IllegalArgumentException("Active jobs already exist for this protocol.");
                }

                AbstractFileAnalysisJob job =
                        protocol.createPipelineJob(getViewBackgroundInfo(), root, filesInputList, fileParameters);

                PipelineService.get().queueJob(job);

                Map<String, Object> resultProperties = new HashMap<>();

                resultProperties.put("status", "success");
                resultProperties.put("jobGUID", job.getJobGUID());

                return new ApiSimpleResponse(resultProperties);
            }
            catch (IOException | PipelineValidationException e)
            {
                throw new ApiUsageException(e);
            }
        }
    }

    /**
     * Called from LABKEY.Pipeline.getFileStatus().
     */
    @RequiresPermissionClass(ReadPermission.class)
    public class GetFileStatusAction extends AbstractAnalysisApiAction
    {
        protected ApiResponse execute(AnalyzeForm form, PipeRoot root, File dirData, AbstractFileAnalysisProtocolFactory factory)
        {
            if (form.getProtocolName() == null || "".equals(form.getProtocolName()))
            {
                throw new NotFoundException("No protocol specified");
            }
            AbstractFileAnalysisProtocol protocol = getProtocol(root, dirData, factory, form.getProtocolName());
            File dirAnalysis = factory.getAnalysisDir(dirData, form.getProtocolName(), root);
            form.initStatus(protocol, dirData, dirAnalysis);

            boolean isRetry = false;

            JSONArray files = new JSONArray();
            for (int i = 0; i < form.getFile().length; i++)
            {
                JSONObject o = new JSONObject();
                o.put("name", form.getFile()[i]);
                o.put("status", form.getFileInputStatus()[i]);
                isRetry |= form.getFileInputStatus()[i] != null;
                files.put(o);
            }
            JSONObject result = new JSONObject();
            result.put("files", files);
            if (!form.isActiveJobs())
            {
                result.put("submitType", isRetry ? "Retry" : "Analyze");
            }
            return new ApiSimpleResponse(result);
        }
    }

    public abstract class AbstractAnalysisApiAction extends ApiAction<AnalyzeForm>
    {
        protected abstract ApiResponse execute(AnalyzeForm form, PipeRoot root, File dirData, AbstractFileAnalysisProtocolFactory factory) throws IOException, PipelineValidationException;

        public ApiResponse execute(AnalyzeForm form, BindException errors) throws Exception
        {
            PipeRoot pr = PipelineService.get().findPipelineRoot(getContainer());
            if (pr == null || !pr.isValid())
                throw new NotFoundException();

            File dirData = null;
            if (form.getPath() != null)
            {
                dirData = pr.resolvePath(form.getPath());
                if (dirData == null || !NetworkDrive.exists(dirData))
                    throw new NotFoundException("Could not resolve path: " + form.getPath());
            }

            TaskPipeline taskPipeline = getTaskPipeline(form.getTaskId());
            AbstractFileAnalysisProtocolFactory factory = getProtocolFactory(taskPipeline);
            return execute(form, pr, dirData, factory);
        }
    }

    /**
     * Called from LABKEY.Pipeline.getProtocols().
     */
    @RequiresPermissionClass(ReadPermission.class)
    public class GetSavedProtocolsAction extends AbstractAnalysisApiAction
    {
        protected ApiResponse execute(AnalyzeForm form, PipeRoot root, File dirData, AbstractFileAnalysisProtocolFactory factory)
        {
            JSONArray protocols = new JSONArray();
            for (String protocolName : factory.getProtocolNames(root, dirData))
            {
                JSONObject protocol = new JSONObject();
                protocol.put("name", protocolName);
                AbstractFileAnalysisProtocol pipelineProtocol = getProtocol(root, dirData, factory, protocolName);
                protocol.put("description", pipelineProtocol.getDescription());
                protocol.put("xmlParameters", pipelineProtocol.getXml());
                ParamParser parser = PipelineJobService.get().createParamParser();
                parser.parse(new ReaderInputStream(new StringReader(pipelineProtocol.getXml())));
                if (parser.getErrors() == null || parser.getErrors().length == 0)
                {
                    protocol.put("jsonParameters", new JSONObject(parser.getInputParameters()));
                }
                protocols.put(protocol);
            }
            JSONObject result = new JSONObject();
            result.put("protocols", protocols);
            result.put("defaultProtocolName", PipelineService.get().getLastProtocolSetting(factory, getContainer(), getUser()));
            return new ApiSimpleResponse(result);
        }
    }

    public static class AnalyzeForm extends PipelinePathForm
    {
        public enum Params { path, taskId, file }

        private String taskId = "";
        private String protocolName = "";
        private String protocolDescription = "";
        private String[] fileInputStatus = null;
        private String configureXml;
        private String configureJson;
        private boolean saveProtocol = false;
        private boolean runAnalysis = false;
        private boolean activeJobs = false;
        private Boolean allowNonExistentFiles;

        private static final String UNKNOWN_STATUS = "UNKNOWN";

        public void initStatus(AbstractFileAnalysisProtocol protocol, File dirData, File dirAnalysis)
        {
            if (fileInputStatus != null)
                return;

            activeJobs = false;

            int len = getFile().length;
            fileInputStatus = new String[len + 1];
            for (int i = 0; i < len; i++)
                fileInputStatus[i] = initStatusFile(protocol, dirData, dirAnalysis, getFile()[i], true);
            fileInputStatus[len] = initStatusFile(protocol,  dirData, dirAnalysis, null, false);
        }

        private String initStatusFile(AbstractFileAnalysisProtocol protocol, File dirData, File dirAnalysis,
                                  String fileInputName, boolean statusSingle)
        {
            if (protocol == null)
            {
                return UNKNOWN_STATUS;
            }

            File fileStatus = null;

            if (!statusSingle)
            {
                fileStatus = PipelineJob.FT_LOG.newFile(dirAnalysis,
                        protocol.getJoinedBaseName());
            }
            else if (fileInputName != null)
            {
                File fileInput = new File(dirData, fileInputName);
                FileType ft = protocol.findInputType(fileInput);
                if (ft != null)
                    fileStatus = PipelineJob.FT_LOG.newFile(dirAnalysis, ft.getBaseName(fileInput));
            }

            if (fileStatus != null)
            {
                PipelineStatusFile sf = PipelineService.get().getStatusFile(fileStatus);
                if (sf == null)
                    return null;

                activeJobs = activeJobs || sf.isActive();
                return sf.getStatus();
            }

            // Failed to get status.  Assume job is active, and return unknown status.
            activeJobs = true;
            return UNKNOWN_STATUS;
        }

        public String getTaskId()
        {
            return taskId;
        }

        public void setTaskId(String taskId)
        {
            this.taskId = taskId;
        }

        public String getConfigureXml()
        {
            return configureXml;
        }

        public void setConfigureXml(String configureXml)
        {
            this.configureXml = (configureXml == null ? "" : configureXml);
        }

        public String getConfigureJson()
        {
            return configureJson;
        }

        public void setConfigureJson(String configureJson)
        {
            this.configureJson = configureJson;
        }

        public String getProtocolName()
        {
            return protocolName;
        }

        public void setProtocolName(String protocolName)
        {
            this.protocolName = (protocolName == null ? "" : protocolName);
        }

        public String getProtocolDescription()
        {
            return protocolDescription;
        }

        public void setProtocolDescription(String protocolDescription)
        {
            this.protocolDescription = (protocolDescription == null ? "" : protocolDescription);
        }

        public String[] getFileInputStatus()
        {
            return fileInputStatus;
        }

        public boolean isActiveJobs()
        {
            return activeJobs;
        }

        public boolean isSaveProtocol()
        {
            return saveProtocol;
        }

        public void setSaveProtocol(boolean saveProtocol)
        {
            this.saveProtocol = saveProtocol;
        }

        public boolean isRunAnalysis()
        {
            return runAnalysis;
        }

        public void setRunAnalysis(boolean runAnalysis)
        {
            this.runAnalysis = runAnalysis;
        }

        public Boolean isAllowNonExistentFiles()
        {
            return allowNonExistentFiles;
        }

        public void setAllowNonExistentFiles(Boolean allowNonExistentFiles)
        {
            this.allowNonExistentFiles = allowNonExistentFiles;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class FileNotificationAction extends ApiAction<Object>
    {
        @Override
        public ApiResponse execute(Object form, BindException errors) throws Exception
        {
            for (PipelineStatusFile statusFile : PipelineService.get().getJobsWaitingForFiles(getContainer()))
            {
                if (PipelineJob.TaskStatus.waitingForFiles.matches(statusFile.getStatus()) && statusFile.getJobStore() != null)
                {
                    PipelineJob pipelineJob = PipelineJobService.get().getJobStore().fromXML(statusFile.getJobStore());
                    if (pipelineJob instanceof AbstractFileAnalysisJob)
                    {
                        List<File> inputFiles = ((AbstractFileAnalysisJob) pipelineJob).getInputFiles();
                        boolean allFilesAvailable = !inputFiles.isEmpty();
                        for (File inputFile : inputFiles)
                        {
                            if (!NetworkDrive.exists(inputFile))
                            {
                                allFilesAvailable = false;
                                break;
                            }
                        }
                        if (allFilesAvailable)
                        {
                            PipelineService.get().queueJob(pipelineJob);
                        }
                    }
                }
            }
            return new ApiSimpleResponse();
        }
    }

    /**
     * Used for debugging task registration.
     */
    @RequiresPermissionClass(AdminPermission.class)
    public class InternalListTasksAction extends SimpleViewAction<Object>
    {
        @Override
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            return new JspView("/org/labkey/pipeline/analysis/internalListTasks.jsp", null, errors);
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Internal List Tasks");
        }
    }

    /**
     * Used for debugging pipeline registration.
     */
    @RequiresPermissionClass(AdminPermission.class)
    public class InternalListPipelinesAction extends SimpleViewAction<Object>
    {
        @Override
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            return new JspView("/org/labkey/pipeline/analysis/internalListPipelines.jsp", null, errors);
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Internal List Pipelines");
        }
    }

    public static class TaskForm
    {
        private String _taskId;

        public String getTaskId()
        {
            return _taskId;
        }

        public void setTaskId(String taskId)
        {
            _taskId = taskId;
        }
    }

    /**
     * Used for debugging task registration.
     */
    @RequiresPermissionClass(AdminPermission.class)
    public class InternalDetailsAction extends SimpleViewAction<TaskForm>
    {
        @Override
        public ModelAndView getView(TaskForm form, BindException errors) throws Exception
        {
            String id = form.getTaskId();
            if (id == null)
                throw new NotFoundException("taskId required");

            TaskFactory factory = null;
            TaskPipeline pipeline = null;

            Map<String, Object> map;
            TaskId taskId = TaskId.valueOf(id);
            if (taskId.getType() == TaskId.Type.task)
            {
                factory = PipelineJobService.get().getTaskFactory(taskId);
                map = BeanUtils.describe(factory);
            }
            else
            {
                pipeline = PipelineJobService.get().getTaskPipeline(taskId);
                map = BeanUtils.describe(pipeline);
            }

            StringBuilder sb = new StringBuilder();
            if (map.isEmpty())
            {
                sb.append("no task or pipeline found");
            }
            else
            {
                sb.append("<table>");
                for (Map.Entry<String, Object> entry : map.entrySet())
                {
                    sb.append("<tr>");
                    sb.append("<td>").append(PageFlowUtil.filter(entry.getKey())).append("</td>");
                    sb.append("<td>").append(PageFlowUtil.filter(entry.getValue())).append("</td>");
                    sb.append("</tr>");
                }
                sb.append("</table>");

                if (pipeline != null)
                {
                    String svg = generateGraph(pipeline);
                    if (svg != null)
                        sb.append(svg);
                }
            }

            return new HtmlView(sb.toString());

            //return new JspView("/org/labkey/pipeline/analysis/internalDetails.jsp", null, errors);
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Internal Details");
        }
    }

    private String generateGraph(TaskPipeline pipeline)
    {
        File svgFile = null;
        try
        {
            File dir = FileUtil.getTempDirectory();
            String dot = buildDigraph(pipeline);
            svgFile = File.createTempFile("pipeline", ".svg", dir);
            DotRunner runner = new DotRunner(dir, dot);
            runner.addSvgOutput(svgFile);
            runner.execute();
            return PageFlowUtil.getFileContentsAsString(svgFile);
        }
        catch (Exception e)
        {
            LOG.error("Error running dot", e);
        }
        finally
        {
            if (svgFile != null)
                svgFile.delete();
        }
        return null;
    }

    /**
     * Generate a dot graph of the pipeline.
     * Each task is drawn as a box with inputs on the left and outputs on the right:
     * <pre>
     * +--------------------+
     * |      task id       |
     * +---------+----------+
     * | in1.xls | out1.txt |
     * | in2.xls |          |
     * +---------+----------+
     * </pre>
     *
     * @param pipeline
     * @return
     */
    private String buildDigraph(TaskPipeline pipeline)
    {
        TaskId[] progression = pipeline.getTaskProgression();
        if (progression == null)
            return null;

        StringBuilder sb = new StringBuilder();
        sb.append("digraph pipeline {\n");

        // First, add all the nodes
        for (TaskId taskId : progression)
        {
            String name = taskId.getName();
            if (name == null)
                name = taskId.getNamespaceClass().getSimpleName();

            TaskFactory factory = PipelineJobService.get().getTaskFactory(taskId);

            if (factory == null)
            {
                // not found
                sb.append("\t\"").append(taskId.toString()).append("\"");
                sb.append(" [label=\"").append(name).append("\"");
                sb.append(" color=red");
                sb.append("];");
            }
            else
            {
                sb.append("\t\"").append(taskId.toString()).append("\"");
                sb.append(" [shape=record label=\"{");
                sb.append(name).append(" | {");

                // inputs
                // TODO: include parameters as inputs
                sb.append("{");
                if (factory instanceof CommandTaskImpl.Factory)
                {
                    CommandTaskImpl.Factory f = (CommandTaskImpl.Factory)factory;

                    sb.append(StringUtils.join(
                            Collections2.transform(f.getInputPaths().keySet(), new Function<String, Object>()
                            {
                                @Override
                                public Object apply(String input)
                                {
                                    return escapeDotFieldLabel(input) + "\\l";
                                }
                            }),
                            " | "));
                }
                else
                {
                    StringUtils.join(factory.getInputTypes(), " | ");
                }
                sb.append("}"); // end inputs

                sb.append(" | ");

                // outputs
                sb.append("{");
                if (factory instanceof CommandTaskImpl.Factory)
                {
                    CommandTaskImpl.Factory f = (CommandTaskImpl.Factory)factory;

                    sb.append(StringUtils.join(
                            Collections2.transform(f.getOutputPaths().keySet(), new Function<String, Object>()
                            {
                                @Override
                                public Object apply(String input)
                                {
                                    return escapeDotFieldLabel(input) + "\\r";
                                }
                            }),
                            " | "));
                }
                else
                {
                    // CONSIDER: can other tasks have outputs?
                }
                sb.append("}"); // end outputs

                sb.append("}"); // end body
                sb.append("}\""); // end label
                sb.append("];");
            }

            sb.append("\n\n");
        }

        sb.append("\n");

        // Now draw edges
        // For now, we draw just a sequence from a->b->c. Eventaully, we should connect outputs to inputs and draw splits/joins.
        sb.append("\t");
        sb.append(StringUtils.join(
                Collections2.transform(Arrays.asList(progression), new Function<TaskId, String>()
                {
                    @Override
                    public String apply(TaskId task)
                    {
                        return "\"" + task.toString() + "\"";
                    }
                }),
                " -> "));

        sb.append("}");
        return sb.toString();
    }

    // Escape a field within a dot record node:
    // - backslash escape [] {} <>
    // - spaces with '&#92;'
    private String escapeDotFieldLabel(String field)
    {
        field = field.replaceAll("[\\[\\]\\{\\}<>]", "\\\\$0");
        return field.replaceAll("\\s", "&#92;");
    }

}
