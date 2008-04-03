package org.labkey.study.controllers.designer;

import jxl.Range;
import jxl.Workbook;
import jxl.WorkbookSettings;
import org.apache.beehive.netui.pageflow.FormData;
import org.apache.beehive.netui.pageflow.Forward;
import org.apache.beehive.netui.pageflow.annotations.Jpf;
import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.lang.StringUtils;
import org.labkey.api.announcements.DiscussionService;
import org.labkey.api.attachments.AttachmentDirectory;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.ExcelColumn;
import org.labkey.api.data.TableViewForm;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.security.ACL;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.User;
import org.labkey.api.util.CaseInsensitiveHashMap;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.*;
import org.labkey.common.tools.TabLoader;
import org.labkey.study.controllers.BaseController;
import org.labkey.study.designer.*;
import org.labkey.study.designer.client.model.GWTCohort;
import org.labkey.study.designer.client.model.GWTStudyDefinition;
import org.labkey.study.designer.view.StudyDesignsWebPart;
import org.labkey.study.importer.SimpleSpecimenImporter;
import org.labkey.study.model.Study;
import org.labkey.study.model.StudyManager;

import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: Mark Igra
 * Date: Feb 12, 2007
 * Time: 5:01:42 PM
 */
@Jpf.Controller(messageBundles = {@Jpf.MessageBundle(bundlePath = "messages.Validation")}, longLived = true)
public class DesignerController extends BaseController
{
    private static final String TEMPLATE_NAME = "Template";
    private CreateRepositoryForm wizardForm; //Will get reused at each step of the wizard
    private static final TabLoader.ColumnDescriptor[] PARTICIPANT_COLS = new TabLoader.ColumnDescriptor[]{
            new TabLoader.ColumnDescriptor("ParticipantId", String.class),
            new TabLoader.ColumnDescriptor("Cohort", String.class),
            new TabLoader.ColumnDescriptor("StartDate", Date.class)
    };

    public enum WizardStep
    {
        INIT(0,  null),
        PICK_FOLDER(1, "Choose Destination Folder"),
        SHOW_PARTICIPANTS(2, "Subject Information"),
        UPLOAD_PARTICIPANTS(3, "Upload Subject Information"),
        SHOW_SAMPLES(4, "Sample Information"),
        UPLOAD_SAMPLES(5, "Upload Sample Information"),
        CONFIRM(6, "Confirm");

        WizardStep(int number, String title)
        {
            this.number = number;
            this.title = title;
        }

        public String getTitle()
        {
            return title;
        }

        public int getNumber()
        {
            return number;
        }
        
        private String title;
        private int number;

        public static WizardStep fromNumber(int number)
        {
            for (WizardStep step : values())
                if (step.number == number)
                    return step;

            return null;
        }
    }

    @Jpf.Action
    @RequiresPermission(ACL.PERM_READ)
    protected Forward begin() throws Exception
    {
        return _renderInTemplate(new StudyDesignsWebPart(getViewContext(), false), "Study Protocol Registration");
    }
    private ActionURL urlBegin()
    {
        return cloneActionURL().deleteParameters().setAction("begin.view");
    }

