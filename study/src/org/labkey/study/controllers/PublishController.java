package org.labkey.study.controllers;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.MutatingApiAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineStatusUrls;
import org.labkey.api.pipeline.PipelineValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.study.Study;
import org.labkey.api.assay.actions.AssayHeaderView;
import org.labkey.api.assay.actions.AssayRunsAction;
import org.labkey.api.assay.actions.BaseAssayAction;
import org.labkey.api.assay.actions.ProtocolIdForm;
import org.labkey.study.assay.PublishConfirmAction;
import org.labkey.study.assay.PublishStartAction;
import org.labkey.api.assay.AssayProvider;
import org.labkey.api.study.assay.AssayPublishService;
import org.labkey.api.assay.AssayService;
import org.labkey.api.assay.AssayUrls;
import org.labkey.api.util.ConfigurationException;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.HelpTopic;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.VBox;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.study.assay.AssayPublishManager;
import org.labkey.study.assay.query.AssayAuditProvider;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PublishController extends SpringActionController
{
    private static final ActionResolver _resolver = new DefaultActionResolver(PublishController.class,
        PublishStartAction.class,
        PublishConfirmAction.class
    );

    public PublishController()
    {
        setActionResolver(_resolver);
    }

    public static class PublishHistoryForm extends ProtocolIdForm
    {
        private String containerFilterName;

        public String getContainerFilterName()
        {
            return containerFilterName;
        }

        public void setContainerFilterName(String containerFilterName)
        {
            this.containerFilterName = containerFilterName;
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class PublishHistoryAction extends BaseAssayAction<PublishHistoryForm>
    {
        private ExpProtocol _protocol;

        @Override
        public ModelAndView getView(PublishHistoryForm form, BindException errors)
        {
            ContainerFilter containerFilter = ContainerFilter.CURRENT;
            if (form.getContainerFilterName() != null)
                containerFilter = ContainerFilter.getContainerFilterByName(form.getContainerFilterName(), getUser());

            _protocol = form.getProtocol();
            VBox view = new VBox();
            view.addView(new AssayHeaderView(_protocol, form.getProvider(), false, true, containerFilter));

            UserSchema schema = AuditLogService.getAuditLogSchema(getUser(), getContainer());

            if (schema != null)
            {
                QuerySettings settings = new QuerySettings(getViewContext(), QueryView.DATAREGIONNAME_DEFAULT);

                SimpleFilter filter = new SimpleFilter();
                if (_protocol.getRowId() != -1)
                    filter.addCondition(FieldKey.fromParts(AssayAuditProvider.COLUMN_NAME_PROTOCOL), _protocol.getRowId());
                filter.addCondition(containerFilter.createFilterClause(ExperimentService.get().getSchema(), FieldKey.fromParts(AssayAuditProvider.COLUMN_NAME_CONTAINER), getContainer()));

                settings.setBaseFilter(filter);
                settings.setQueryName(AssayAuditProvider.ASSAY_PUBLISH_AUDIT_EVENT);
                view.addView(schema.createView(getViewContext(), settings, errors));
            }
            setHelpTopic(new HelpTopic("publishHistory"));
            return view;
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            root.addChild("Assay List", PageFlowUtil.urlProvider(AssayUrls.class).getBeginURL(getContainer()));
            root.addChild(_protocol.getName(), new ActionURL(AssayRunsAction.class, getContainer()).addParameter("rowId", _protocol.getRowId()));
            root.addChild("Copy-to-Study History");
            return root;
        }
    }

    @RequiresPermission(InsertPermission.class)
    public class AutoCopyRunAction extends MutatingApiAction<AutoCopyRunForm>
    {
        @Override
        public Object execute(AutoCopyRunForm form, BindException errors) throws Exception
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            String jobGuid = startAutoCopyPipelineJob(form);
            Integer jobId = null;

            if (null != jobGuid)
                jobId = PipelineService.get().getJobId(getUser(), getContainer(), jobGuid);

            PipelineStatusUrls urls = PageFlowUtil.urlProvider(PipelineStatusUrls.class);
            ActionURL url  = null != jobId ? urls.urlDetails(getContainer(), jobId) : urls.urlBegin(getContainer());

            response.put("success", true);
            response.put("returnUrl", url);

            return response;
        }

        private String startAutoCopyPipelineJob(AutoCopyRunForm form)
        {
            Container c = getContainer();
            ViewBackgroundInfo vbi = new ViewBackgroundInfo(c, getUser(), null);
            PipeRoot root = PipelineService.get().findPipelineRoot(c);

            if (null == root)
                throw new ConfigurationException("Invalid pipeline configuration for " + c);

            if (!root.isValid())
                throw new ConfigurationException("Invalid pipeline configuration for " + c + ", " + root.getRootPath().getPath());

            try
            {
                PipelineJob job = new AutoCopyPipelineJob(vbi, root, form);
                PipelineService.get().queueJob(job);
                return job.getJobGUID();
            }
            catch (PipelineValidationException e)
            {
                throw new RuntimeException(e);
            }
        }

    }

    public static class AutoCopyRunForm extends ProtocolIdForm
    {
        private List<Integer> _runId;
        private Container _targetStudy;

        public Container getTargetStudy()
        {
            return _targetStudy;
        }

        public void setTargetStudy(Container targetStudy)
        {
            _targetStudy = targetStudy;
        }

        public List<Integer> getRunId()
        {
            return _runId;
        }

        public void setRunId(List<Integer> runId)
        {
            _runId = runId;
        }
    }

    private static class AutoCopyPipelineJob extends PipelineJob
    {
        private Container _targetStudyContainer;
        private Integer _protocolId;
        private List<Integer> _runIds = Collections.emptyList();
        private ActionURL _statusUrl;

        // For serialization
        protected AutoCopyPipelineJob() {}

        public AutoCopyPipelineJob(ViewBackgroundInfo info, @NotNull PipeRoot pipeRoot, AutoCopyRunForm form)
        {
            super(null, info, pipeRoot);
            _targetStudyContainer = form.getTargetStudy();
            _protocolId = form.getProtocol().getRowId();
            _runIds = form.getRunId();

            setLogFile(new File(pipeRoot.getRootPath(), FileUtil.makeFileNameWithTimestamp("auto_copy_to_study", "log")));
        }

        @Override
        public URLHelper getStatusHref()
        {
            return _statusUrl;
        }

        @Override
        public String getDescription()
        {
            return  "Automatic copying of assay data to study";
        }

        @Override
        public void run()
        {
            setStatus(TaskStatus.running);
            TaskStatus finalStatus = TaskStatus.complete;
            try
            {
                if (_targetStudyContainer != null)
                {
                    info("Starting copy of data to study in folder: " + _targetStudyContainer.getPath());
                    boolean hasPermission = false;
                    for (Study publishTarget : AssayPublishService.get().getValidPublishTargets(getUser(), InsertPermission.class))
                    {
                        if (publishTarget.getContainer().equals(_targetStudyContainer))
                        {
                            hasPermission = true;
                            break;
                        }
                    }

                    if (!hasPermission)
                    {
                        error("Insufficient permission to copy assay data to study in folder : " + _targetStudyContainer.getPath());
                    }
                    else
                    {
                        ExpProtocol protocol = ExperimentService.get().getExpProtocol(_protocolId);
                        AssayProvider provider = AssayService.get().getProvider(protocol);
                        _runIds.forEach((runId) -> {

                            info("Starting copy for run : " + runId);
                            ExpRun run = ExperimentService.get().getExpRun(runId);
                            if (run != null)
                            {
                                List<String> errors = new ArrayList<>();
                                _statusUrl = AssayPublishManager.getInstance().autoCopyResults(
                                        protocol,
                                        provider,
                                        run,
                                        getUser(),
                                        getContainer(),
                                        _targetStudyContainer,
                                        errors,
                                        getLogger());

                                errors.forEach(this::error);
                            }
                            else
                                error("Unable to locate run : " + runId);
                        });
                    }
                }
                else
                    error("Invalid target study folder");

            }
            catch (Throwable t)
            {
                error("Failure", t);
                finalStatus = TaskStatus.error;
            }
            info("Auto copy to study complete");
            setStatus(finalStatus);
        }
    }
}
