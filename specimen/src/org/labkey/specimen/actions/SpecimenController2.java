package org.labkey.specimen.actions;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.FormHandlerAction;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.HasViewContext;
import org.labkey.api.action.MutatingApiAction;
import org.labkey.api.action.ReadOnlyApiAction;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.attachments.AttachmentParent;
import org.labkey.api.attachments.BaseDownloadAction;
import org.labkey.api.data.ActionButton;
import org.labkey.api.data.BeanViewForm;
import org.labkey.api.data.Container;
import org.labkey.api.data.ExcelColumn;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.module.FolderType;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineStatusUrls;
import org.labkey.api.pipeline.browse.PipelinePathForm;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.RequiresSiteAdmin;
import org.labkey.api.security.User;
import org.labkey.api.security.ValidEmail;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.specimen.SpecimenManagerNew;
import org.labkey.api.specimen.SpecimenRequestManager;
import org.labkey.api.specimen.SpecimenSearchWebPart;
import org.labkey.api.specimen.Vial;
import org.labkey.api.specimen.actions.IdForm;
import org.labkey.api.specimen.actions.ManageReqsBean;
import org.labkey.api.specimen.actions.SpecimenEventBean;
import org.labkey.api.specimen.actions.ViewRequestsHeaderBean;
import org.labkey.api.specimen.importer.RequestabilityManager;
import org.labkey.api.specimen.importer.SimpleSpecimenImporter;
import org.labkey.api.specimen.model.SpecimenRequestEvent;
import org.labkey.api.specimen.pipeline.SpecimenArchive;
import org.labkey.api.specimen.query.SpecimenEventQueryView;
import org.labkey.api.specimen.query.SpecimenRequestQueryView;
import org.labkey.api.specimen.requirements.SpecimenRequestRequirement;
import org.labkey.api.specimen.requirements.SpecimenRequestRequirementProvider;
import org.labkey.api.specimen.requirements.SpecimenRequestRequirementType;
import org.labkey.api.specimen.security.permissions.ManageDisplaySettingsPermission;
import org.labkey.api.specimen.security.permissions.ManageNotificationsPermission;
import org.labkey.api.specimen.security.permissions.ManageRequestRequirementsPermission;
import org.labkey.api.specimen.security.permissions.ManageRequestSettingsPermission;
import org.labkey.api.specimen.security.permissions.RequestSpecimensPermission;
import org.labkey.api.specimen.settings.DisplaySettings;
import org.labkey.api.specimen.settings.RepositorySettings;
import org.labkey.api.specimen.settings.RequestNotificationSettings;
import org.labkey.api.specimen.settings.SettingsManager;
import org.labkey.api.specimen.view.SpecimenWebPart;
import org.labkey.api.study.MapArrayExcelWriter;
import org.labkey.api.study.SpecimenUrls;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.StudyUrls;
import org.labkey.api.study.security.permissions.ManageStudyPermission;
import org.labkey.api.util.HelpTopic;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.AjaxCompletion;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.RedirectException;
import org.labkey.api.view.VBox;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartView;
import org.labkey.specimen.pipeline.SpecimenBatch;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

// TEMPORARY: Move specimen actions from study SpecimenController to here. Once all actions are moved, we'll rename this.
public class SpecimenController2 extends SpringActionController
{
    private static final DefaultActionResolver _resolver = new DefaultActionResolver(
        SpecimenController2.class,

        ShowGroupMembersAction.class,
        ShowSearchAction.class,
        ShowUploadSpecimensAction.class,
        ShowUploadSpecimensAction.ImportCompleteAction.class,

        // Report actions from SpecimenReportActions
        SpecimenReportActions.ParticipantSummaryReportAction.class,
        SpecimenReportActions.ParticipantTypeReportAction.class,
        SpecimenReportActions.ParticipantSiteReportAction.class,
        SpecimenReportActions.RequestReportAction.class,
        SpecimenReportActions.RequestEnrollmentSiteReportAction.class,
        SpecimenReportActions.RequestSiteReportAction.class,
        SpecimenReportActions.RequestParticipantReportAction.class,
        SpecimenReportActions.TypeParticipantReportAction.class,
        SpecimenReportActions.TypeSummaryReportAction.class,
        SpecimenReportActions.TypeCohortReportAction.class
    );

    private Study _study = null;

    public SpecimenController2()
    {
        setActionResolver(_resolver);
    }

    @Nullable
    public Study getStudy()
    {
        if (null == _study)
            _study = StudyService.get().getStudy(getContainer());
        return _study;
    }

    @NotNull
    public Study getStudyThrowIfNull() throws IllegalStateException
    {
        Study study = StudyService.get().getStudy(getContainer());
        if (null == study)
        {
            // We expected to find a study
            throw new NotFoundException("No study found.");
        }
        return study;
    }

    @NotNull
    public Study getStudyRedirectIfNull()
    {
        Study study = StudyService.get().getStudy(getContainer());
        if (null == study)
        {
            // redirect to the study home page, where admins will see a 'create study' button,
            // and non-admins will simply see a message that no study exists.
            throw new RedirectException(urlProvider(StudyUrls.class).getBeginURL(getContainer()));
        }
        return study;
    }

