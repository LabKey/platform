/*
 * Copyright (c) 2007-2017 LabKey Corporation
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

package org.labkey.study.controllers.designer;

import gwt.client.org.labkey.study.designer.client.model.GWTCohort;
import gwt.client.org.labkey.study.designer.client.model.GWTStudyDefinition;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.action.ApiAction;
import org.labkey.api.action.ApiJsonWriter;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.ExportAction;
import org.labkey.api.action.FormHandlerAction;
import org.labkey.api.action.GWTServiceAction;
import org.labkey.api.action.SimpleRedirectAction;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.announcements.DiscussionService;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DataRegionSelection;
import org.labkey.api.data.ExcelColumn;
import org.labkey.api.gwt.server.BaseRemoteService;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.reader.ColumnDescriptor;
import org.labkey.api.reader.TabLoader;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.TimepointType;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.RedirectException;
import org.labkey.api.view.VBox;
import org.labkey.api.view.template.ClientDependency;
import org.labkey.study.controllers.BaseStudyController;
import org.labkey.study.designer.JSONSerializer;
import org.labkey.study.designer.MapArrayExcelWriter;
import org.labkey.study.designer.StudyDefinitionServiceImpl;
import org.labkey.study.designer.StudyDesignInfo;
import org.labkey.study.designer.StudyDesignManager;
import org.labkey.study.designer.StudyDesignVersion;
import org.labkey.study.designer.XMLSerializer;
import org.labkey.study.designer.view.StudyDesignsWebPart;
import org.labkey.study.importer.SimpleSpecimenImporter;
import org.labkey.study.view.StudyGWTView;
import org.labkey.study.view.VaccineStudyWebPart;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: jgarms
 */
public class DesignerController extends SpringActionController
{
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

    private static final String TEMPLATE_NAME = "Template";
    private static final ColumnDescriptor[] PARTICIPANT_COLS = new ColumnDescriptor[]{
            new ColumnDescriptor("ParticipantId", String.class),
            new ColumnDescriptor("Cohort", String.class),
            new ColumnDescriptor("StartDate", Date.class)
    };

    private static final String PARTICIPANT_KEY = DesignerController.class + ".participants";
    private static final String SPECIMEN_KEY = DesignerController.class + ".specimens";

    private static final DefaultActionResolver ACTION_RESOLVER =
            new DefaultActionResolver(DesignerController.class);

    public DesignerController()
    {
        super();
        setActionResolver(ACTION_RESOLVER);
    }

