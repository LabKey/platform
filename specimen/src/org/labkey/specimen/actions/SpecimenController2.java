package org.labkey.specimen.actions;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.FormHandlerAction;
import org.labkey.api.action.MutatingApiAction;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.attachments.AttachmentParent;
import org.labkey.api.attachments.BaseDownloadAction;
import org.labkey.api.data.Container;
import org.labkey.api.data.ExcelColumn;
import org.labkey.api.module.FolderType;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineStatusUrls;
import org.labkey.api.pipeline.browse.PipelinePathForm;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.RequiresSiteAdmin;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.specimen.SpecimenManagerNew;
import org.labkey.api.specimen.SpecimenRequestManager;
import org.labkey.api.specimen.SpecimenSearchWebPart;
import org.labkey.api.specimen.Vial;
import org.labkey.api.specimen.importer.SimpleSpecimenImporter;
import org.labkey.api.specimen.model.SpecimenRequestEvent;
import org.labkey.api.specimen.pipeline.SpecimenArchive;
import org.labkey.api.specimen.security.permissions.ManageDisplaySettingsPermission;
import org.labkey.api.specimen.settings.RepositorySettings;
import org.labkey.api.specimen.settings.SettingsManager;
import org.labkey.api.specimen.view.SpecimenWebPart;
import org.labkey.api.study.MapArrayExcelWriter;
import org.labkey.api.study.SpecimenUrls;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.StudyUrls;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.RedirectException;
import org.labkey.api.view.VBox;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.specimen.pipeline.SpecimenBatch;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
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

    public void addRootNavTrail(NavTree root, User user)
    {
        Study study = getStudyRedirectIfNull();
        Container c = getContainer();
        ActionURL rootURL;
        FolderType folderType = c.getFolderType();
        if ("study".equals(folderType.getDefaultModule().getName()))
        {
            rootURL = folderType.getStartURL(c, user);
        }
        else
        {
            rootURL = urlProvider(StudyUrls.class).getBeginURL(c);
        }
        root.addChild(study.getLabel(), rootURL);
    }

    private void addBaseSpecimenNavTrail(NavTree root)
    {
        addRootNavTrail(root, getUser());
        ActionURL overviewURL = new ActionURL(OverviewAction.class, getContainer());
        root.addChild("Specimen Overview", overviewURL);
    }

    private ActionURL getManageStudyURL()
    {
        return urlProvider(StudyUrls.class).getManageStudyURL(getContainer());
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
    public class GetSpecimenExcelAction extends SimpleViewAction
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
                    ActionURL url = urlProvider(SpecimenUrls.class).getSpecimenEventsURL(form.getTargetStudy(), null).addParameter("id", vial.getRowId());
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
    public class AutoReportListAction extends SimpleViewAction
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
}
