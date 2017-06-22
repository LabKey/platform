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
import org.labkey.api.action.FormHandlerAction;
import org.labkey.api.action.ReturnUrlForm;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.data.Container;
import org.labkey.api.data.DataRegionSelection;
import org.labkey.api.pipeline.AnalyzeForm;
import org.labkey.api.pipeline.ParamParser;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.PipelineProtocolFactory;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineStatusFile;
import org.labkey.api.pipeline.PipelineValidationException;
import org.labkey.api.pipeline.TaskFactory;
import org.labkey.api.pipeline.TaskId;
import org.labkey.api.pipeline.TaskPipeline;
import org.labkey.api.pipeline.file.AbstractFileAnalysisJob;
import org.labkey.api.pipeline.file.AbstractFileAnalysisProtocol;
import org.labkey.api.pipeline.file.AbstractFileAnalysisProtocolFactory;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.DotRunner;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.NetworkDrive;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.ViewForm;
import org.labkey.api.writer.ContainerUser;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    @RequiresPermission(InsertPermission.class)
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

                return new JspView<>("/org/labkey/pipeline/analysis/analyze.jsp", getViewContext().getActionURL());
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

    private static TaskPipeline getTaskPipeline(String taskIdString)
    {
        return PipelineJobService.get().getTaskPipeline(taskIdString);
    }

    private static AbstractFileAnalysisProtocolFactory getProtocolFactory(TaskPipeline taskPipeline)
    {
        return PipelineJobService.get().getProtocolFactory(taskPipeline);
    }

    /**
     * Called from LABKEY.Pipeline.startAnalysis()
     */
    @RequiresPermission(InsertPermission.class)
    public class StartAnalysisAction extends ApiAction<AnalyzeForm>
    {
        public ApiResponse execute(AnalyzeForm form, BindException errors)
        {
            try
            {
                String jobGUID = PipelineService.get().startFileAnalysis(form, null, getViewContext());
                Map<String, Object> resultProperties = new HashMap<>();
                resultProperties.put("status", "success");
                resultProperties.put("jobGUID", jobGUID);

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
    @RequiresPermission(ReadPermission.class)
    public class GetFileStatusAction extends ApiAction<AnalyzeForm>
    {
        public ApiResponse execute(AnalyzeForm form, BindException errors)
        {
            if (form.getProtocolName() == null || "".equals(form.getProtocolName()))
            {
                throw new NotFoundException("No protocol specified");
            }
            PipelineService.FileAnalysisProperties props = PipelineService.get().getFileAnalysisProperties(getContainer(), form.getTaskId(), form.getPath());
            AbstractFileAnalysisProtocol protocol = props.getFactory().getProtocol(props.getPipeRoot(), props.getDirData(), form.getProtocolName(), false);
            //NOTE: if protocol if null, initFileStatus() will return a result of UNKNOWN
            File dirAnalysis = props.getFactory().getAnalysisDir(props.getDirData(), form.getProtocolName(), props.getPipeRoot());
            form.initStatus(protocol, props.getDirData(), dirAnalysis);

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

    /**
     * Called from LABKEY.Pipeline.getProtocols().
     */
    @RequiresPermission(ReadPermission.class)
    public class GetSavedProtocolsAction extends ApiAction<AnalyzeForm>
    {
        public ApiResponse execute(AnalyzeForm form, BindException errors)
        {
            PipelineService.FileAnalysisProperties props = PipelineService.get().getFileAnalysisProperties(getContainer(), form.getTaskId(), form.getPath());
            JSONArray protocols = new JSONArray();
            for (String protocolName : props.getFactory().getProtocolNames(props.getPipeRoot(), props.getDirData(), false))
            {
                protocols.put(getProtocolJson(protocolName, props.getPipeRoot(), props.getDirData(), props.getFactory()));
            }

            if (form.getIncludeWorkbooks())
            {
                for (Container c : getContainer().getChildren())
                {
                    if (c.isWorkbook())
                    {
                        PipeRoot wbRoot = PipelineService.get().findPipelineRoot(c);
                        if (wbRoot == null || !wbRoot.isValid())
                            continue;

                        File wbDirData = null;
                        if (form.getPath() != null)
                        {
                            wbDirData = wbRoot.resolvePath(form.getPath());
                            if (wbDirData == null || !NetworkDrive.exists(wbDirData))
                                continue;
                        }

                        for (String protocolName : props.getFactory().getProtocolNames(wbRoot, wbDirData, false))
                        {
                            protocols.put(getProtocolJson(protocolName, wbRoot, wbDirData, props.getFactory()));
                        }
                    }
                }
            }

            JSONObject result = new JSONObject();
            result.put("protocols", protocols);
            result.put("defaultProtocolName", PipelineService.get().getLastProtocolSetting(props.getFactory(), getContainer(), getUser()));
            return new ApiSimpleResponse(result);
        }

        protected JSONObject getProtocolJson(String protocolName, PipeRoot root, File dirData, AbstractFileAnalysisProtocolFactory factory) throws NotFoundException
        {
            JSONObject protocol = new JSONObject();
            AbstractFileAnalysisProtocol pipelineProtocol = factory.getProtocol(root, dirData, protocolName, false);
            if (pipelineProtocol == null)
            {
                throw new NotFoundException("Protocol not found: " + protocolName);
            }

            protocol.put("name", protocolName);
            protocol.put("description", pipelineProtocol.getDescription());
            protocol.put("xmlParameters", pipelineProtocol.getXml());
            protocol.put("containerPath", root.getContainer().getPath());
            ParamParser parser = PipelineJobService.get().createParamParser();
            parser.parse(new ReaderInputStream(new StringReader(pipelineProtocol.getXml())));
            if (parser.getErrors() == null || parser.getErrors().length == 0)
            {
                protocol.put("jsonParameters", new JSONObject(parser.getInputParameters()));
            }

            return protocol;
        }
    }

    /**
     * For management of protocol files
     */
    public enum ProtocolTask
    {
        delete
            {
                @Override
                boolean doIt(PipeRoot root, PipelineProtocolFactory factory, String name)
                {
                    return factory.deleteProtocolFile(root, name);
                }
            },
        archive
            {
                @Override
                boolean doIt(PipeRoot root, PipelineProtocolFactory factory, String name)
                {
                    return factory.changeArchiveStatus(root, name, true);
                }
            },
        unarchive
            {
                @Override
                boolean doIt(PipeRoot root, PipelineProtocolFactory factory, String name)
                {
                    return factory.changeArchiveStatus(root, name, false);
                }
            };

        abstract boolean doIt(PipeRoot root, PipelineProtocolFactory factory, String name);

        String pastTense()
        {
            return this.toString() + "d";
        }

        boolean run(ContainerUser cu, Map<String, List<String>> selected)
        {
            PipeRoot root = PipelineService.get().findPipelineRoot(cu.getContainer());

            // selected is a map of taskId -> list of protocol names.
            // Find the correct factory for each taskId, then perform operation on list of names. Fail and return on first error
            return selected.entrySet().stream().allMatch( entry -> {
                PipelineProtocolFactory factory = getProtocolFactory(getTaskPipeline(entry.getKey()));
                return entry.getValue().stream().allMatch( name -> {
                    if (doIt(root, factory, name))
                    {
                        AuditLogService.get().addEvent(cu.getUser(),
                                new ProtocolManagementAuditProvider.ProtocolManagementEvent(ProtocolManagementAuditProvider.EVENT,
                                        cu.getContainer(), factory.getName(), name, this.pastTense()));
                        return true;
                    }
                    else
                        return false;
                });
            });
        }

        public static boolean isInEnum(String value) {
            return Arrays.stream(ProtocolTask.values()).anyMatch(e -> e.name().equals(value));
        }
    }

    @RequiresPermission(DeletePermission.class)
    public class ProtocolManagementAction extends FormHandlerAction<ProtocolManagementForm>
    {

        @Override
        public void validateCommand(ProtocolManagementForm form, Errors errors)
        {
            if (!ProtocolTask.isInEnum(form.getAction()))
                errors.reject("An invalid action was passed: " + form.getAction());
            try
            {
                form.getSelected();
            }
            catch (Exception e)
            {
                errors.reject("Invalid selection");
            }
        }

        @Override
        public boolean handlePost(ProtocolManagementForm form, BindException errors) throws Exception
        {
            try
            {
                return ProtocolTask.valueOf(form.getAction()).run(getViewContext(), form.getSelected());
            }
            catch (Exception e)
            {
                LOG.error("Error processing protocol management action.", e);
                errors.reject("Error processing action. See server log for more details.");
                return false;
            }
        }

        @Override
        public URLHelper getSuccessURL(ProtocolManagementForm form)
        {
            return form.getReturnActionURL();
        }


    }

    public static class ProtocolManagementForm extends ViewForm
    {
        String action;
        Map<String, List<String>> selected = null;
        String taskId = null;
        String name = null;

        public String getAction()
        {
            return action;
        }

        public void setAction(String action)
        {
            this.action = action;
        }

        public void setTaskId(String taskId)
        {
            this.taskId = taskId;
        }

        public void setName(String name)
        {
            this.name = name;
        }

        public Map<String, List<String>> getSelected()
        {
            if (selected == null)
            {
                if (null != taskId && null != name) // came in from the Details page, not the grid view
                {
                    selected = new HashMap<>();
                    selected.put(taskId, Collections.singletonList(name));
                }
                else
                {
                    selected = parseSelected(DataRegionSelection.getSelected(getViewContext(), true));
                }
            }
            return selected;
        }

        /**
         * The select set are comma separated pairs of taskId, protocol name
         * Split into a map of taskId -> list of names
         */
        private Map<String, List<String>> parseSelected(Set<String> selected)
        {
            Map<String, List<String>> parsedSelected = new HashMap<>();
            for (String pair : selected)
            {
                String[] split = pair.split(",", 2);
                if (split.length == 2) // silently ignore malformed input
                {
                    List<String> names = parsedSelected.get(split[0]);
                    if (null == names)
                    {
                        names = new ArrayList<>();
                        parsedSelected.put(split[0], names);
                    }
                    names.add(split[1]);
                }
            }
            return parsedSelected;
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class ProtocolDetailsAction extends SimpleViewAction<ProtocolDetailsForm>
    {
        private String _protocolName;

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Protocol: " + _protocolName);
        }

        @Override
        public ModelAndView getView(ProtocolDetailsForm form, BindException errors) throws Exception
        {
            _protocolName = form.getName();
            PipeRoot root = PipelineService.get().findPipelineRoot(getViewContext().getContainer());
            AbstractFileAnalysisProtocolFactory factory = getProtocolFactory(getTaskPipeline(form.getTaskId()));
            AbstractFileAnalysisProtocol protocol = factory.getProtocol(root, null, _protocolName, form.isArchived());
            if (null != protocol)
                form.setXml(protocol.getXml());
            return new JspView<>("/org/labkey/pipeline/analysis/protocolDetail.jsp", form);
        }
    }

    public static class ProtocolDetailsForm extends ReturnUrlForm
    {
        private String _taskId;
        private String _name;
        private boolean _archived;
        private String _xml = null;

        public String getTaskId()
        {
            return _taskId;
        }

        public void setTaskId(String taskId)
        {
            _taskId = taskId;
        }

        public String getName()
        {
            return _name;
        }

        public void setName(String name)
        {
            _name = name;
        }

        public boolean isArchived()
        {
            return _archived;
        }

        public void setArchived(boolean archived)
        {
            _archived = archived;
        }

        public String getXml()
        {
            return _xml;
        }

        public void setXml(String xml)
        {
            _xml = xml;
        }
    }

    @RequiresPermission(ReadPermission.class)
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
    @RequiresPermission(AdminPermission.class)
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
    @RequiresPermission(AdminPermission.class)
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
    @RequiresPermission(AdminPermission.class)
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