    public void addRootNavTrail(NavTree root)
    {
        Study study = getStudyRedirectIfNull();
        Container c = getContainer();
        ActionURL rootURL;
        FolderType folderType = c.getFolderType();
        if ("study".equals(folderType.getDefaultModule().getName()))
        {
            rootURL = folderType.getStartURL(c, getUser());
        }
        else
        {
            rootURL = urlProvider(StudyUrls.class).getBeginURL(c);
        }
        root.addChild(study.getLabel(), rootURL);
    }

    private void addBaseSpecimenNavTrail(NavTree root)
    {
        addRootNavTrail(root);
        ActionURL overviewURL = new ActionURL(OverviewAction.class, getContainer());
        root.addChild("Specimen Overview", overviewURL);
    }

    private void addSpecimenRequestsNavTrail(NavTree root)
    {
        addBaseSpecimenNavTrail(root);
        root.addChild("Specimen Requests", new ActionURL(ViewRequestsAction.class, getContainer()));
    }

    private void addManageStudyNavTrail(NavTree root)
    {
        urlProvider(StudyUrls.class).addManageStudyNavTrail(root, getContainer(), getUser());
    }

    private ActionURL getManageStudyURL()
    {
        return urlProvider(StudyUrls.class).getManageStudyURL(getContainer());
    }

    public void ensureSpecimenRequestsConfigured(boolean checkExistingStatuses)
    {
        if (!SettingsManager.get().isSpecimenRequestEnabled(getContainer(), checkExistingStatuses))
            throw new RedirectException(new ActionURL(SpecimenRequestConfigRequiredAction.class, getContainer()));
    }

    @RequiresPermission(ReadPermission.class)
    public class OverviewAction extends SimpleViewAction
    {
        @Override
        public ModelAndView getView(Object form, BindException errors)
        {
            if (null == StudyService.get().getStudy(getContainer()))
                return new HtmlView("This folder does not contain a study.");
            SpecimenSearchWebPart specimenSearch = new SpecimenSearchWebPart(true);
            SpecimenWebPart specimenSummary = new SpecimenWebPart(true, StudyService.get().getStudy(getContainer()));
            return new VBox(specimenSummary, specimenSearch);
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            addBaseSpecimenNavTrail(root);
        }
    }

    public static class SpecimenWebPartForm
    {
        private String[] _grouping1;
        private String[] _grouping2;
        private String[] _columns;

        public String[] getGrouping1()
        {
            return _grouping1;
        }

        public void setGrouping1(String[] grouping1)
        {
            _grouping1 = grouping1;
        }

        public String[] getGrouping2()
        {
            return _grouping2;
        }

        public void setGrouping2(String[] grouping2)
        {
            _grouping2 = grouping2;
        }

        public String[] getColumns()
        {
            return _columns;
        }

        public void setColumns(String[] columns)
        {
            _columns = columns;
        }
    }

    @RequiresPermission(ManageDisplaySettingsPermission.class)
    public static class ManageSpecimenWebPartAction extends SimpleViewAction<SpecimenWebPartForm>
    {
        @Override
        public ModelAndView getView(SpecimenWebPartForm form, BindException errors)
        {
            RepositorySettings settings = SettingsManager.get().getRepositorySettings(getContainer());
            ArrayList<String[]> groupings = settings.getSpecimenWebPartGroupings();
            form.setGrouping1(groupings.get(0));
            form.setGrouping2(groupings.get(1));
            form.setColumns(SpecimenRequestManager.get().getGroupedValueAllowedColumns());
            return new JspView<>("/org/labkey/specimen/view/manageSpecimenWebPart.jsp", form);
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            setHelpTopic("manageSpecimens#group");
            urlProvider(StudyUrls.class).addManageStudyNavTrail(root, getContainer(), getUser());
            root.addChild("Configure Specimen Web Part");
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class SaveSpecimenWebPartSettingsAction extends MutatingApiAction<SpecimenWebPartForm>
    {
        @Override
        public ApiResponse execute(SpecimenWebPartForm form, BindException errors)
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            Study study = getStudy();
            if (study != null)
            {
                Container container = getContainer();
                RepositorySettings settings = SettingsManager.get().getRepositorySettings(container);
                ArrayList<String[]> groupings = new ArrayList<>(2);
                groupings.add(form.getGrouping1());
                groupings.add(form.getGrouping2());
                settings.setSpecimenWebPartGroupings(groupings);
                SettingsManager.get().saveRepositorySettings(container, settings);
                response.put("success", true);
                return response;
            }
            else
                throw new IllegalStateException("A study does not exist in this folder");
        }
    }

    @RequiresSiteAdmin
    public static class PivotAction extends SimpleViewAction<Object>
    {
        @Override
        public ModelAndView getView(Object o, BindException errors)
        {
            return new JspView<>("/org/labkey/specimen/view/pivot.jsp");
        }

