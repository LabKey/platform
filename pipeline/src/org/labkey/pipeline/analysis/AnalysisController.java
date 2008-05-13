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
package org.labkey.pipeline.analysis;

import org.apache.log4j.Logger;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.RedirectAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.data.Container;
import org.labkey.api.exp.pipeline.XarGeneratorId;
import org.labkey.api.jsp.FormPage;
import org.labkey.api.pipeline.*;
import org.labkey.api.pipeline.file.AbstractFileAnalysisJob;
import org.labkey.api.pipeline.file.FileAnalysisTaskPipeline;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.security.ACL;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.util.*;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.ViewForm;
import org.labkey.api.view.template.PageConfig;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * <code>AnalysisController</code>
 */
public class AnalysisController extends SpringActionController
{
    private static Logger _log = Logger.getLogger(AnalysisController.class);
    private static DefaultActionResolver _resolver = new DefaultActionResolver(AnalysisController.class);

    private static HelpTopic getHelpTopic(String topic)
    {
        return new HelpTopic(topic, HelpTopic.Area.SERVER);
    }

    public AnalysisController()
    {
        super();
        setActionResolver(_resolver);
    }

    public PageConfig defaultPageConfig()
    {
        PageConfig p = super.defaultPageConfig();
        p.setHelpTopic(getHelpTopic("pipeline-analysis"));
        return p;
    }

    public static ActionURL urlAnalyze(Container container, TaskId tid, String path)
    {
        return new ActionURL(AnalyzeAction.class, container)
                .addParameter(AnalyzeForm.Params.nsClass, tid.getNamespaceClass().getName())
                .addParameter(AnalyzeForm.Params.name, tid.getName())
                .addParameter(AnalyzeForm.Params.path, path);
    }

    @RequiresPermission(ACL.PERM_INSERT)    
    public class AnalyzeAction extends FormViewAction<AnalyzeForm>
    {
        private FileAnalysisTaskPipeline _taskPipeline;
        private File _dirRoot;
        private File _dirData;
        private File _dirAnalysis;
        private FileAnalysisPipelineProvider _provider;
        private FileAnalysisProtocol _protocol;

        public ActionURL getSuccessURL(AnalyzeForm form)
        {
            return PageFlowUtil.urlProvider(ProjectUrls.class).urlStart(getContainer());
        }

        public ModelAndView handleRequest(AnalyzeForm form, BindException errors) throws Exception
        {
            // HACK: Because Spring likes to split up single values if they are
            //       are set to an array.  Very lame for checkboxes.
            String name = AnalyzeForm.Params.fileInputNames.toString();
            form.setFileInputNames(getViewContext().getRequest().getParameterValues(name));
            
            TaskId taskId = form.getTaskId();
            if (taskId == null)
                return HttpView.throwNotFoundMV("The pipeline is not valid.");
            TaskPipeline pipeline = PipelineJobService.get().getTaskPipeline(taskId);
            if (pipeline == null)
                return HttpView.throwNotFoundMV("The pipeline '" + taskId + "' was not found.");
            if (!(pipeline instanceof FileAnalysisTaskPipeline))
                return HttpView.throwNotFoundMV("The pipeline '" + taskId + "' is not valid.");

            _taskPipeline = (FileAnalysisTaskPipeline) pipeline;

            PipeRoot pr = PipelineService.get().findPipelineRoot(getContainer());
            if (pr == null || !URIUtil.exists(pr.getUri()))
                return HttpView.throwNotFoundMV();

            URI uriRoot = pr.getUri();
            URI uriData = URIUtil.resolve(uriRoot, form.getPath());
            if (uriData == null)
                return HttpView.throwNotFoundMV();

            _dirRoot = new File(uriRoot);
            _dirData = new File(uriData);
            if (!NetworkDrive.exists(_dirData))
                return HttpView.throwNotFoundMV();

            _provider = (FileAnalysisPipelineProvider)
                    PipelineService.get().getPipelineProvider(FileAnalysisPipelineProvider.name);
            if (_provider == null)
                return HttpView.throwNotFoundMV();

            FileAnalysisProtocolFactory protocolFactory = _provider.getProtocolFactory(_taskPipeline);

            String protocolName = form.getProtocol();

            if (protocolName.length() != 0)
            {
                _dirAnalysis = protocolFactory.getAnalysisDir(_dirData, protocolName);

                try
                {
                    File protocolFile = protocolFactory.getParametersFile(_dirData, protocolName);
                    if (NetworkDrive.exists(protocolFile))
                    {
                        _protocol = protocolFactory.loadInstance(protocolFile);

                        // Don't allow the instance file to override the protocol name.
                        _protocol.setName(protocolName);
                    }
                    else
                    {
                        _protocol = protocolFactory.load(uriRoot, protocolName);
                    }

                    form.setProtocolName(_protocol.getName());
                    form.setProtocolDescription(_protocol.getDescription());
                    form.setConfigureXml(_protocol.getXml());
                }
                catch (IOException eio)
                {
                    errors.reject(ERROR_MSG, "Failed to load requested protocol.");
                }
            }

            return super.handleRequest(form, errors);
        }