    @Jpf.Action
    /**
     * This method represents the point of entry into the pageflow
     */
    @RequiresPermission(ACL.PERM_READ)
    protected Forward designer(StudyDesignForm form) throws Exception
    {
        Map<String, String> params = new HashMap<String,String>();
        params.put("studyId", Integer.toString(form.getStudyId()));
        StudyDesignInfo info = StudyDesignManager.get().getStudyDesign(getContainer(), form.getStudyId());
        //If url is to source container and we've moved to study folder throw the new container
        if (null != info && !info.getContainer().equals(getContainer()))
        {
            ActionURL url = cloneActionURL().setExtraPath(info.getContainer().getPath());
            return new ViewForward(url);
        }

        int revision = form.getRevision();
        if (revision == 0 && form.getStudyId() > 0)
            revision = StudyDesignManager.get().getLatestRevisionNumber(getContainer(), form.getStudyId());
        params.put("revision", Integer.toString(revision));
        params.put("edit", getViewContext().hasPermission(ACL.PERM_UPDATE) && form.isEdit() ? "true" : "false");
        boolean canEdit = getViewContext().hasPermission(ACL.PERM_UPDATE);
        params.put("canEdit",  Boolean.toString(canEdit));
        params.put("canCreateRepository", Boolean.toString(canEdit && null != info && !info.isActive()));
        if (null != StringUtils.trimToNull(form.getFinishURL()))
            params.put("finishURL", form.getFinishURL());
        
        HttpView studyView = new GWTView("org.labkey.study.designer.Designer", params);
        if (0 != form.getStudyId())
        {
            HttpView discussion = DiscussionService.get().getDisussionArea(getViewContext(),
                    info.getLsid().toString(), getActionURL(), "Discussion of " + info.getLabel() + " revision " + revision, true, false);
            VBox vbox = new VBox();
            if (null != getRequest().getParameter("discussion.start") || null != getRequest().getParameter("discussion.id"))
                vbox.addView(new HtmlView("Study information is on this page below the discussion."));
            vbox.addView(discussion);
            vbox.addView(studyView);
            studyView = vbox;
        }
        return _renderInTemplate(studyView, "Study Protocol Definition", new NavTree("Study Protocol Registration", urlBegin()));
    }

    @Jpf.Action
    @RequiresPermission(ACL.PERM_READ)
    protected Forward editTemplate(StudyDesignForm form) throws Exception
    {
        //Just find the template and redirect...
        StudyDesignInfo info = getTemplateInfo(getUser(), getContainer());
        ActionURL url = cloneActionURL();
        url.setAction("designer");
        url.setExtraPath(info.getContainer().getPath());
        url.deleteParameters();
        url.addParameter("edit", "true");
        url.addParameter("studyId", String.valueOf(info.getStudyId()));
        url.addParameter("finishURL", cloneActionURL().setAction("begin.view").toString());
        return new ViewForward(url, true);
    }

    @Jpf.Action
    @RequiresPermission(ACL.PERM_DELETE)
    protected Forward delete(DeleteForm form) throws Exception
    {
        String[] selectedRows = form.getSelectedRows();

        if (null != selectedRows)
            for (String row : selectedRows)
                StudyDesignManager.get().deleteStudyDesign(getContainer(), Integer.parseInt(row));

        return new ViewForward(cloneActionURL().setAction("begin.view"));
    }