        @Override
        public void addNavTrail(NavTree root)
        {
        }
    }

    public static class PipelineForm extends PipelinePathForm
    {
        private String replaceOrMerge = "replace";

        public String getReplaceOrMerge()
        {
            return replaceOrMerge;
        }

        public void setReplaceOrMerge(String replaceOrMerge)
        {
            this.replaceOrMerge = replaceOrMerge;
        }

        public boolean isMerge()
        {
            return "merge".equals(this.replaceOrMerge);
        }
    }

    public static void submitSpecimenBatch(Container c, User user, ActionURL url, File f, PipeRoot root, boolean merge) throws IOException
    {
        if (null == f || !f.exists() || !f.isFile())
            throw new NotFoundException();

        SpecimenBatch batch = new SpecimenBatch(new ViewBackgroundInfo(c, user, url), f, root, merge);
        batch.submit();
    }

    @RequiresPermission(AdminPermission.class)
    public class SubmitSpecimenBatchImportAction extends FormHandlerAction<PipelineForm>
    {
        @Override
        public void validateCommand(PipelineForm target, Errors errors)
        {
        }

        @Override
        public boolean handlePost(PipelineForm form, BindException errors) throws Exception
        {
            Container c = getContainer();
            PipeRoot root = PipelineService.get().findPipelineRoot(c);
            boolean first = true;
            for (File f : form.getValidatedFiles(c))
            {
                // Only possibly overwrite when the first archive is loaded:
                boolean merge = !first || form.isMerge();
                submitSpecimenBatch(c, getUser(), getViewContext().getActionURL(), f, root, merge);
                first = false;
            }
            return true;
        }

        @Override
        public ActionURL getSuccessURL(PipelineForm pipelineForm)
        {
            return urlProvider(PipelineStatusUrls.class).urlBegin(getContainer());
        }
    }

    /**
     * Legacy method hit via WGET/CURL to programmatically initiate a specimen import; no longer used by the UI,
     * but this method should be kept around until we receive verification that the URL is no longer being hit
     * programmatically.
     */
    @RequiresPermission(AdminPermission.class)
    public class SubmitSpecimenImport extends FormHandlerAction<PipelineForm>
    {
        @Override
        public void validateCommand(PipelineForm target, Errors errors)
        {
        }

        @Override
        public boolean handlePost(PipelineForm form, BindException errors) throws Exception
        {
            Container c = getContainer();
            String path = form.getPath();
            File f = null;

            PipeRoot root = PipelineService.get().findPipelineRoot(c);
            if (path != null)
            {
                if (root != null)
                    f = root.resolvePath(path);
            }

            submitSpecimenBatch(c, getUser(), getViewContext().getActionURL(), f, root, form.isMerge());
            return true;
        }

        @Override
        public ActionURL getSuccessURL(PipelineForm pipelineForm)
        {
            return urlProvider(PipelineStatusUrls.class).urlBegin(getContainer());
        }
    }


    public static class ImportSpecimensBean
    {
        private final String _path;
        private final List<SpecimenArchive> _archives;
        private final List<String> _errors;
        private final Container _container;
        private final String[] _files;

        private boolean noSpecimens = false;
        private boolean _defaultMerge = false;
        private boolean _isEditableSpecimens = false;

        public ImportSpecimensBean(Container container, List<SpecimenArchive> archives,
                                   String path, String[] files, List<String> errors)
        {
            _path = path;
            _files = files;
            _archives = archives;
            _errors = errors;
            _container = container;
        }

        public List<SpecimenArchive> getArchives()
        {
            return _archives;
        }

        public String getPath()
        {
            return _path;
        }

        public String[] getFiles()
        {
            return _files;
        }

        public List<String> getErrors()
        {
            return _errors;
        }

        public Container getContainer()
        {
            return _container;
        }

        public boolean isNoSpecimens()
        {
            return noSpecimens;
        }

        public void setNoSpecimens(boolean noSpecimens)
        {
            this.noSpecimens = noSpecimens;
        }

        public boolean isDefaultMerge()
        {
            return _defaultMerge;
        }

        public void setDefaultMerge(boolean defaultMerge)
        {
            _defaultMerge = defaultMerge;
        }

        public boolean isEditableSpecimens()
        {
            return _isEditableSpecimens;
        }

