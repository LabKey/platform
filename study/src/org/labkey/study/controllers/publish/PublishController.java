package org.labkey.study.controllers.publish;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.MutatingApiAction;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.assay.AssayProvider;
import org.labkey.api.assay.AssayService;
import org.labkey.api.assay.AssayUrls;
import org.labkey.api.assay.actions.AssayHeaderView;
import org.labkey.api.assay.actions.AssayRunsAction;
import org.labkey.api.assay.actions.ProtocolIdForm;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExpSampleType;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.ExperimentUrls;
import org.labkey.api.exp.api.SampleTypeService;
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
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.ConfigurationException;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.VBox;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.study.assay.AssayPublishConfirmAction;
import org.labkey.study.assay.AssayPublishStartAction;
import org.labkey.study.assay.StudyPublishManager;
import org.labkey.study.assay.query.PublishAuditProvider;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PublishController extends SpringActionController
{
    private static final ActionResolver _resolver = new DefaultActionResolver(
            PublishController.class,
            AssayPublishStartAction.class,
            AssayPublishConfirmAction.class,
            SampleTypePublishStartAction.class,
            SampleTypePublishConfirmAction.class
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

    /**
     * Create a QueryView over the publish event audit log filtered by the source.
     */
    protected @Nullable QueryView createHistoryView(int sourceRowId, String sourceColumnName, ContainerFilter cf, BindException errors)
    {
        UserSchema schema = AuditLogService.getAuditLogSchema(getUser(), getContainer());
        if (schema == null)
            return null;

        QuerySettings settings = new QuerySettings(getViewContext(), QueryView.DATAREGIONNAME_DEFAULT);

        SimpleFilter filter = new SimpleFilter();
        if (sourceRowId != -1)
            filter.addCondition(FieldKey.fromParts(sourceColumnName), sourceRowId);
        filter.addCondition(cf.createFilterClause(ExperimentService.get().getSchema(), FieldKey.fromParts(PublishAuditProvider.COLUMN_NAME_CONTAINER)));

        settings.setBaseFilter(filter);
        settings.setQueryName(PublishAuditProvider.PUBLISH_AUDIT_EVENT);
        QueryView view = schema.createView(getViewContext(), settings, errors);
        view.setContainerFilter(cf);
        return view;
    }

    @RequiresPermission(ReadPermission.class)
    public class PublishAssayHistoryAction extends SimpleViewAction<PublishHistoryForm>
    {
        private ExpProtocol _protocol;

        @Override
        public ModelAndView getView(PublishHistoryForm form, BindException errors)
        {
            ContainerFilter containerFilter = ContainerFilter.current(getContainer());
            if (form.getContainerFilterName() != null)
                containerFilter = ContainerFilter.getContainerFilterByName(form.getContainerFilterName(), getContainer(), getUser());

            _protocol = form.getProtocol();
            VBox view = new VBox();
            view.addView(new AssayHeaderView(_protocol, form.getProvider(), false, true, containerFilter));

            QueryView historyView = createHistoryView(_protocol.getRowId(), PublishAuditProvider.COLUMN_NAME_PROTOCOL, containerFilter, errors);
            view.addView(historyView);

            setHelpTopic("publishHistory");
            return view;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild("Assay List", urlProvider(AssayUrls.class).getBeginURL(getContainer()));
            root.addChild(_protocol.getName(), new ActionURL(AssayRunsAction.class, getContainer()).addParameter("rowId", _protocol.getRowId()));
            root.addChild("Link to Study History");
        }
    }

    public static class SampleTypeHistoryForm
    {
        private int rowId;
        private String containerFilterName;

        public String getContainerFilterName()
        {
            return containerFilterName;
        }

        public void setContainerFilterName(String containerFilterName)
        {
            this.containerFilterName = containerFilterName;
        }

        public int getRowId()
        {
            return rowId;
        }

        public void setRowId(int rowId)
        {
            this.rowId = rowId;
        }

        /** @throws NotFoundException if we can't resolve the protocol */
        @NotNull ExpSampleType getSampleType(Container scope, User user)
        {
            if (rowId == -1)
                throw new NotFoundException("rowId required");

            ExpSampleType st = SampleTypeService.get().getSampleType(scope, user, rowId);
            if (st == null)
                throw new NotFoundException("SampleType not found: " + rowId);

            return st;
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class PublishSampleTypeHistoryAction extends SimpleViewAction<SampleTypeHistoryForm>
    {
        private ExpSampleType _sampleType;

        @Override
        public ModelAndView getView(SampleTypeHistoryForm form, BindException errors)
        {
            ContainerFilter containerFilter = ContainerFilter.current(getContainer());
            if (form.getContainerFilterName() != null)
                containerFilter = ContainerFilter.getContainerFilterByName(form.getContainerFilterName(), getContainer(), getUser());

            _sampleType = form.getSampleType(getContainer(), getUser());
            VBox view = new VBox();
            // CONSIDER: create a header component for the sample type?

            QueryView historyView = createHistoryView(_sampleType.getRowId(), PublishAuditProvider.COLUMN_NAME_SAMPLE_TYPE_ID, containerFilter, errors);
            view.addView(historyView);

            setHelpTopic("publishHistory");
            return view;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild("Sample Types", ExperimentUrls.get().getShowSampleTypeListURL(getContainer()));
            root.addChild(_sampleType.getName(), ExperimentUrls.get().getShowSampleTypeURL(_sampleType));
            root.addChild("Link to Study History");
        }
    }

    @RequiresPermission(InsertPermission.class)
    public class AutoLinkRunAction extends MutatingApiAction<AutoLinkRunForm>
    {
        @Override
        public Object execute(AutoLinkRunForm form, BindException errors) throws Exception
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            String jobGuid = startAutoLinkPipelineJob(form);
            Integer jobId = null;

            if (null != jobGuid)
                jobId = PipelineService.get().getJobId(getUser(), getContainer(), jobGuid);

            PipelineStatusUrls urls = urlProvider(PipelineStatusUrls.class);
            ActionURL url  = null != jobId ? urls.urlDetails(getContainer(), jobId) : urls.urlBegin(getContainer());

            response.put("success", true);
            response.put(ActionURL.Param.returnUrl.name(), url);

            return response;
        }

        private String startAutoLinkPipelineJob(AutoLinkRunForm form)
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
                PipelineJob job = new AutoLinkPipelineJob(vbi, root, form);
                PipelineService.get().queueJob(job);
                return job.getJobGUID();
            }
            catch (PipelineValidationException e)
            {
                throw new RuntimeException(e);
            }
        }

    }

    public static class AutoLinkRunForm extends ProtocolIdForm
    {
        private List<Integer> _runId;
        private Container _targetStudy;
        private String _autoLinkCategory;

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

        public String getAutoLinkCategory()
        {
            return _autoLinkCategory;
        }

        public void setAutoLinkCategory(String autoLinkCategory)
        {
            _autoLinkCategory = autoLinkCategory;
        }
    }

    private static class AutoLinkPipelineJob extends PipelineJob
    {
        private Container _targetStudyContainer;
        private Integer _protocolId;
        private List<Integer> _runIds = Collections.emptyList();
        private ActionURL _statusUrl;
        private String _autoLinkCategory;

        // For serialization
        protected AutoLinkPipelineJob() {}

        public AutoLinkPipelineJob(ViewBackgroundInfo info, @NotNull PipeRoot pipeRoot, AutoLinkRunForm form)
        {
            super(null, info, pipeRoot);
            _targetStudyContainer = form.getTargetStudy();
            _protocolId = form.getProtocol().getRowId();
            _runIds = form.getRunId();
            _autoLinkCategory = form.getAutoLinkCategory();

            setLogFile(new File(pipeRoot.getRootPath(), FileUtil.makeFileNameWithTimestamp("auto_link_to_study", "log")));
        }

        @Override
        public URLHelper getStatusHref()
        {
            return _statusUrl;
        }

        @Override
        public String getDescription()
        {
            return  "Automatic linkage of assay data to study";
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
                    info("Starting link of data to study in folder: " + _targetStudyContainer.getPath());

                    ExpProtocol protocol = ExperimentService.get().getExpProtocol(_protocolId);
                    AssayProvider provider = AssayService.get().getProvider(protocol);

                    _runIds.forEach((runId) -> {

                        info("Starting linkage for run : " + runId);
                        ExpRun run = ExperimentService.get().getExpRun(runId);
                        if (run != null)
                        {
                            List<String> errors = new ArrayList<>();
                            _statusUrl = StudyPublishManager.getInstance().autoLinkResults(
                                    protocol,
                                    provider,
                                    run,
                                    getUser(),
                                    getContainer(),
                                    _targetStudyContainer,
                                    _autoLinkCategory,
                                    errors,
                                    getLogger());

                            errors.forEach(this::error);
                        }
                        else
                            error("Unable to locate run : " + runId);
                    });
                }
                else
                    error("Invalid target study folder");
            }
            catch (Throwable t)
            {
                error("Failure", t);
                finalStatus = TaskStatus.error;
            }
            info("Auto link to study complete");
            setStatus(finalStatus);
        }
    }
}