    @Jpf.Action(useFormBean = "wizardForm") //Note form will get reused at each step of wizard
    @RequiresPermission(ACL.PERM_ADMIN)
    protected Forward createRepository(CreateRepositoryForm form) throws Exception
    {
        int studyId = form.getStudyId();
        StudyDesignInfo info = StudyDesignManager.get().getStudyDesign(getContainer(), studyId);

        form.setMessage(null); //We're reusing the form, so reset the message.
        validateStep(form, info); //Make sure we are not in some weird back/forward state
        
        //Wizard step is the *last* wizard step shown to the user.
        //Each method handles the post and sets up the form to render the next wizard step
        //  or re-render same step if errors occured
        switch(form.getWizardStep())
        {
            case INIT:
                //We keep the same form instance across posts except if we start new wizard
                form = new CreateRepositoryForm();
                form.setStudyId(studyId);
                form.setParentFolder(getContainer());
                form.setBeginDateStr(DateUtil.formatDate(new Date()));
                form.setWizardStepNumber(1);
                GWTStudyDefinition def = StudyDesignManager.get().getGWTStudyDefinition(getContainer(), info);
                form.setStudyDefinition(def);

                form.setStudyName(info.getLabel());
                form.setFolderName(info.getLabel());

                wizardForm = form;
                break;
            case PICK_FOLDER:
                pickFolder(form);
                break;
            case SHOW_PARTICIPANTS:
                showParticipants(form);
                break;
            case UPLOAD_PARTICIPANTS:
                uploadParticipants(form);
                break;
            case SHOW_SAMPLES:
                showSamples(form);
                break;
            case UPLOAD_SAMPLES:
                handleUploadSamples(form);
                break;
            case CONFIRM:
                //Put visitids back on uploaded participant info...
                Map<String,Object>[] participantMaps = new Map[form.getParticipants().length];
                for (int i = 0; i < form.getParticipants().length; i++)
                {
                    participantMaps[i] = new HashMap<String,Object>(form.getParticipants()[i]);
                    participantMaps[i].put("Date", participantMaps[i].get("StartDate")); //Date of demographic data *is* StartDate by default
                }
                Study study = StudyDesignManager.get().generateStudyFromDesign(getUser(), form.getParentFolder(), form.getFolderName(), form.getBeginDate(), info, participantMaps, form.getSpecimens());
                ActionURL studyUrl = cloneActionURL().deleteParameters();
                wizardForm = null; //This is a long-lived pageFlow so free the memory
                studyUrl.setExtraPath(study.getContainer().getPath());
                studyUrl.setPageFlow("Project");
                studyUrl.setAction("begin.view");
                return new ViewForward(studyUrl);
        }

        JspView<CreateRepositoryForm> wizardView = new JspView<CreateRepositoryForm>("/org/labkey/study/designer/view/CreateRepositoryWizard.jsp", form);
        return _renderInTemplate(wizardView, "Create Study Folder: " + form.getWizardStep().getTitle());
    }

    void validateStep(CreateRepositoryForm form, StudyDesignInfo info) throws Exception
    {

        if (null == info)
            throw new NotFoundException("Couldn't find study with id " + form.getStudyId());

        //Now see if the study already exists.
        if (info.isActive())
            HttpView.throwRedirect(PageFlowUtil.urlProvider(ProjectUrls.class).urlStart(info.getContainer()));

        //Make sure we haven't done some crazy back/forward thing
        int stepNumber = form.getWizardStep().getNumber();
        if (null == form.getStudyName() || null == form.getParentFolder()|| null == form.getStudyDefinition())
        {
            form.setWizardStep(WizardStep.INIT);
            return;
        }
        Container studyFolder = form.getParentFolder().getChild(form.getFolderName());
        if (null != studyFolder && null != StudyManager.getInstance().getStudy(studyFolder))
        {
            form.setMessage("Folder already exists");
            form.setWizardStep(WizardStep.PICK_FOLDER);
            return;
        }
        if (stepNumber > WizardStep.UPLOAD_PARTICIPANTS.getNumber() && null == form.getParticipants())
        {
            form.setWizardStep(WizardStep.SHOW_PARTICIPANTS);
            return;
        }
        if (stepNumber > WizardStep.UPLOAD_SAMPLES.getNumber() && null == form.getSpecimens())
        {
            form.setWizardStep(WizardStep.SHOW_SAMPLES);
            return;
        }
    }

    @Jpf.Action(useFormBean = "wizardForm") //Note form will get reused at each step of wizard
    @RequiresPermission(ACL.PERM_ADMIN)
    protected Forward cancelWizard(CreateRepositoryForm form) throws Exception
    {
        //Don't save any settings after cancel
        wizardForm = new CreateRepositoryForm();
        ActionURL designUrl = cloneActionURL().deleteParameters();
        designUrl.setAction("designer").replaceParameter("studyId", String.valueOf(form.getStudyId()));

        return new ViewForward(designUrl);
    }