        public void validateCommand(AnalyzeForm target, Errors errors)
        {
        }

        public boolean handlePost(AnalyzeForm form, BindException errors) throws Exception
        {
            if (!form.isRunAnalysis())
                return false;
            
            try
            {
                // If not a saved protocol, create one from the information in the form.
                if (!"".equals(form.getProtocol()))
                {
                    PipelineService.get().rememberLastProtocolSetting(_protocol.getFactory(),
                            getContainer(), getUser(), form.getProtocol());
                }
                else
                {
                    _protocol = _provider.getProtocolFactory(_taskPipeline).createProtocolInstance(
                            form.getProtocolName(),
                            form.getProtocolDescription(),
                            form.getConfigureXml());

                    _protocol.setEmail(getUser().getEmail());
                    _protocol.validateToSave(_dirRoot.toURI());
                    if (form.isSaveProtocol())
                    {
                        _protocol.saveDefinition(_dirRoot.toURI());
                        PipelineService.get().rememberLastProtocolSetting(_protocol.getFactory(),
                                getContainer(), getUser(), form.getProtocolName());
                    }
                }

                Container c = getContainer();
                /*  TODO: Running status
                File[] annotatedFiles = MS2PipelineManager.getAnalysisFiles(_dirData, _dirAnalysis, FileStatus.ANNOTATED, c);
                File[] unprocessedFile = MS2PipelineManager.getAnalysisFiles(_dirData, _dirAnalysis, FileStatus.UNKNOWN, c);
                List<File> mzXMLFileList = new ArrayList<File>();
                mzXMLFileList.addAll(Arrays.asList(annotatedFiles));
                mzXMLFileList.addAll(Arrays.asList(unprocessedFile));
                File[] mzXMLFiles = mzXMLFileList.toArray(new File[mzXMLFileList.size()]);
                if (mzXMLFiles.length == 0)
                    throw new IllegalArgumentException("Analysis for this protocol is already complete.");
                */
                _protocol.getFactory().ensureDefaultParameters(_dirRoot);

                File fileParameters = _protocol.getParametersFile(_dirData);
                // Make sure configure.xml file exists for the job when it runs.
                if (!fileParameters.exists())
                {
                    _protocol.setEmail(getUser().getEmail());
                    _protocol.saveInstance(fileParameters, getContainer());
                }

                ArrayList<File> filesInputList = new ArrayList<File>();
                for (String fileInputName : form.getFileInputNames())
                {
                    if (fileInputName == null)
                    {
                        errors.reject(ERROR_MSG, "Empty name found in file list.");
                        return false;
                    }
                    filesInputList.add(new File(_dirData, fileInputName));
                }
                File[] filesInput = filesInputList.toArray(new File[filesInputList.size()]);

                AbstractFileAnalysisJob job =
                        _protocol.createPipelineJob(getViewBackgroundInfo(), filesInput, fileParameters, false);

                if (filesInput.length == 1)
                    PipelineService.get().queueJob(job);
                else
                {
                    for (AbstractFileAnalysisJob jobSingle : job.getSingleFileJobs())
                            PipelineService.get().queueJob(jobSingle);
                }
            }
            catch (IllegalArgumentException e)
            {
                errors.reject(ERROR_MSG, e.getMessage());
                return false;
            }
            catch (PipelineProtocol.PipelineValidationException e)
            {
                errors.reject(ERROR_MSG, e.getMessage());
                return false;
            }
            catch (IOException e)
            {
                errors.reject(ERROR_MSG, "Failure attempting to write input parameters." + e.getMessage());
                return false;
            }

            return true;
        }