    @RequiresPermission(ReadPermission.class)
    public class BeginAction extends SimpleViewAction
    {

        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            return new StudyDesignsWebPart(getViewContext(), false);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Study Protocol Registration");
        }
    }

    @RequiresPermission(DeletePermission.class)
    public class DeleteAction extends FormHandlerAction
    {

        public void validateCommand(Object target, Errors errors) {}

        public boolean handlePost(Object o, BindException errors) throws Exception
        {
            Set<String> selectedRows = DataRegionSelection.getSelected(getViewContext(), true);
            for (String row : selectedRows)
            {
                StudyDesignManager.get().deleteStudyDesign(getContainer(), Integer.parseInt(row));
            }
            return true;
        }

        public ActionURL getSuccessURL(Object o)
        {
            return new ActionURL(BeginAction.class, getContainer());
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class CancelWizardAction extends SimpleRedirectAction<CreateRepositoryForm>
    {
        public ActionURL getRedirectURL(CreateRepositoryForm form) throws Exception
        {
            setParticipants(null);
            setSpecimens(null);
            ActionURL designURL = new ActionURL(DesignerAction.class, getContainer());
            designURL.addParameter("studyId", form.getStudyId());
            return designURL;
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class DesignerAction extends SimpleViewAction<StudyDesignForm>
    {

        public NavTree appendNavTrail(NavTree root)
        {
            try
            {
            if (null != getCommand().getPanel())
                root.addChild("Study Protocol Registration");
            } catch(Exception e)
            {}
            return root;
        }

        public ModelAndView getView(StudyDesignForm form, BindException errors) throws Exception
        {
            if (null == form.getPanel()) //Old code to handle deprecated case of designs not affiliated with studies
            {
                Map<String, String> params = new HashMap<>();
                params.put("studyId", Integer.toString(form.getStudyId()));
                StudyDesignInfo info = StudyDesignManager.get().getStudyDesign(getContainer(), form.getStudyId());
                //If url is to source container and we've moved to study folder throw the new container
                if (null != info && !info.getContainer().equals(getContainer()))
                {
                    ActionURL url = new ActionURL(DesignerAction.class, info.getContainer());
                    url.addParameter("studyId", form.getStudyId());
                    throw new RedirectException(url);
                }

                int revision = form.getRevision();
                if (revision == 0 && form.getStudyId() > 0)
                {
                    Integer revInteger = StudyDesignManager.get().getLatestRevisionNumber(getContainer(), form.getStudyId());
                    if (revInteger == null)
                    {
                        throw new NotFoundException("No revision found for Study ID: " + form.getStudyId());
                    }
                    revision = revInteger.intValue();
                }
                params.put("revision", Integer.toString(revision));
                params.put("edit", getViewContext().hasPermission(UpdatePermission.class) && form.isEdit() ? "true" : "false");
                boolean canEdit = getViewContext().hasPermission(UpdatePermission.class);
                params.put("canEdit",  Boolean.toString(canEdit));
                boolean canAdmin = getViewContext().hasPermission(AdminPermission.class);
                params.put("canAdmin", Boolean.toString(canAdmin));
                params.put("showAllLookups", "true");
                params.put("canCreateRepository", Boolean.toString(canEdit && null != info && !info.isActive()));
                if (null != StringUtils.trimToNull(form.getFinishURL()))
                    params.put("finishURL", form.getFinishURL());

                HttpView studyView = new StudyGWTView(gwt.client.org.labkey.study.designer.client.Designer.class, params);
                if (0 != form.getStudyId() && info != null)
                {
                    HttpView discussion = DiscussionService.get().getDiscussionArea(
                            getViewContext(),
                            info.getLsid().toString(),
                            getViewContext().getActionURL(),
                            "Discussion of " + info.getLabel() + " revision " + revision,
                            true, false);
                    VBox vbox = new VBox();
                    vbox.addClientDependency(ClientDependency.fromPath("study/StudyVaccineDesign.css"));
                    if (null != HttpView.currentRequest().getParameter("discussion.start") || null != HttpView.currentRequest().getParameter("discussion.id"))
                        vbox.addView(new HtmlView("Study information is on this page below the discussion."));
                    vbox.addView(discussion);
                    vbox.addView(studyView);
                    studyView = vbox;
                }
                return studyView;
            }
            else
            {

                VaccineStudyWebPart.Model model = new VaccineStudyWebPart.Model();
                Study study = BaseStudyController.getStudyRedirectIfNull(getContainer());
                StudyDesignInfo info = StudyDesignManager.get().getDesignForStudy(getUser(), study, getContainer().hasPermission(getUser(), AdminPermission.class));
                if (null == info)
                    return new HtmlView("Study design information not available for this study.  Contact an administrator to configure the study design.");

                model.setStudyId(info.getStudyId());
                model.setEditMode(form.isEdit());
                model.setPanel(form.getPanel());
                model.setFinishURL(form.getFinishURL());

                return new VaccineStudyWebPart(model);
            }
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class DefinitionServiceAction extends GWTServiceAction
    {
        protected BaseRemoteService createService()
        {
            return new StudyDefinitionServiceImpl(getViewContext());
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class EditTemplateAction extends SimpleRedirectAction<StudyDesignForm>
    {
        public ActionURL getRedirectURL(StudyDesignForm studyDesignForm) throws Exception
        {
            StudyDesignInfo info = getTemplateInfo(getUser(), getContainer());
            ActionURL url = new ActionURL(DesignerAction.class, info.getContainer());
            url.addParameter("edit", "true");
            url.addParameter("studyId", info.getStudyId());
            url.addParameter("finishURL", new ActionURL(BeginAction.class, info.getContainer()).toString());
            return url;
        }
    }

    @SuppressWarnings("unchecked")
    @RequiresPermission(AdminPermission.class)
    public class GetParticipantExcelAction extends ExportAction<CreateRepositoryForm>
    {
        public void export(CreateRepositoryForm form, HttpServletResponse response, BindException errors) throws Exception
        {
            List<Map<String,Object>> participantGroup = new ArrayList<>();
            int participantNum = 1;
            for (GWTCohort cohort : getStudyDefinition(form).getGroups())
            {
                for (int i = 0; i < cohort.getCount(); i++)
                {
                    HashMap<String,Object> hm = new HashMap<>();
                    hm.put("SubjectId", participantNum++);
                    hm.put("Cohort", cohort.getName());
                    hm.put("StartDate", form.getBeginDate());
                    participantGroup.add(hm);
                }
            }

            ColumnDescriptor[] xlCols = new ColumnDescriptor[3];
            xlCols[0] = new ColumnDescriptor("SubjectId", Integer.class);
            xlCols[1] = new ColumnDescriptor("Cohort", String.class);
            xlCols[2] = new ColumnDescriptor("StartDate", Date.class);
            MapArrayExcelWriter xlWriter = new MapArrayExcelWriter(participantGroup, xlCols);
            xlWriter.setHeaders(Arrays.asList("#Update the SubjectId column of this spreadsheet to the identifiers used when sending a sample to labs", "#"));
            xlWriter.write(response);
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class GetStudyDesigns extends ApiAction<GetStudyDesignsForm>
    {

        public ApiResponse execute(GetStudyDesignsForm getStudyDesignsForm, BindException errors) throws Exception
        {
            JSONArray jsonDesigns = new JSONArray();
            StudyDesignInfo[] designs;
            if (getStudyDesignsForm.isIncludeSubfolders())
                designs = StudyDesignManager.get().getStudyDesignsForAllFolders(getUser(), getContainer());
            else
                designs = StudyDesignManager.get().getStudyDesigns(getContainer());
            for (StudyDesignInfo info : designs)
            {
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("publicRevision", info.getPublicRevision());
                jsonObject.put("sourceContainer", containerJSON(info.getSourceContainer()));
                jsonObject.put("label", info.getLabel());
                jsonObject.put("studyId", info.getStudyId());
                jsonObject.put("active", info.isActive());
                jsonObject.put("container", containerJSON(info.getContainer()));
                jsonObject.put("studyDefinition", JSONSerializer.toJSON(StudyDesignManager.get().getGWTStudyDefinition(getUser(), getContainer(), info)));
                jsonDesigns.put(jsonObject);
            }

            ApiJsonWriter writer = new ApiJsonWriter(getViewContext().getResponse());
            writer.writeResponse(new ApiSimpleResponse("studyDesigns", jsonDesigns));
            return null;
        }

        Map<String,Object> containerJSON(Container c)
        {
            JSONObject j = new JSONObject();
            j.put("id", c.getId());
            j.put("path", c.getPath());
            j.put("name", c.getName());
            
            return j;
        }
    }

    public static class GetStudyDesignsForm
    {
        private boolean includeSubfolders;

        public boolean isIncludeSubfolders()
        {
            return includeSubfolders;
        }

        public void setIncludeSubfolders(boolean includeSubfolders)
        {
            this.includeSubfolders = includeSubfolders;
        }
    }
    @RequiresPermission(ReadPermission.class)
    public class GetSpecimenExcelAction extends ExportAction<CreateRepositoryForm>
    {
        public void export(CreateRepositoryForm form, HttpServletResponse response, BindException errors) throws Exception
        {
            //Search for a template in all folders up to root.
            SimpleSpecimenImporter importer = new SimpleSpecimenImporter(getContainer(), getUser(), TimepointType.DATE, "Subject");
            List<Map<String,Object>> defaultSpecimens = StudyDesignManager.get().generateSampleList(getStudyDefinition(form), getParticipants(), form.getBeginDate());
            MapArrayExcelWriter xlWriter = new MapArrayExcelWriter(defaultSpecimens, importer.getSimpleSpecimenColumns());
            for (ExcelColumn col : xlWriter.getColumns())
            {
                col.setCaption(importer.label(col.getName()));
            }

            xlWriter.write(response);
        }
    }

    @SuppressWarnings("unchecked")
    @RequiresPermission(AdminPermission.class)
    public class CreateRepository extends SimpleViewAction<CreateRepositoryForm>
    {
        String titleForNav;

        public ModelAndView getView(CreateRepositoryForm form, BindException errors) throws Exception
        {
            int studyId = form.getStudyId();
            StudyDesignInfo info = StudyDesignManager.get().getStudyDesign(getContainer(), studyId);
            StudyDesignVersion version = StudyDesignManager.get().getStudyDesignVersion(info.getContainer(), info.getStudyId());
            GWTStudyDefinition def = XMLSerializer.fromXML(version.getXML(), getUser(), getContainer());

            form.setMessage(null); //We're reusing the form, so reset the message.
            validateStep(form, info); //Make sure we are not in some weird back/forward state

            //Wizard step is the *last* wizard step shown to the user.
            //Each method handles the post and sets up the form to render the next wizard step
            //  or re-render same step if errors occurred
            switch(form.getWizardStep())
            {
                case INIT:
                    //We keep the same form instance across posts except if we start new wizard
                    form = new CreateRepositoryForm();
                    form.setStudyId(studyId);
                    form.setParentFolderId(getContainer().getId());
                    form.setBeginDate(new Date());
                    form.setWizardStepNumber(WizardStep.PICK_FOLDER.getNumber());
                    form.setStudyName(info.getLabel());
                    form.setFolderName(info.getLabel());
                    form.setSubjectNounSingular(def.getAnimalSpecies());
                    form.setSubjectNounPlural(def.getAnimalSpecies() + "s");
                    form.setSubjectColumnName(def.getAnimalSpecies() + "Id");
                    setParticipants(null);
                    setSpecimens(null);
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
                    List<Map<String,Object>> participantMaps = new ArrayList<>(getParticipants().size());
                    for (int i = 0; i < getParticipants().size(); i++)
                    {
                        HashMap<String, Object> newMap = new HashMap<>(getParticipants().get(i));
                        newMap.put("Date", newMap.get("StartDate")); //Date of demographic data *is* StartDate by default
                        participantMaps.add(newMap);
                    }
                    Study study = StudyDesignManager.get().generateStudyFromDesign(getUser(), ContainerManager.getForId(form.getParentFolderId()),
                            form.getFolderName(), form.getBeginDate(), form.getSubjectNounSingular(), form.getSubjectNounPlural(),
                            form.getSubjectColumnName(), info, participantMaps, getSpecimens());
                    final ActionURL studyFolderUrl = PageFlowUtil.urlProvider(ProjectUrls.class).getStartURL(study.getContainer());
                    throw new RedirectException(studyFolderUrl);
            }

            titleForNav = form.getWizardStep().getTitle();
            if (titleForNav == null)
                titleForNav = form.getStudyName();
            return new JspView<>("/org/labkey/study/designer/view/CreateRepositoryWizard.jsp", form);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Create Study Folder: " + titleForNav);
        }
    }


    private void validateStep(CreateRepositoryForm form, StudyDesignInfo info) throws Exception
    {
        if (null == info)
            throw new NotFoundException("Couldn't find study with id " + form.getStudyId());

        //Now see if the study already exists.
        if (info.isActive())
        {
            throw new RedirectException(PageFlowUtil.urlProvider(ProjectUrls.class).getStartURL(info.getContainer()));
        }

        //Make sure we haven't done some crazy back/forward thing
        int stepNumber = form.getWizardStep().getNumber();
        if (null == form.getStudyName() ||
                "".equals(form.getStudyName()) ||
                null == form.getParentFolderId()||
                null == getStudyDefinition(form))
        {
            form.setWizardStep(WizardStep.INIT);
            return;
        }
        Container studyFolder = ContainerManager.getForId(form.getParentFolderId()).getChild(form.getFolderName());
        if (null != studyFolder && null != BaseStudyController.getStudy(studyFolder))
        {
            form.setMessage("Folder already exists");
            form.setWizardStep(WizardStep.PICK_FOLDER);
            return;
        }
        if (stepNumber > WizardStep.UPLOAD_PARTICIPANTS.getNumber() && null == getParticipants())
        {
            form.setWizardStep(WizardStep.SHOW_PARTICIPANTS);
            return;
        }
        if (stepNumber > WizardStep.UPLOAD_SAMPLES.getNumber() && null == getSpecimens())
        {
            form.setWizardStep(WizardStep.SHOW_SAMPLES);
        }
    }

    private void pickFolder(CreateRepositoryForm form) throws Exception
    {
        String folderName = StringUtils.trimToNull(form.getFolderName());
        Container container = getContainer();
        if (null == folderName)
            form.setMessage("Please set a folder name.");
        else if (null == form.getBeginDate())
        {
            form.setMessage("Please enter a date in the format yyyy-MM-dd.");
            form.setBeginDate(new Date());
        }
        else if (container.hasChild(folderName) && null != BaseStudyController.getStudy(container.getChild(folderName)))
            form.setMessage(container.getName() + " already has a child named " + folderName + " containing a study.");
        else if (!StudyService.get().isValidSubjectColumnName(getContainer(), form.getSubjectColumnName()))
            form.setMessage("\"" + form.getSubjectColumnName() + "\" is not a valid subject column name.");
        else if (!StudyService.get().isValidSubjectNounSingular(getContainer(), form.getSubjectNounSingular()))
            form.setMessage("\"" + form.getSubjectNounSingular() + "\" is not a valid subject noun.");
        else
        {
            GWTStudyDefinition def = getStudyDefinition(form);
            List<Map<String,Object>> participantDataset = StudyDesignManager.get().generateParticipantDataset(getUser(), def);
            setParticipants(participantDataset);
            form.setWizardStep(WizardStep.SHOW_PARTICIPANTS);
        }
    }

    @SuppressWarnings("unchecked")
    private void showParticipants(CreateRepositoryForm form) throws IOException, SQLException
    {
        if (null != form.getParticipantTSV())
        {
            TabLoader tl = new TabLoader(form.getParticipantTSV(), true);
            setParticipants(tl.load());
        }
        if (form.isUploadParticipants())
            uploadParticipants(form);
    }

    @SuppressWarnings("unchecked")
    private void uploadParticipants(CreateRepositoryForm form)
            throws SQLException, IOException
    {
        //Parse and validate uploaded participant info
        if (null == StringUtils.trimToNull(form.getParticipantTSV()))
        {
            form.setMessage("Please provide participant information.");
            return;
        }
        TabLoader loader = new TabLoader(form.getParticipantTSV(), true);
        fixupParticipantCols(loader);
        List<String> errors = new ArrayList<>();
        Set<String> participants = new HashSet<>();
        Map<String,Integer> cohortCounts = new CaseInsensitiveHashMap<>();
        GWTStudyDefinition def = getStudyDefinition(form);
        for (GWTCohort group : def.getGroups())
            cohortCounts.put(group.getName(), 0);

        List<Map<String, Object>> rows = loader.load();
        if (rows == null || rows.isEmpty())
        {
            form.setMessage("Information for at least one participant is required.");
            return;
        }
        
        setParticipants(rows);
        int rowNum = 1;
        for (Map<String, Object> row : rows)
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
                cohortCounts.put(cohort, (null == cohortCounts.get(cohort) ? 0 : cohortCounts.get(cohort).intValue()) + 1);

            if (null == participant)
                errors.add("Error, Row " + rowNum + " no subject is listed.");
            else if (participants.contains(participant))
                errors.add("Error, Row " + rowNum + " subject is listed more than once");
            else
                participants.add(participant);

            if (null == startDate)
                errors.add("Error, Row " + rowNum + " StartDate is not provided.");

            if (errors.size() >= 3)
                break;

            rowNum++;
        }
        if (!form.isIgnoreWarnings() && errors.size() == 0)
            for (GWTCohort group : def.getGroups())
            {
                if (cohortCounts.get(group.getName()).intValue() < group.getCount())
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

    private static void fixupParticipantCols(TabLoader loader) throws IOException
    {
        ColumnDescriptor[] loaderCols = loader.getColumns();
        Map<String, ColumnDescriptor> colMap = new CaseInsensitiveHashMap<>();
        for (ColumnDescriptor col : PARTICIPANT_COLS)
            colMap.put(col.name, col);
        colMap.put("ptid", colMap.get("ParticipantId"));
        colMap.put("SubjectId", colMap.get(SimpleSpecimenImporter.PARTICIPANT_ID));

        ColumnDescriptor[] newCols = new ColumnDescriptor[loaderCols.length];
        for (int i = 0; i < loaderCols.length; i++)
        {
            if (colMap.containsKey(loaderCols[i].name))
                newCols[i] = colMap.get(loaderCols[i].name);
            else
                newCols[i] = loaderCols[i];
        }
        loader.setColumns(newCols);
    }

    private static StudyDesignInfo getTemplateInfo(User u, Container c) throws Exception
    {
        StudyDesignInfo info = StudyDesignManager.get().getStudyDesign(c.getProject(), TEMPLATE_NAME);
        if (null == info)
        {
            getTemplate(u, c);
            info = StudyDesignManager.get().getStudyDesign(c.getProject(), TEMPLATE_NAME);
        }
        return info;
    }

    @SuppressWarnings("unchecked")
    private void showSamples(CreateRepositoryForm form) throws IOException
    {
        if (form.isUploadSpecimens())
        {
            if (null != form.getSpecimenTSV())
            {
                //Handle back->forward case by reloading
                TabLoader tl = new TabLoader(form.getSpecimenTSV(), true);
                setSpecimens(tl.load());
            }
            handleUploadSamples(form);
        }
        else
        {
            if (null != form.getSpecimenTSV()) //Reset to autogenerated list
                setSpecimens(StudyDesignManager.get().generateSampleList(getStudyDefinition(form), getParticipants(), form.getBeginDate()));
            form.setWizardStep(WizardStep.CONFIRM);
        }
    }

    @SuppressWarnings("unchecked")
    private void handleUploadSamples(CreateRepositoryForm form)
            throws IOException
    {
        Set<String> errors = new LinkedHashSet<>();
        String specimenTSV = StringUtils.trimToNull(form.getSpecimenTSV());
        if (null == specimenTSV)
        {
            form.setMessage("Please provide specimen information.");
            return;
        }
        TabLoader loader = new TabLoader(specimenTSV, true);
        Map<String, String> columnAliases = new HashMap();
        Map<String, String> labels = new HashMap();
        //Make sure we accept the labels
        SimpleSpecimenImporter importer = new SimpleSpecimenImporter(getContainer(), getUser());
        for (Map.Entry<String, String> entry : importer.getColumnLabels().entrySet())
        {
            columnAliases.put(entry.getValue(), entry.getKey());
            labels.put(entry.getKey(), entry.getValue());
        }

        //And a few more aliases
        columnAliases.put("ParticipantId", SimpleSpecimenImporter.PARTICIPANT_ID);
        columnAliases.put("Date", SimpleSpecimenImporter.DRAW_TIMESTAMP);
        columnAliases.put("Subject", SimpleSpecimenImporter.PARTICIPANT_ID);

        //Remember whether we used a different header so we can put up error messages that make sense
        for (ColumnDescriptor c : loader.getColumns())
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

        List<Map<String, Object>> specimenRows = loader.load();
        setSpecimens(specimenRows);
        Set<String> participants = new HashSet<>();
        int rowNum = 1;
        for (Map<String,Object> row : specimenRows)
        {
            if (row.get(SimpleSpecimenImporter.VIAL_ID) ==  null && row.get(SimpleSpecimenImporter.SAMPLE_ID) == null)
                errors.add("Error, Row " + rowNum + " must provide a sample or vial ID.");

            String participant = (String) row.get(SimpleSpecimenImporter.PARTICIPANT_ID);
            if (null == participant)
                errors.add("Error, Row " + rowNum + " field " + labels.get(SimpleSpecimenImporter.PARTICIPANT_ID) + " is not supplied");
            else
                participants.add(participant);

            for (String col : PageFlowUtil.set(SimpleSpecimenImporter.SAMPLE_ID, SimpleSpecimenImporter.DRAW_TIMESTAMP))
                if (null == row.get(col))
                    errors.add("Error, Row " + rowNum + " does not contain a value for field " + (labels.containsKey(col) ? labels.get(col) : col));

            if (errors.size() >= 3)
                break;

            rowNum++;
        }
        if (!form.isIgnoreWarnings())
        {
            int nParticipantsExpected = 0;
            for (GWTCohort cohort : getStudyDefinition(form).getGroups())
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

    public static GWTStudyDefinition getStudyDefinition(CreateRepositoryForm form, User user, Container container)
    {
        StudyDesignInfo info = StudyDesignManager.get().getStudyDesign(container, form.getStudyId());
        if (info == null)
            throw new IllegalStateException("Could not find StudyDesignInfo for studyId " + form.getStudyId());
        return StudyDesignManager.get().getGWTStudyDefinition(user, container, info);
    }

    private GWTStudyDefinition getStudyDefinition(CreateRepositoryForm form)
    {
        return getStudyDefinition(form, getUser(), getContainer());
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String,Object>> getParticipants()
    {
        return (List<Map<String,Object>>)HttpView.currentContext().getSession().getAttribute(PARTICIPANT_KEY);
    }

    private void setParticipants(List<Map<String,Object>> participants)
    {
        HttpSession session = HttpView.currentContext().getSession();
        if (participants == null)
        {
            session.removeAttribute(PARTICIPANT_KEY);
            return;
        }
        session.setAttribute(PARTICIPANT_KEY, participants);
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String,Object>> getSpecimens()
    {
        return (List<Map<String,Object>>)HttpView.currentContext().getSession().getAttribute(SPECIMEN_KEY);
    }

    private void setSpecimens(List<Map<String,Object>> specimens)
    {
        HttpSession session = HttpView.currentContext().getSession();
        if (specimens == null)
        {
            session.removeAttribute(SPECIMEN_KEY);
            return;
        }
        session.setAttribute(SPECIMEN_KEY, specimens);
    }

    public static synchronized GWTStudyDefinition getTemplate(User u, Container c) throws Exception
    {
        StudyDesignInfo info = StudyDesignManager.get().getStudyDesign(c.getProject(), TEMPLATE_NAME);
        if (null != info)
        {
            StudyDesignVersion version = StudyDesignManager.get().getStudyDesignVersion(c.getProject(), info.getStudyId());
            GWTStudyDefinition def =  XMLSerializer.fromXML(version.getXML(), u, c);
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

    public static class CreateRepositoryForm
    {
        private int wizardStepNumber;
        private int studyId;
        private String studyName;
        private String folderName;
        private String message;
        private boolean uploadParticipants;
        private boolean uploadSpecimens;
        private String participantTSV;
        private String specimenTSV;
        private String parentFolderId;
        private Date beginDate;
        private boolean ignoreWarnings;
        private boolean containsWarnings;
        private String subjectNounSingular;
        private String subjectNounPlural;
        private String subjectColumnName;

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

        public String getParentFolderId()
        {
            return parentFolderId;
        }

        public void setParentFolderId(String parentFolderId)
        {
            this.parentFolderId = parentFolderId;
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

        public String getSubjectNounSingular()
        {
            return subjectNounSingular;
        }

        public void setSubjectNounSingular(String subjectNounSingular)
        {
            this.subjectNounSingular = subjectNounSingular;
        }

        public String getSubjectNounPlural()
        {
            return subjectNounPlural;
        }

        public void setSubjectNounPlural(String subjectNounPlural)
        {
            this.subjectNounPlural = subjectNounPlural;
        }

        public String getSubjectColumnName()
        {
            return subjectColumnName;
        }

        public void setSubjectColumnName(String subjectColumnName)
        {
            this.subjectColumnName = subjectColumnName;
        }
    }

    public static class StudyDesignForm
    {
        private int studyId;
        private int revision;
        private String finishURL;
        private boolean edit;
        private String panel;

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

        public String getPanel()
        {
            return panel;
        }

        public void setPanel(String panel)
        {
            this.panel = panel;
        }
    }
}