    @Jpf.Action(useFormBean = "wizardForm") //Note form will get reused at each step of wizard
    @RequiresPermission(ACL.PERM_ADMIN)
    protected Forward getParticipantExcel(CreateRepositoryForm form) throws Exception
    {
        Set<String> participants = new HashSet<String>();
        List<Map<String,Object>> participantList = new ArrayList<Map<String,Object>>();
        int participantNum = 1;
        for (GWTCohort cohort : (List<GWTCohort>) form.getStudyDefinition().getGroups())
            for (int i = 0; i < cohort.getCount(); i++)
            {
                HashMap<String,Object> hm = new HashMap<String,Object>();
                hm.put("SubjectId", participantNum++);
                hm.put("Cohort", cohort.getName());
                hm.put("StartDate", form.getBeginDate());
                participantList.add(hm);
            }

        TabLoader.ColumnDescriptor[] xlCols = new TabLoader.ColumnDescriptor[3];
        xlCols[0] = new TabLoader.ColumnDescriptor("SubjectId", Integer.class);
        xlCols[1] = new TabLoader.ColumnDescriptor("Cohort", String.class);
        xlCols[2] = new TabLoader.ColumnDescriptor("StartDate", Date.class);
        MapArrayExcelWriter xlWriter = new MapArrayExcelWriter(participantList.toArray(new Map[0]), xlCols);
        xlWriter.setHeaders(Arrays.asList("#Update the SubjectId column of this spreadsheet to the identifiers used when sending a sample to labs", "#"));
        xlWriter.write(getResponse());

        return null;
    }

    @Jpf.Action(useFormBean = "wizardForm") //Note form will get reused at each step of wizard
    @RequiresPermission(ACL.PERM_READ)
    protected Forward getSpecimenExcel(CreateRepositoryForm form) throws Exception
    {
        Container c = getContainer();
        //Search for a template in all folders up to root.
        Workbook inputWorkbook = null;
        while (!c.equals(ContainerManager.getRoot()))
        {
            AttachmentDirectory dir = AttachmentService.get().getMappedAttachmentDirectory(c, false);
            if (null != dir && dir.getFileSystemDirectory().exists())
            {
                if (new File(dir.getFileSystemDirectory(), "Samples.xls").exists())
                {
                    WorkbookSettings settings = new WorkbookSettings();
                    settings.setGCDisabled(true);
                    inputWorkbook = Workbook.getWorkbook(new File(dir.getFileSystemDirectory(), "Samples.xls"), settings);
                }
            }
            c = c.getParent();
        }
        int startRow = 0;
        if (null != inputWorkbook)
        {
            Range[] range = inputWorkbook.findByName("specimen_headers");
            if (null != range && range.length > 0)
                startRow = range[0].getTopLeft().getRow();
            else
                inputWorkbook = null;
        }

        SimpleSpecimenImporter importer = new SimpleSpecimenImporter(true, "Subject Id");
        Map<String,Object>[] defaultSpecimens = StudyDesignManager.get().generateSampleList(form.getStudyDefinition(), form.getParticipants(), form.getBeginDate());
        MapArrayExcelWriter xlWriter = new MapArrayExcelWriter(defaultSpecimens, importer.getSimpleSpecimenColumns());
        for (ExcelColumn col : xlWriter.getColumns())
            col.setCaption(importer.label(col.getName()));
        xlWriter.setCurrentRow(startRow);
        if (null != inputWorkbook)
            xlWriter.setTemplate(inputWorkbook);

        xlWriter.write(getResponse());

        return null;
    }