        public ModelAndView getView(AnalyzeForm form, boolean reshow, BindException errors) throws Exception
        {
            if (!reshow || "".equals(form.getProtocol()))
                form.setSaveProtocol(true);

            if (form.getConfigureXml().length() == 0)
            {
                form.setConfigureXml("<?xml version=\"1.0\"?>\n" +
                        "<bioml>\n" +
                        "<!-- Override default parameters here. -->\n" +
                        "</bioml>");
            }

            PipelineProtocolFactory factory = _provider.getProtocolFactory(_taskPipeline);
            String[] protocolNames = factory.getProtocolNames(_dirRoot.toURI());
            if (!reshow && "".equals(form.getProtocol()))
            {
                // If protocol is empty check for a saved protocol
                String protocolName = PipelineService.get().getLastProtocolSetting(factory,
                        getContainer(), getUser());
                if (protocolName != null && !"".equals(protocolName))
                {
                    // Make sure it is still around.
                    if (Arrays.asList(protocolNames).contains(protocolName))
                        form.setProtocol(protocolName);
                }
            }

            AnalyzePage page = (AnalyzePage) FormPage.get(AnalysisController.class, form, "analyze.jsp");

            page.setProtocolNames(protocolNames);

            return page.createView(errors);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Analyze Files");
        }
    }

    public static ActionURL urlImport(Container container, TaskId tid, String path)
    {
        return new ActionURL(ImportAction.class, container)
                .addParameter(AnalyzeForm.Params.nsClass, tid.getNamespaceClass().getName())
                .addParameter(AnalyzeForm.Params.name, tid.getName())
                .addParameter(AnalyzeForm.Params.path, path);
    }

    @RequiresPermission(ACL.PERM_INSERT)
    public class ImportAction extends RedirectAction<AnalyzeForm>
    {
        public ActionURL getSuccessURL(AnalyzeForm analyzeForm)
        {
            return PageFlowUtil.urlProvider(ProjectUrls.class).urlStart(getContainer());
        }

        public boolean throwNotFound()
        {
            HttpView.throwNotFound();
            return false;
        }

        public boolean throwNotFound(String message)
        {
            HttpView.throwNotFound(message);
            return false;
        }

        public void validateCommand(AnalyzeForm form, Errors errors)
        {
            // HACK: Because Spring likes to split up single values if they are
            //       are set to an array.  Very lame for checkboxes.
            String name = AnalyzeForm.Params.fileInputNames.toString();
            form.setFileInputNames(getViewContext().getRequest().getParameterValues(name));
        }