        public void setEditableSpecimens(boolean editableSpecimens)
        {
            _isEditableSpecimens = editableSpecimens;
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class ImportSpecimenDataAction extends SimpleViewAction<PipelineForm>
    {
        private String[] _filePaths = null;

        @Override
        public ModelAndView getView(PipelineForm form, BindException bindErrors)
        {
            List<File> dataFiles = form.getValidatedFiles(getContainer());
            List<SpecimenArchive> archives = new ArrayList<>();
            List<String> errors = new ArrayList<>();
            _filePaths = form.getFile();
            for (File dataFile : dataFiles)
            {
                if (null == dataFile || !dataFile.exists() || !dataFile.isFile())
                {
                    throw new NotFoundException();
                }

                if (!dataFile.canRead())
                    errors.add("Can't read data file: " + dataFile);

                SpecimenArchive archive = new SpecimenArchive(dataFile);
                archives.add(archive);
            }

            ImportSpecimensBean bean = new ImportSpecimensBean(getContainer(), archives, form.getPath(), form.getFile(), errors);
            boolean isEmpty = SpecimenManagerNew.get().isSpecimensEmpty(getContainer(), getUser());
            if (isEmpty)
            {
                bean.setNoSpecimens(true);
            }
            else if (SettingsManager.get().getRepositorySettings(getStudyThrowIfNull().getContainer()).isSpecimenDataEditable())
            {
                bean.setDefaultMerge(true);         // Repository is editable; make Merge the default
                bean.setEditableSpecimens(true);
            }

            return new JspView<>("/org/labkey/specimen/view/importSpecimens.jsp", bean);
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            String msg;
            if (_filePaths.length == 1)
                msg = _filePaths[0];
            else
                msg = _filePaths.length + " specimen archives";
            root.addChild("Import Study Batch - " + msg);
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class GetSpecimenExcelAction extends SimpleViewAction<Object>
    {
        @Override
        public ModelAndView getView(Object o, BindException errors)
        {
            List<Map<String,Object>> defaultSpecimens = new ArrayList<>();
            SimpleSpecimenImporter importer = new SimpleSpecimenImporter(getContainer(), getUser(),
                    getStudyRedirectIfNull().getTimepointType(), StudyService.get().getSubjectNounSingular(getContainer()));
            MapArrayExcelWriter xlWriter = new MapArrayExcelWriter(defaultSpecimens, importer.getSimpleSpecimenColumns());
            for (ExcelColumn col : xlWriter.getColumns())
                col.setCaption(importer.label(col.getName()));

            xlWriter.write(getViewContext().getResponse());

            return null;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
        }
    }

    static class SpecimenEventForm
    {
        private String _id;
        private Container _targetStudy;

        public String getId()
        {
            return _id;
        }

        public void setId(String id)
        {
            _id = id;
        }

        public Container getTargetStudy()
        {
            return _targetStudy;
        }

        public void setTargetStudy(Container targetStudy)
        {
            _targetStudy = targetStudy;
        }
    }

    @SuppressWarnings("unused") // Referenced in SpecimenForeignKey
    @RequiresPermission(ReadPermission.class)
    public static class SpecimenEventsRedirectAction extends SimpleViewAction<SpecimenEventForm>
    {
        @Override
        public ModelAndView getView(SpecimenEventForm form, BindException errors)
        {
            if (form.getId() != null && form.getTargetStudy() != null)
            {
                Vial vial = SpecimenManagerNew.get().getVial(form.getTargetStudy(), getUser(), form.getId());
                if (vial != null)
                {
                    ActionURL url = new ActionURL(SpecimenEventsAction.class, form.getTargetStudy()).addParameter("id", vial.getRowId());
                    throw new RedirectException(url);
                }
            }
            return new HtmlView("<span class='labkey-error'>Unable to resolve the Specimen ID and target Study</span>");
        }

        @Override
        public void addNavTrail(NavTree root)
        {
        }
    }

    public static class SpecimenEventAttachmentForm
    {
        private int _eventId;
        private String _name;

        public int getEventId()
        {
            return _eventId;
        }

        public void setEventId(int eventId)
        {
            _eventId = eventId;
        }

        public String getName()
        {
            return _name;
        }

        public void setName(String name)
        {
            _name = name;
        }
    }

    public static ActionURL getDownloadURL(SpecimenRequestEvent event, String name)
    {
        return new ActionURL(DownloadAction.class, event.getContainer())
            .addParameter("eventId", event.getRowId())
            .addParameter("name", name);
    }

    @RequiresPermission(ReadPermission.class)
    public static class DownloadAction extends BaseDownloadAction<SpecimenEventAttachmentForm>
    {
        @Override
        public @Nullable Pair<AttachmentParent, String> getAttachment(SpecimenEventAttachmentForm form)
        {
            SpecimenRequestEvent event = SpecimenRequestManager.get().getRequestEvent(getContainer(), form.getEventId());
            if (event == null)
                throw new NotFoundException("Specimen event not found");

            return new Pair<>(event, form.getName());
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class AutoReportListAction extends SimpleViewAction<Object>
    {
        @Override
        public ModelAndView getView(Object form, BindException errors)
        {
            return new JspView<>("/org/labkey/specimen/view/autoReportList.jsp", new ReportConfigurationBean(getViewContext()));
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            setHelpTopic("exploreSpecimens");
            addBaseSpecimenNavTrail(root);
            root.addChild("Specimen Reports");
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class SpecimenRequestConfigRequiredAction extends SimpleViewAction<Object>
    {
        @Override
        public ModelAndView getView(Object o, BindException errors)
        {
            return new JspView<>("/org/labkey/specimen/view/configurationRequired.jsp");
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            addRootNavTrail(root);
            root.addChild("Unable to Request Specimens");
        }
    }

    @RequiresPermission(ManageRequestSettingsPermission.class)
    public class ConfigureRequestabilityRulesAction extends SimpleViewAction<Object>
    {
        @Override
        public ModelAndView getView(Object o, BindException errors)
        {
            ensureSpecimenRequestsConfigured(false);
            return new JspView<>("/org/labkey/specimen/view/configRequestabilityRules.jsp");
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            setHelpTopic("coordinateSpecimens#requestability");
            addManageStudyNavTrail(root);
            root.addChild("Configure Requestability Rules");
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class ViewRequestsAction extends SimpleViewAction<Object>
    {
        @Override
        public ModelAndView getView(Object o, BindException errors)
        {
            requiresLogin();
            SpecimenRequestQueryView grid = SpecimenRequestQueryView.createView(getViewContext());
            grid.setExtraLinks(true);
            grid.setShowCustomizeLink(false);
            grid.setShowDetailsColumn(false);
            if (getContainer().hasPermission(getUser(), RequestSpecimensPermission.class))
            {
                ActionButton insertButton = new ActionButton(urlProvider(SpecimenUrls.class).getShowCreateSpecimenRequestAction(getContainer()), "Create New Request", ActionButton.Action.LINK);
                grid.setButtons(Collections.singletonList(insertButton));
            }
            else
            {
                grid.setButtons(Collections.emptyList());
            }

            JspView<ViewRequestsHeaderBean> header = new JspView<>("/org/labkey/specimen/view/viewRequestsHeader.jsp",
                    new ViewRequestsHeaderBean(getViewContext(), grid));

            return new VBox(header, grid);
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            addSpecimenRequestsNavTrail(root);
        }
    }

    public static class ViewEventForm extends IdForm
    {
        private boolean _selected;
        private boolean _vialView;

        public boolean isSelected()
        {
            return _selected;
        }

        public void setSelected(boolean selected)
        {
            _selected = selected;
        }

        public boolean isVialView()
        {
            return _vialView;
        }

        public void setVialView(boolean vialView)
        {
            _vialView = vialView;
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class SpecimenEventsAction extends SimpleViewAction<ViewEventForm>
    {
        private boolean _showingSelectedSpecimens;

        @Override
        public ModelAndView getView(ViewEventForm viewEventForm, BindException errors)
        {
            Study study = getStudyThrowIfNull();
            _showingSelectedSpecimens = viewEventForm.isSelected();
            Vial vial = SpecimenManagerNew.get().getVial(getContainer(), getUser(), viewEventForm.getId());
            if (vial == null)
                throw new NotFoundException("Specimen " + viewEventForm.getId() + " does not exist.");

            JspView<SpecimenEventBean> summaryView = new JspView<>("/org/labkey/study/view/specimen/specimen.jsp",
                    new SpecimenEventBean(vial, viewEventForm.getReturnUrl()));
            summaryView.setTitle("Vial Summary");

            SpecimenEventQueryView vialHistoryView = SpecimenEventQueryView.createView(getViewContext(), vial);
            vialHistoryView.setTitle("Vial History");

            VBox vbox;

            if (SettingsManager.get().getRepositorySettings(getStudyRedirectIfNull().getContainer()).isEnableRequests())
            {
                List<Integer> requestIds = SpecimenRequestManager.get().getRequestIdsForSpecimen(vial);
                SimpleFilter requestFilter;
                WebPartView relevantRequests;

                if (!requestIds.isEmpty())
                {
                    requestFilter = new SimpleFilter();
                    StringBuilder whereClause = new StringBuilder();
                    for (int i = 0; i < requestIds.size(); i++)
                    {
                        if (i > 0)
                            whereClause.append(" OR ");
                        whereClause.append("RequestId = ?");
                    }
                    requestFilter.addWhereClause(whereClause.toString(), requestIds.toArray());
                    SpecimenRequestQueryView queryView = SpecimenRequestQueryView.createView(getViewContext(), requestFilter);
                    queryView.setExtraLinks(true);
                    relevantRequests = queryView;
                }
                else
                    relevantRequests = new JspView("/org/labkey/specimen/view/relevantRequests.jsp");
                relevantRequests.setTitle("Relevant Vial Requests");
                vbox = new VBox(summaryView, vialHistoryView, relevantRequests);
            }
            else
            {
                vbox = new VBox(summaryView, vialHistoryView);
            }

            return vbox;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            addBaseSpecimenNavTrail(root);
            if (_showingSelectedSpecimens)
            {
                root.addChild("Selected Specimens", urlProvider(SpecimenUrls.class).getSpecimensURL(getContainer(), true));
            }
            root.addChild("Vial History");
        }
    }

    private boolean isNullOrBlank(String toCheck)
    {
        return ((toCheck == null) || toCheck.equals(""));
    }

    @RequiresPermission(ManageNotificationsPermission.class)
    public class ManageNotificationsAction extends FormViewAction<RequestNotificationSettings>
    {
        @Override
        public void validateCommand(RequestNotificationSettings form, Errors errors)
        {
            String replyTo = form.getReplyTo();
            if (replyTo == null || replyTo.length() == 0)
            {
                errors.reject(SpringActionController.ERROR_MSG, "Reply-to cannot be empty.");
            }
            else if (!RequestNotificationSettings.REPLY_TO_CURRENT_USER_VALUE.equals(replyTo))
            {
                try
                {
                    new ValidEmail(replyTo);
                }
                catch(ValidEmail.InvalidEmailException e)
                {
                    errors.reject(SpringActionController.ERROR_MSG, replyTo + " is not a valid email address.");
                }
            }

            String subjectSuffix = form.getSubjectSuffix();
            if (subjectSuffix == null || subjectSuffix.length() == 0)
            {
                errors.reject(SpringActionController.ERROR_MSG, "Subject suffix cannot be empty.");
            }

            try
            {
                form.getNewRequestNotifyAddresses();
            }
            catch (ValidEmail.InvalidEmailException e)
            {
                errors.reject(SpringActionController.ERROR_MSG, e.getBadEmail() + " is not a valid email address.");
            }

            try
            {
                form.getCCAddresses();
            }
            catch (ValidEmail.InvalidEmailException e)
            {
                errors.reject(SpringActionController.ERROR_MSG, e.getBadEmail() + " is not a valid email address.");
            }
        }

        @Override
        public ModelAndView getView(RequestNotificationSettings form, boolean reshow, BindException errors)
        {
            ensureSpecimenRequestsConfigured(false);

            // try to get the settings from the form, just in case this is a reshow:
            RequestNotificationSettings settings = form;
            if (settings == null || settings.getReplyTo() == null)
                settings = SettingsManager.get().getRequestNotificationSettings(getContainer());

            return new JspView<>("/org/labkey/specimen/view/manageNotifications.jsp", settings, errors);
        }

        @Override
        public boolean handlePost(RequestNotificationSettings settings, BindException errors)
        {
            ensureSpecimenRequestsConfigured(false);

            if (!settings.isNewRequestNotifyCheckbox())
                settings.setNewRequestNotify(null);
            else
            {
                if (isNullOrBlank(settings.getNewRequestNotify()))
                    errors.reject(ERROR_MSG, "New request notify is blank and send email is checked");
            }
            if (!settings.isCcCheckbox())
                settings.setCc(null);
            else
            {
                if (isNullOrBlank(settings.getCc()))
                    errors.reject(ERROR_MSG, "Always CC is blank and send email is checked");
            }
            if (errors.hasErrors())
                return false;

            SettingsManager.get().saveRequestNotificationSettings(getContainer(), settings);
            return true;
        }

        @Override
        public ActionURL getSuccessURL(RequestNotificationSettings form)
        {
            return getManageStudyURL();
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            setHelpTopic("coordinateSpecimens#notify");
            addManageStudyNavTrail(root);
            root.addChild("Manage Notifications");
        }
    }

    @RequiresPermission(ManageDisplaySettingsPermission.class)
    public class ManageDisplaySettingsAction extends FormViewAction<DisplaySettingsForm>
    {
        @Override
        public void validateCommand(DisplaySettingsForm target, Errors errors)
        {
        }

        @Override
        public ModelAndView getView(DisplaySettingsForm form, boolean reshow, BindException errors)
        {
            // try to get the settings from the form, just in case this is a reshow:
            DisplaySettings settings = form.getBean();
            if (settings == null || settings.getLastVialEnum() == null)
                settings = SettingsManager.get().getDisplaySettings(getContainer());

            return new JspView<>("/org/labkey/specimen/view/manageDisplay.jsp", settings);
        }

        @Override
        public boolean handlePost(DisplaySettingsForm form, BindException errors)
        {
            DisplaySettings settings = form.getBean();
            SettingsManager.get().saveDisplaySettings(getContainer(), settings);

            return true;
        }

        @Override
        public ActionURL getSuccessURL(DisplaySettingsForm displaySettingsForm)
        {
            return getManageStudyURL();
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            setHelpTopic(new HelpTopic("specimenRequest#display"));
            addManageStudyNavTrail(root);
            root.addChild("Manage Specimen Display Settings");
        }
    }

    public static class DisplaySettingsForm extends BeanViewForm<DisplaySettings>
    {
        public DisplaySettingsForm()
        {
            super(DisplaySettings.class);
        }
    }

    public static class UpdateRequestabilityRulesForm implements HasViewContext
    {
        private ViewContext _viewContext;
        private String[] _ruleType;
        private String[] _ruleData;
        private String[] _markType;

        @Override
        public ViewContext getViewContext()
        {
            return _viewContext;
        }

        @Override
        public void setViewContext(ViewContext viewContext)
        {
            _viewContext = viewContext;
        }

        public String[] getRuleType()
        {
            return _ruleType;
        }

        @SuppressWarnings("unused")
        public void setRuleType(String[] ruleType)
        {
            _ruleType = ruleType;
        }

        public String[] getRuleData()
        {
            return _ruleData;
        }

        @SuppressWarnings("unused")
        public void setRuleData(String[] ruleData)
        {
            _ruleData = ruleData;
        }

        public String[] getMarkType()
        {
            return _markType;
        }

        public void setMarkType(String[] markType)
        {
            _markType = markType;
        }
    }

    @RequiresPermission(ManageRequestSettingsPermission.class)
    public static class UpdateRequestabilityRulesAction extends MutatingApiAction<UpdateRequestabilityRulesForm>
    {
        @Override
        public ApiResponse execute(UpdateRequestabilityRulesForm form, BindException errors)
        {
            final List<RequestabilityManager.RequestableRule> rules = new ArrayList<>();
            for (int i = 0; i < form.getRuleType().length; i++)
            {
                String typeName = form.getRuleType()[i];
                RequestabilityManager.RuleType type = RequestabilityManager.RuleType.valueOf(typeName);
                String dataString = form.getRuleData()[i];
                rules.add(type.createRule(getContainer(), dataString));
            }
            RequestabilityManager.getInstance().saveRules(getContainer(), getUser(), rules);

            return new ApiSimpleResponse(Collections.<String, Object>singletonMap("savedCount", rules.size()));
        }
    }

    @RequiresPermission(ManageStudyPermission.class)
    public class ManageRepositorySettingsAction extends FormViewAction<ManageRepositorySettingsForm>
    {
        @Override
        public ModelAndView getView(ManageRepositorySettingsForm from, boolean reshow, BindException errors)
        {
            return new JspView<>("/org/labkey/specimen/view/manageRepositorySettings.jsp", SettingsManager.get().getRepositorySettings(getContainer()));
        }

        @Override
        public void validateCommand(ManageRepositorySettingsForm form, Errors errors)
        {
        }

        @Override
        public boolean handlePost(ManageRepositorySettingsForm form, BindException errors) throws Exception
        {
            RepositorySettings settings = SettingsManager.get().getRepositorySettings(getContainer());
            settings.setSimple(form.isSimple());
            settings.setEnableRequests(!form.isSimple() && form.isEnableRequests());
            settings.setSpecimenDataEditable(!form.isSimple() && form.isSpecimenDataEditable());
            SettingsManager.get().saveRepositorySettings(getContainer(), settings);

            return true;
        }

        @Override
        public URLHelper getSuccessURL(ManageRepositorySettingsForm manageRepositorySettingsForm)
        {
            return getManageStudyURL();
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            setHelpTopic("specimenAdminTutorial");
            addManageStudyNavTrail(root);
            root.addChild("Manage Repository Settings");
        }
    }

    public static class ManageRepositorySettingsForm
    {
        private boolean _simple;
        private boolean _enableRequests;
        private boolean _specimenDataEditable;

        public boolean isSimple()
        {
            return _simple;
        }

        public void setSimple(boolean simple)
        {
            _simple = simple;
        }

        public boolean isEnableRequests()
        {
            return _enableRequests;
        }

        public void setEnableRequests(boolean enableRequests)
        {
            _enableRequests = enableRequests;
        }

        public boolean isSpecimenDataEditable()
        {
            return _specimenDataEditable;
        }

        public void setSpecimenDataEditable(boolean specimenDataEditable)
        {
            _specimenDataEditable = specimenDataEditable;
        }
    }

    public static class DefaultRequirementsForm
    {
        private int _originatorActor;
        private String _originatorDescription;
        private int _providerActor;
        private String _providerDescription;
        private int _receiverActor;
        private String _receiverDescription;
        private int _generalActor;
        private String _generalDescription;
        private String _nextPage;

        public int getGeneralActor()
        {
            return _generalActor;
        }

        @SuppressWarnings("unused")
        public void setGeneralActor(int generalActor)
        {
            _generalActor = generalActor;
        }

        public String getGeneralDescription()
        {
            return _generalDescription;
        }

        @SuppressWarnings("unused")
        public void setGeneralDescription(String generalDescription)
        {
            _generalDescription = generalDescription;
        }

        public int getProviderActor()
        {
            return _providerActor;
        }

        @SuppressWarnings("unused")
        public void setProviderActor(int providerActor)
        {
            _providerActor = providerActor;
        }

        public String getProviderDescription()
        {
            return _providerDescription;
        }

        @SuppressWarnings("unused")
        public void setProviderDescription(String providerDescription)
        {
            _providerDescription = providerDescription;
        }

        public int getReceiverActor()
        {
            return _receiverActor;
        }

        @SuppressWarnings("unused")
        public void setReceiverActor(int receiverActor)
        {
            _receiverActor = receiverActor;
        }

        public String getReceiverDescription()
        {
            return _receiverDescription;
        }

        @SuppressWarnings("unused")
        public void setReceiverDescription(String receiverDescription)
        {
            _receiverDescription = receiverDescription;
        }

        public int getOriginatorActor()
        {
            return _originatorActor;
        }

        @SuppressWarnings("unused")
        public void setOriginatorActor(int originatorActor)
        {
            _originatorActor = originatorActor;
        }

        public String getOriginatorDescription()
        {
            return _originatorDescription;
        }

        @SuppressWarnings("unused")
        public void setOriginatorDescription(String originatorDescription)
        {
            _originatorDescription = originatorDescription;
        }

        public String getNextPage()
        {
            return _nextPage;
        }

        @SuppressWarnings("unused")
        public void setNextPage(String nextPage)
        {
            _nextPage = nextPage;
        }
    }

    @RequiresPermission(ManageRequestRequirementsPermission.class)
    public class ManageDefaultReqsAction extends FormViewAction<DefaultRequirementsForm>
    {
        @Override
        public void validateCommand(DefaultRequirementsForm target, Errors errors)
        {
        }

        @Override
        public ModelAndView getView(DefaultRequirementsForm defaultRequirementsForm, boolean reshow, BindException errors)
        {
            ensureSpecimenRequestsConfigured(true);
            return new JspView<>("/org/labkey/specimen/view/manageDefaultReqs.jsp",
                    new ManageReqsBean(getUser(), getContainer()));
        }

        @Override
        public boolean handlePost(DefaultRequirementsForm form, BindException errors)
        {
            ensureSpecimenRequestsConfigured(true);
            createDefaultRequirement(form.getOriginatorActor(), form.getOriginatorDescription(), SpecimenRequestRequirementType.ORIGINATING_SITE);
            createDefaultRequirement(form.getProviderActor(), form.getProviderDescription(), SpecimenRequestRequirementType.PROVIDING_SITE);
            createDefaultRequirement(form.getReceiverActor(), form.getReceiverDescription(), SpecimenRequestRequirementType.RECEIVING_SITE);
            createDefaultRequirement(form.getGeneralActor(), form.getGeneralDescription(), SpecimenRequestRequirementType.NON_SITE_BASED);
            return true;
        }

        private void createDefaultRequirement(Integer actorId, String description, SpecimenRequestRequirementType type)
        {
            if (actorId != null && actorId.intValue() > 0 && description != null && description.length() > 0)
            {
                SpecimenRequestRequirement requirement = new SpecimenRequestRequirement();
                requirement.setContainer(getContainer());
                requirement.setActorId(actorId);
                requirement.setDescription(description);
                requirement.setRequestId(-1);
                SpecimenRequestRequirementProvider.get().createDefaultRequirement(getUser(), requirement, type);
            }
        }

        @Override
        public ActionURL getSuccessURL(DefaultRequirementsForm form)
        {
            if (form.getNextPage() != null && form.getNextPage().length() > 0)
                return new ActionURL(form.getNextPage());
            else
                return getManageStudyURL();
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            setHelpTopic("coordinateSpecimens#requirements");
            addManageStudyNavTrail(root);
            root.addChild("Manage Default Requirements");
        }
    }

    @RequiresPermission(ManageRequestRequirementsPermission.class)
    public class DeleteDefaultRequirementAction extends FormHandlerAction<IdForm>
    {
        @Override
        public void validateCommand(IdForm target, Errors errors)
        {
        }

        @Override
        public boolean handlePost(IdForm form, BindException errors) throws Exception
        {
            SpecimenRequestRequirement requirement =
                    SpecimenRequestRequirementProvider.get().getRequirement(getContainer(), form.getId());
            // we should only be deleting default requirements (those without an associated request):
            if (requirement != null && requirement.getRequestId() == -1)
            {
                SpecimenRequestManager.get().deleteRequestRequirement(getUser(), requirement, false);
                return true;
            }

            return false;
        }

        @Override
        public ActionURL getSuccessURL(IdForm requirementForm)
        {
            return new ActionURL(ManageDefaultReqsAction.class, getContainer());
        }
    }

    public static class CompleteSpecimenForm
    {
        private String _prefix;

        public String getPrefix()
        {
            return _prefix;
        }

        public void setPrefix(String prefix)
        {
            _prefix = prefix;
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class CompleteSpecimenAction extends ReadOnlyApiAction<CompleteSpecimenForm>
    {
        @Override
        public ApiResponse execute(CompleteSpecimenForm form, BindException errors)
        {
            ApiSimpleResponse response = new ApiSimpleResponse();

            Study study = getStudy();
            if (study == null)
                throw new NotFoundException("No study exists in this folder.");

            List<JSONObject> completions = new ArrayList<>();
            for (AjaxCompletion completion : getAjaxCompletions(study))
                completions.add(completion.toJSON());

            response.put("completions", completions);
            return response;
        }
    }

    private List<AjaxCompletion> getAjaxCompletions(Study study)
    {
        List<AjaxCompletion> completions = new ArrayList<>();
        String allString = "All " + PageFlowUtil.filter(StudyService.get().getSubjectNounPlural(study.getContainer())) +  " (Large Report)";

        completions.add(new AjaxCompletion(allString, allString));

        for (String ptid : StudyService.get().getParticipantIds(study, getViewContext().getUser()))
            completions.add(new AjaxCompletion(ptid, ptid));

        return completions;
    }
}