    private void handleUploadSamples(CreateRepositoryForm form)
            throws IOException
    {
        Set<String> errors = new LinkedHashSet<String>();
        String specimenTSV = StringUtils.trimToNull(form.getSpecimenTSV());
        if (null == specimenTSV)
        {
            form.setMessage("Please provide specimen information.");
            return;
        }
        TabLoader loader = new TabLoader(specimenTSV, true);
        Map<String,String> columnAliases = new HashMap();
        //Make sure we accept the labels
        SimpleSpecimenImporter importer = new SimpleSpecimenImporter();
        for (Map.Entry<String,String> entry : importer.getColumnLabels().entrySet())
            columnAliases.put(entry.getValue(), entry.getKey());
        //And a few more aliases
        columnAliases.put("ParticipantId", SimpleSpecimenImporter.PARTICIPANT_ID);
        columnAliases.put("Subject", SimpleSpecimenImporter.PARTICIPANT_ID);

        //Remember whether we used a different header so we can put up error messages that make sense
        Map<String,String> labels = new HashMap();
        for (TabLoader.ColumnDescriptor c : loader.getColumns())
        {
            if (columnAliases.containsKey(c.name))
            {
                labels.put(columnAliases.get(c.name), c.name);
                c.name = columnAliases.get(c.name);
            }
            else
                labels.put(c.name, c.name);
        }
        importer.fixupSpecimenColumns(loader);

        Map<String,Object>[] specimenRows = (Map<String,Object>[]) loader.load();
        form.setSpecimens(specimenRows);
        Set<String> participants = new HashSet<String>();
        int rowNum = 1;
        for (Map<String,Object> row : specimenRows)
        {
            String participant = (String) row.get(SimpleSpecimenImporter.PARTICIPANT_ID);
            if (null == participant)
                errors.add("Error, Row " + rowNum + " field " + labels.get(SimpleSpecimenImporter.PARTICIPANT_ID) + " is not supplied");
            else
                participants.add((String) row.get(SimpleSpecimenImporter.PARTICIPANT_ID));

            for (String col : PageFlowUtil.set(SimpleSpecimenImporter.SAMPLE_ID, SimpleSpecimenImporter.DRAW_TIMESTAMP))
                if (null == row.get(col))
                    errors.add("Error, Row " + rowNum + " does not contain a value for field " + labels.get(col));

            if (errors.size() >= 3)
                break;

            rowNum++;
        }
        if (!form.isIgnoreWarnings())
        {
            int nParticipantsExpected = 0;
            for (GWTCohort cohort : (List<GWTCohort>) form.getStudyDefinition().getGroups())
                nParticipantsExpected += cohort.getCount();

            if (participants.size() != nParticipantsExpected)
                errors.add("Warning, Expected samples for " + nParticipantsExpected + " subjects, received samples for " + participants);

            form.setContainsWarnings(true);
        }
        if (errors.size() > 0)
        {
            StringBuilder sb = new StringBuilder();
            for (String e : errors)
                sb.append(e).append("\n");

            form.setMessage(sb.toString());
        }
        else
            form.setWizardStep(WizardStep.CONFIRM);
    }

    private void showSamples(CreateRepositoryForm form) throws IOException
    {
        if (form.isUploadSpecimens())
        {
            if (null != form.getSpecimenTSV())
            {
                //Handle back->forward case by reloading
                TabLoader tl = new TabLoader(form.getSpecimenTSV(), true);
                form.setSpecimens((Map<String,Object>[]) tl.load());
            }
            handleUploadSamples(form);
        }
        else
        {
            if (null != form.getSpecimenTSV()) //Reset to autogenerated list
                form.setSpecimens(StudyDesignManager.get().generateSampleList(form.getStudyDefinition(), form.getParticipants(), form.getBeginDate()));
            form.setWizardStep(WizardStep.CONFIRM);
        }
    }