        public boolean doAction(AnalyzeForm form, BindException errors) throws Exception
        {
            TaskId taskId = form.getTaskId();
            if (taskId == null)
                return throwNotFound("The pipeline is not valid.");
            TaskPipeline pipeline = PipelineJobService.get().getTaskPipeline(taskId);
            if (pipeline == null)
                return throwNotFound("The pipeline '" + taskId + "' was not found.");
            if (!(pipeline instanceof FileAnalysisTaskPipeline))
                return throwNotFound("The pipeline '" + taskId + "' is not valid.");

            FileAnalysisTaskPipeline tp = (FileAnalysisTaskPipeline) pipeline;
            FileType[] initialTypes = tp.getInitialFileTypes();
            if (initialTypes.length == 0)
                return throwNotFound("The pipeline '" + taskId + "' has no initial file types.");

            TaskId taskIdGenerator = null;
            for (TaskId id : tp.getTaskProgression())
            {
                if (id.getNamespaceClass().equals(XarGeneratorId.class))
                {
                    taskIdGenerator = id;
                    break;
                }
            }

            if (taskIdGenerator == null)
                return throwNotFound("The pipeline '" + taskId + "' does not contain a valid XAR XML generator.");

            XarGeneratorId.Factory factoryGenerator = (XarGeneratorId.Factory)
                    PipelineJobService.get().getTaskFactory(taskIdGenerator);
            FileType ftGenInput = factoryGenerator.getInputType();
            if (ftGenInput == null)
                return throwNotFound("The pipeline '" + taskId + "' XAR generator input type not found.");                

            PipeRoot pr = PipelineService.get().findPipelineRoot(getContainer());
            if (pr == null || !URIUtil.exists(pr.getUri()))
                return throwNotFound("This folder does not have a pipeline root.");

            URI uriRoot = pr.getUri();
            URI uriAnalysis = URIUtil.resolve(uriRoot, form.getPath());
            if (uriAnalysis == null)
                return throwNotFound();

            File dirRoot = new File(uriRoot);
            File dirAnalysis = new File(uriAnalysis);
            if (!NetworkDrive.exists(dirAnalysis))
                return throwNotFound();

            FileAnalysisPipelineProvider provider = (FileAnalysisPipelineProvider)
                    PipelineService.get().getPipelineProvider(FileAnalysisPipelineProvider.name);
            if (provider == null)
                return throwNotFound();

            try
            {
                Container c = getContainer();

                FileAnalysisProtocolFactory factory = provider.getProtocolFactory(tp);
                factory.ensureDefaultParameters(dirRoot);

                File fileParameters = new File(dirAnalysis, factory.getParametersFileName());
                FileAnalysisProtocol protocol;
                if (!NetworkDrive.exists(fileParameters))
                {
                    protocol = factory.createProtocolInstance(
                        "import",
                        "Import data from unknown source.",
                        PipelineJobService.get().createParamParser().getXML());
                }
                else
                {
                    protocol = factory.loadInstance(fileParameters);
                }

                protocol.setEmail(getUser().getEmail());
                protocol.saveInstance(fileParameters, c);

                ArrayList<File> filesInputList = new ArrayList<File>();
                FileType ftInput = null;
                for (String fileInputName : form.getFileInputNames())
                {
                    if (fileInputName == null)
                    {
                        errors.reject(ERROR_MSG, "Empty name found in file list.");
                        return false;
                    }
                    File fileGenInput = new File(dirAnalysis, fileInputName);
                    String baseName = ftGenInput.getBaseName(fileGenInput);
                    if (ftInput == null)
                    {
                        for (FileType ftCheck : initialTypes)
                        {
                            File fileCheck = tp.findInputFile(dirRoot, dirAnalysis, ftCheck.getName(baseName));
                            if (fileCheck != null && NetworkDrive.exists(fileCheck))
                            {
                                ftInput = ftCheck;
                                break;
                            }
                        }

                        if (ftInput == null)
                            ftInput = initialTypes[0];
                    }
                    File fileInput = tp.findInputFile(dirRoot, dirAnalysis, ftInput.getName(baseName));
                    if (fileInput == null)
                        return throwNotFound("Failed to locate input file " + ftInput.getName(baseName) + ".");
                    filesInputList.add(fileInput);
                }
                File[] filesInput = filesInputList.toArray(new File[filesInputList.size()]);

                AbstractFileAnalysisJob job =
                        protocol.createPipelineJob(getViewBackgroundInfo(), filesInput, fileParameters, false);

                if (filesInput.length == 1)
                {
                    job.setActiveTaskId(taskIdGenerator);
                    PipelineService.get().queueJob(job);
                }
                else
                {
                    for (AbstractFileAnalysisJob jobSingle : job.getSingleFileJobs())
                    {
                        job.setActiveTaskId(taskIdGenerator);
                        PipelineService.get().queueJob(jobSingle);
                    }
                }
            }
            catch (IllegalArgumentException e)
            {
                errors.reject(ERROR_MSG, e.getMessage());
                return false;
            }
            catch (IOException e)
            {
                errors.reject(ERROR_MSG, "Failure attempting to write input parameters." + e.getMessage());
                return false;
            }

            return true;
        }
    }

    public static class AnalyzeForm extends ViewForm
    {
        public enum Params { path, nsClass, name, fileInputNames }

        private String path = "";
        private String nsClass = "";
        private String name = "";
        private String protocol = "";
        private String protocolName = "";
        private String protocolDescription = "";
        private String[] fileInputNames = new String[0];
        private String configureXml = "";
        private boolean saveProtocol = false;
        private boolean runAnalysis = false;

        public TaskId getTaskId()
        {
            try
            {
                return new TaskId(Class.forName(nsClass), name);
            }
            catch (ClassNotFoundException e)
            {
                return null;
            }
        }

        public String getPath()
        {
            return path;
        }

        public void setPath(String path)
        {
            this.path = (path == null ? "" : path);
        }

        public String getNsClass()
        {
            return nsClass;
        }

        public void setNsClass(String nsClass)
        {
            this.nsClass = nsClass;
        }

        public String getName()
        {
            return name;
        }

        public void setName(String name)
        {
            this.name = name;
        }

        public String getConfigureXml()
        {
            return configureXml;
        }

        public void setConfigureXml(String configureXml)
        {
            this.configureXml = (configureXml == null ? "" : configureXml);
        }

        public String getProtocol()
        {
            return protocol;
        }

        public void setProtocol(String protocol)
        {
            this.protocol = (protocol == null ? "" : protocol);
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

        public String[] getFileInputNames()
        {
            return fileInputNames;
        }

        public void setFileInputNames(String[] fileInputNames)
        {
            this.fileInputNames = fileInputNames;
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
    }
}