    private void uploadParticipants(CreateRepositoryForm form)
            throws IOException
    {
        //Parse and validate uploaded participant info
        if (null == StringUtils.trimToNull(form.getParticipantTSV()))
        {
            form.setMessage("Please provide participant information.");
            return;
        }
        TabLoader loader = new TabLoader(form.getParticipantTSV(), true);
        fixupParticipantCols(loader);
        List<String> errors = new ArrayList<String>();
        Set<String> participants = new HashSet<String>();
        Map<String,Integer> cohortCounts = new CaseInsensitiveHashMap<Integer>();
        GWTStudyDefinition def = form.getStudyDefinition();
        for (GWTCohort group : (List<GWTCohort>) def.getGroups())
            cohortCounts.put(group.getName(), 0);

        Map<String,Object>[] rows = (Map<String,Object>[]) loader.load();
        form.setParticipants(rows);
        int rowNum = 1;
        for (Map row : rows)
        {
            String cohort = (String) row.get("Cohort");
            String participant = (String) row.get("ParticipantId");
            Date startDate = (Date) row.get("StartDate");
            if (!form.isIgnoreWarnings() && null == cohort)
            {
                errors.add("Warning, Row " + rowNum + " no cohort is listed.");
                form.setContainsWarnings(true);
            }
            else if (!form.isIgnoreWarnings() && !cohortCounts.containsKey(cohort))
            {
                errors.add("Warning, Row " + rowNum + " cohort " + cohort + " is not listed in study definition.");
                form.setContainsWarnings(true);
            }
            else
                cohortCounts.put(cohort, (null == cohortCounts.get(cohort) ? 0 : cohortCounts.get(cohort)) + 1);

            if (null == participant)
                errors.add("Error, Row " + rowNum + " no subject is listed.");
            else if (participants.contains(participant))
                errors.add("Error, Row " + rowNum + " subject is listed more than once");

            if (null == startDate)
                errors.add("Error, Row " + rowNum + " StartDate is not provided.");
            
            if (errors.size() >= 3)
                break;

            rowNum++;
        }
        if (!form.isIgnoreWarnings() && errors.size() == 0)
            for (GWTCohort group : (List<GWTCohort>) def.getGroups())
            {
                if (cohortCounts.get(group.getName()) < group.getCount())
                {
                    errors.add("Warning: not enough subjects for cohort " + group.getName() + " expected " + group.getCount() + " found " + cohortCounts.get(group.getName()));
                    form.setContainsWarnings(true);
                }                    
            }

        if (errors.size() > 0 && !form.isIgnoreWarnings())
        {
            StringBuilder sb = new StringBuilder();
            for (String error : errors)
                sb.append(error).append("\n");

            form.setMessage(sb.toString());
        }
        else
        {
            form.setWizardStep(WizardStep.SHOW_SAMPLES);
        }
    }

    private void fixupParticipantCols(TabLoader loader) throws IOException
    {
        TabLoader.ColumnDescriptor[] loaderCols = loader.getColumns();
        Map<String,TabLoader.ColumnDescriptor> colMap = new CaseInsensitiveHashMap<TabLoader.ColumnDescriptor>();
        for (TabLoader.ColumnDescriptor col : PARTICIPANT_COLS)
            colMap.put(col.name, col);
        colMap.put("ptid", colMap.get("ParticipantId"));
        colMap.put("SubjectId", colMap.get(SimpleSpecimenImporter.PARTICIPANT_ID));

        TabLoader.ColumnDescriptor[] newCols = new TabLoader.ColumnDescriptor[loaderCols.length];
        for (int i = 0; i < loaderCols.length; i++)
        {
            if (colMap.containsKey(loaderCols[i].name))
                newCols[i] = colMap.get(loaderCols[i].name);
            else
                newCols[i] = loaderCols[i];
        }
        loader.setColumns(newCols);
    }

    private void showParticipants(CreateRepositoryForm form) throws IOException, SQLException
    {
        if (null != form.getParticipantTSV())
        {
            TabLoader tl = new TabLoader(form.getParticipantTSV(), true);
            form.setParticipants((Map<String,Object>[]) tl.load());
        }
        if (form.isUploadParticipants())
            uploadParticipants(form);
    }

    private void pickFolder(CreateRepositoryForm form)
            throws ServletException, SQLException
    {
        Date beginDate = null;
        if (null == StringUtils.trimToNull(form.getBeginDateStr()))
        {
            form.setMessage("Please enter a start date");
            return;
        }
        try
        {
            beginDate = (Date) ConvertUtils.convert(form.getBeginDateStr(), Date.class);
            form.setBeginDate(beginDate);
        }
        catch (ConversionException x)
        {
            form.setMessage("Please enter a valid start date.");
            return;
        }

        String folderName = StringUtils.trimToNull(form.getFolderName());
        if (null == folderName)
            form.setMessage("Please set a folder name.");
        else if (getContainer().hasChild(folderName) && null != StudyManager.getInstance().getStudy(getContainer().getChild(folderName)))
            form.setMessage(getContainer().getName() + " already has a child named " + folderName + " containing a study.");
        else
        {
            GWTStudyDefinition def = form.getStudyDefinition();
            Map<String,Object>[] participantDataset = StudyDesignManager.get().generateParticipantDataset(getUser(), def);
            form.setParticipants(participantDataset);
            form.setWizardStep(WizardStep.SHOW_PARTICIPANTS);
        }
    }


    public static synchronized GWTStudyDefinition getTemplate(User u, Container c) throws Exception
    {
        StudyDesignInfo info = StudyDesignManager.get().getStudyDesign(c.getProject(), TEMPLATE_NAME);
        if (null != info)
        {
            StudyDesignVersion version = StudyDesignManager.get().getStudyDesignVersion(c.getProject(), info.getStudyId());
            GWTStudyDefinition def =  XMLSerializer.fromXML(version.getXML());
            def.setCavdStudyId(version.getStudyId());
            def.setRevision(version.getRevision());
            return def;
        }

        GWTStudyDefinition def = GWTStudyDefinition.getDefaultTemplate();
        def.setStudyName(TEMPLATE_NAME);
        StudyDesignVersion version = new StudyDesignVersion();
        version.setContainer(c.getProject());
        version.setLabel(TEMPLATE_NAME);
        version.setDescription("Template used for creating new studies");
        version.setXML(XMLSerializer.toXML(def).toString());
        version = StudyDesignManager.get().saveStudyDesign(u, c.getProject(), version);

        def.setRevision(version.getRevision());
        def.setCavdStudyId(version.getStudyId());

        return def;
    }

    private StudyDesignInfo getTemplateInfo(User u, Container c) throws Exception
    {
        StudyDesignInfo info = StudyDesignManager.get().getStudyDesign(c.getProject(), TEMPLATE_NAME);
        if (null == info)
        {
            getTemplate(u, c);
            info = StudyDesignManager.get().getStudyDesign(c.getProject(), TEMPLATE_NAME);
        }
        return info;
    }


        @Jpf.Action
    @RequiresPermission(ACL.PERM_READ)
    protected Forward definitionService() throws Exception
    {
        StudyDefinitionServiceImpl service = new StudyDefinitionServiceImpl(getViewContext());
        service.doPost(getRequest(), getResponse());
        return null;
    }


    public static class CreateRepositoryForm extends FormData
    {
        private int wizardStepNumber;
        private int studyId;
        private String studyName;
        private String folderName;
        private String message;
        private GWTStudyDefinition studyDefinition;
        private Map<String,Object>[] participants;
        private Map<String,Object>[] specimens;
        private boolean uploadParticipants;
        private boolean uploadSpecimens;
        private String participantTSV;
        private String specimenTSV;
        private Container parentFolder;
        private String beginDateStr;
        private Date beginDate;
        private boolean ignoreWarnings;
        private boolean containsWarnings;


        public int getStudyId()
        {
            return studyId;
        }

        public void setStudyId(int studyId)
        {
            this.studyId = studyId;
        }

        public String getStudyName()
        {
            return studyName;
        }

        public void setStudyName(String studyName)
        {
            this.studyName = studyName;
        }

        public int getWizardStepNumber()
        {
            return wizardStepNumber;
        }

        public void setWizardStepNumber(int wizardStepNumber)
        {
            this.wizardStepNumber = wizardStepNumber;
        }

        public String getMessage()
        {
            return message;
        }

        public void setMessage(String message)
        {
            this.message = message;
        }

        public String getFolderName()
        {
            return folderName;
        }

        public void setFolderName(String folderName)
        {
            this.folderName = folderName;
        }

        public GWTStudyDefinition getStudyDefinition()
        {
            return studyDefinition;
        }

        public void setStudyDefinition(GWTStudyDefinition studyDefinition)
        {
            this.studyDefinition = studyDefinition;
        }

        public Map<String, Object>[] getParticipants()
        {
            return participants;
        }

        public void setParticipants(Map<String, Object>[] participants)
        {
            this.participants = participants;
        }

        public boolean isUploadParticipants()
        {
            return uploadParticipants;
        }

        public void setUploadParticipants(boolean uploadParticipants)
        {
            this.uploadParticipants = uploadParticipants;
        }

        public String getParticipantTSV()
        {
            return participantTSV;
        }

        public void setParticipantTSV(String participantTSV)
        {
            this.participantTSV = participantTSV;
        }

        public Map<String, Object>[] getSpecimens()
        {
            return specimens;
        }

        public void setSpecimens(Map<String, Object>[] specimens)
        {
            this.specimens = specimens;
        }

        public boolean isUploadSpecimens()
        {
            return uploadSpecimens;
        }

        public void setUploadSpecimens(boolean uploadSpecimens)
        {
            this.uploadSpecimens = uploadSpecimens;
        }

        public String getSpecimenTSV()
        {
            return specimenTSV;
        }

        public void setSpecimenTSV(String specimenTSV)
        {
            this.specimenTSV = specimenTSV;
        }

        public WizardStep getWizardStep()
        {
            return WizardStep.fromNumber(getWizardStepNumber());
        }

        public void setWizardStep(WizardStep wizardStep)
        {
            wizardStepNumber = wizardStep.getNumber();
        }

        public Container getParentFolder()
        {
            return parentFolder;
        }

        public void setParentFolder(Container parentFolder)
        {
            this.parentFolder = parentFolder;
        }

        public String getBeginDateStr()
        {
            return beginDateStr;
        }

        public void setBeginDateStr(String beginDateStr)
        {
            this.beginDateStr = beginDateStr;
        }

        public void setBeginDate(Date beginDate)
        {
            this.beginDate = beginDate;
        }

        public Date getBeginDate()
        {
            return beginDate;
        }

        public boolean isIgnoreWarnings()
        {
            return ignoreWarnings;
        }

        public void setIgnoreWarnings(boolean ignoreWarnings)
        {
            this.ignoreWarnings = ignoreWarnings;
        }

        public boolean isContainsWarnings()
        {
            return containsWarnings;
        }

        public void setContainsWarnings(boolean containsWarnings)
        {
            this.containsWarnings = containsWarnings;
        }
    }
    public static class StudyDesignForm extends FormData
    {
        private int studyId;
        private int revision;
        private String finishURL;
        private boolean edit;

        public int getRevision()
        {
            return revision;
        }

        public void setRevision(int revision)
        {
            this.revision = revision;
        }

        public int getStudyId()
        {
            return studyId;
        }

        public void setStudyId(int studyId)
        {
            this.studyId = studyId;
        }

        public boolean isEdit()
        {
            return edit;
        }

        public void setEdit(boolean edit)
        {
            this.edit = edit;
        }

        public String getFinishURL()
        {
            return finishURL;
        }

        public void setFinishURL(String finishURL)
        {
            this.finishURL = finishURL;
        }
    }

    public static class DeleteForm extends TableViewForm
    {
        public DeleteForm()
        {
            super(StudyDesignManager.get().getStudyDesignTable());
        }
    }
}
