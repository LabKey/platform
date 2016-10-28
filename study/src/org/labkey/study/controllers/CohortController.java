/*
 * Copyright (c) 2008-2016 LabKey Corporation
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
package org.labkey.study.controllers;

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.SimpleRedirectAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.data.ActionButton;
import org.labkey.api.data.ButtonBar;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.TableInfo;
import org.labkey.api.gwt.client.util.PropertyUtil;
import org.labkey.api.query.QueryUpdateForm;
import org.labkey.api.query.ValidationError;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.study.Cohort;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DataView;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.InsertView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.UpdateView;
import org.labkey.api.view.VBox;
import org.labkey.study.StudySchema;
import org.labkey.study.model.CohortImpl;
import org.labkey.study.model.CohortManager;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.query.CohortQueryView;
import org.labkey.study.query.CohortTable;
import org.labkey.study.query.StudyQuerySchema;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User: jgarms
 * Date: Jul 23, 2008
 * Time: 5:40:59 PM
 */
public class CohortController extends BaseStudyController
{
    private static final ActionResolver ACTION_RESOLVER = new DefaultActionResolver(CohortController.class);

    public CohortController()
    {
        super();
        setActionResolver(ACTION_RESOLVER);
    }

    @RequiresPermission(AdminPermission.class)
    public class DeleteCohortAction extends SimpleRedirectAction<CohortIdForm>
    {
        public ActionURL getRedirectURL(CohortIdForm form) throws Exception
        {
            CohortImpl cohort = StudyManager.getInstance().getCohortForRowId(getContainer(), getUser(), form.getRowId());
            if (cohort != null && !cohort.isInUse())
                StudyManager.getInstance().deleteCohort(cohort);

            return new ActionURL(CohortController.ManageCohortsAction.class, getContainer());
        }
    }

    public static class CohortIdForm
    {
        private int rowId;
        public void setRowId(int rowId) {this.rowId = rowId;}
        public int getRowId() {return rowId;}
    }

    @RequiresPermission(AdminPermission.class)
    public class DeleteUnusedCohortsAction extends SimpleRedirectAction
    {
        public ActionURL getRedirectURL(Object form) throws Exception
        {
            List<CohortImpl> cohorts = StudyManager.getInstance().getCohorts(getContainer(), getUser());
            for (CohortImpl cohort : cohorts)
            {
                if (!cohort.isInUse())
                    StudyManager.getInstance().deleteCohort(cohort);
            }

            return new ActionURL(ManageCohortsAction.class, getContainer());
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class ManageCohortsAction extends FormViewAction<ManageCohortsForm>
    {
        public ModelAndView getView(ManageCohortsForm form, boolean reshow, BindException errors) throws Exception
        {
            StudyManager.getInstance().assertCohortsViewable(getContainer(), HttpView.currentContext().getUser());

            VBox vbox = new VBox();
            StudyImpl study = getStudyRedirectIfNull();
            StudyJspView<Object> top = new StudyJspView<>(study, "manageCohortsTop.jsp", null, errors);
            top.setTitle("Cohort Assignment");
            vbox.addView(top);

            CohortQueryView queryView = new CohortQueryView(getUser(), study, HttpView.currentContext());
            queryView.setTitle("Defined Cohorts");
            queryView.setButtonBarPosition(DataRegion.ButtonBarPosition.TOP);
            vbox.addView(queryView);
            
            StudyJspView<Object> bottom = new StudyJspView<>(study, "manageCohortsBottom.jsp", null, errors);
            bottom.setTitle(StudyService.get().getSubjectNounSingular(getContainer()) + "-Cohort Assignments");
            vbox.addView(bottom);

            return vbox;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            setHelpTopic("manageCohorts");
            _appendManageStudy(root);
            root.addChild("Manage Cohorts");
            return root;
        }

        public void validateCommand(ManageCohortsForm target, Errors errors) {}

        public boolean handlePost(ManageCohortsForm form, BindException errors) throws Exception
        {
            StudyImpl study = getStudyThrowIfNull();

            if (form.isClearParticipants())
            {
                CohortManager.getInstance().clearParticipantCohorts(study);
                if (!form.isManualCohortAssignment())
                {
                    CohortManager.getInstance().setAutomaticCohortAssignment(study, getUser(), -1,
                            null, form.isAdvancedCohortSupport(), true);
                }
                return true;
            }

            // In the special case where we're switching from manual assignment to advanced assignment,
            // clear all current cohort assignments.  The user has been warned that this will happen.
            if (form.isAdvancedCohortSupport() && study.isManualCohortAssignment())
                CohortManager.getInstance().clearParticipantCohorts(study);

            if (form.isManualCohortAssignment())
            {
                if (form.isManualCohortAssignment() != study.isManualCohortAssignment())
                {
                    study.setManualCohortAssignment(form.isManualCohortAssignment());
                    StudyManager.getInstance().updateStudy(getUser(), study);

                    // We'll ignore anything else, since our whole view is about to change
                    return true;
                }

                // Update the assignments from the individual entries in the form
                int[] cohorts = form.getCohortId();
                String[] participants = form.getParticipantId();

                if (participants == null)
                {
                    // No participants -- nothing to do
                    return true;
                }
                assert cohorts.length == participants.length : "Submitted different numbers of " +
                        StudyService.get().getSubjectNounPlural(study.getContainer()) + " and cohorts";

                Map<String, Integer> p2c = new HashMap<>();

                for (int i=0; i<participants.length; i++)
                {
                    p2c.put(participants[i], cohorts[i]);
                }

                CohortManager.getInstance().setManualCohortAssignment(study, getUser(), p2c);
            }
            else
            {
                // Update all participants via the new dataset column
                // Note: we need to do this even if no changes have been made to
                // this setting, as it's possible that the user manually set some cohorts previously
                boolean updateNow = form.isUpdateParticipants() || (study.isAdvancedCohorts() != form.isAdvancedCohortSupport());
                CohortManager.getInstance().setAutomaticCohortAssignment(study, getUser(), form.getParticipantCohortDatasetId(),
                        form.getParticipantCohortProperty(), form.isAdvancedCohortSupport(), updateNow);
            }

            return true;
        }

        public ActionURL getSuccessURL(ManageCohortsForm form)
        {
            if (form.isReshow())
                return new ActionURL(ManageCohortsAction.class, getContainer());
            return new ActionURL(StudyController.ManageStudyAction.class, getContainer());
        }
    }

    public static class ManageCohortsForm
    {        
        private boolean manualCohortAssignment;
        private String[] participantId;
        private int[] cohortId;
        private Integer participantCohortDatasetId;
        private String participantCohortProperty;
        private boolean reshow;
        private boolean clearParticipants;
        private boolean updateParticipants;
        private boolean advancedCohortSupport;

        public int[] getCohortId() {return cohortId;}
        public void setCohortId(int[] cohortId) {this.cohortId = cohortId;}

        public boolean isManualCohortAssignment() {return manualCohortAssignment;}
        public void setManualCohortAssignment(boolean manualCohortAssignment) {this.manualCohortAssignment = manualCohortAssignment;}

        public boolean isAdvancedCohortSupport() {return advancedCohortSupport;}
        public void setAdvancedCohortSupport(boolean advancedCohortSupport) {this.advancedCohortSupport = advancedCohortSupport;}

        public Integer getParticipantCohortDatasetId() {return participantCohortDatasetId;}
        public void setParticipantCohortDatasetId(Integer participantCohortDatasetId) {this.participantCohortDatasetId = participantCohortDatasetId;}

        public String getParticipantCohortProperty() {return participantCohortProperty;}
        public void setParticipantCohortProperty(String participantCohortProperty) {this.participantCohortProperty = participantCohortProperty;}

        public String[] getParticipantId() {return participantId;}
        public void setParticipantId(String[] participantId) {this.participantId = participantId;}

        public boolean isReshow() {return reshow;}
        public void setReshow(boolean reshow) {this.reshow = reshow;}

        public boolean isClearParticipants()
        {
            return clearParticipants;
        }

        public void setClearParticipants(boolean clearParticipants)
        {
            this.clearParticipants = clearParticipants;
        }

        public boolean isUpdateParticipants()
        {
            return updateParticipants;
        }

        public void setUpdateParticipants(boolean updateParticipants)
        {
            this.updateParticipants = updateParticipants;
        }
    }

    private abstract class InsertUpdateAction extends FormViewAction<EditCohortForm>
    {
        protected abstract boolean isInsert();
        protected abstract NavTree appendExtraNavTrail(NavTree root);

        protected String cohortLabel; // Will be null on insert

        protected CohortTable getTableInfo()
        {
            StudyImpl study = getStudyThrowIfNull();
            StudyQuerySchema schema = StudyQuerySchema.createSchema(study, getUser(), true);
            return new CohortTable(schema);
        }

        public ModelAndView getView(EditCohortForm form, boolean reshow, BindException errors) throws Exception
        {
            TableInfo table = getTableInfo();

            if (!isInsert())
            {
                Cohort cohort = StudyManager.getInstance().getCohortForRowId(getContainer(), getUser(), form.getRowId());
                if (cohort == null)
                    throw new NotFoundException("Could not find cohort for rowId: " + form.getRowId());
                cohortLabel = cohort.getLabel();
                if (!getStudyRedirectIfNull().isManualCohortAssignment())
                {
                    // Can't edit the label if the cohorts are automatically generated
                    ColumnInfo labelColumn = table.getColumn("Label");
                    labelColumn.setUserEditable(false);
                }
            }

            // If we are reshowing because we have existing errors, don't let the QueryUpdateForm
            // duplicate them.  Instead just return the BindException we have.
            boolean suppressErrors = (reshow && (errors != null && errors.hasErrors()));
            QueryUpdateForm updateForm = new QueryUpdateForm(table, getViewContext(), suppressErrors ? null : errors);

            DataView view;
            if (isInsert())
            {
                view = new InsertView(updateForm, errors);
                // by default, cohorts are enrolled
                updateForm.set("enrolled", true);
            }
            else
            {
                view = new UpdateView(updateForm, errors);
            }
            DataRegion dataRegion = view.getDataRegion();

            // In case we reshow due to errors, we need to stuff the row id into the data region
            dataRegion.addHiddenFormField("rowId", Integer.toString(form.getRowId()));

            ActionURL cancelURL = new ActionURL(CohortController.ManageCohortsAction.class, getContainer());
            
            ButtonBar buttonBar = dataRegion.getButtonBar(DataRegion.MODE_UPDATE);
            buttonBar = new ButtonBar(buttonBar); // need to copy since the original is read-only
            ActionButton cancelButton = new ActionButton(cancelURL, "Cancel", DataRegion.MODE_UPDATE, ActionButton.Action.GET);
            cancelButton.setURL(cancelURL);
            buttonBar.add(1, cancelButton);
            if (isInsert())
            {
                // Need to update the URL to be the insert action
                buttonBar.getList().remove(0);
                buttonBar.add(0, ActionButton.BUTTON_DO_INSERT);
                cancelButton.setDisplayModes(DataRegion.MODE_INSERT);
            }
            dataRegion.setButtonBar(buttonBar);

            return view;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            _appendManageStudy(root);
            root.addChild("Manage Cohorts", new ActionURL(ManageCohortsAction.class, getContainer()));
            appendExtraNavTrail(root);
            return root;
        }

        public void validateCommand(EditCohortForm form, Errors errors) {}

        public boolean handlePost(EditCohortForm form, BindException errors) throws Exception
        {
            QueryUpdateForm updateForm = new QueryUpdateForm(getTableInfo(), getViewContext(), errors);

            if (errors.getErrorCount() > 0)
                return false;

            Map<String,Object> dataMap = updateForm.getTypedColumns();
            Object pkVal = updateForm.getPkVal();

            CohortImpl cohort;
            String newLabel = (String)dataMap.remove("label"); // remove and handle label, as it isn't an ontology object
            boolean newEnrolled = (Boolean) dataMap.remove("enrolled"); // same with enrolled
            Integer newSubjectCount = (Integer)dataMap.remove("subjectCount"); // same with subjectCount;
            String newDescription = (String)dataMap.remove("description"); // same with description;

            DbScope scope = StudySchema.getInstance().getSchema().getScope();
            try (DbScope.Transaction transaction = scope.ensureTransaction())
            {
                if (isInsert())
                {
                    Study study = getStudyThrowIfNull();
                    cohort = CohortManager.getInstance().createCohort(study, getUser(), newLabel, newEnrolled, newSubjectCount, newDescription);
                }
                else
                {
                    assert pkVal != null : "Update attempted with no primary key set";

                    int rowId;
                    try
                    {
                        rowId = Integer.parseInt(pkVal.toString());
                    }
                    catch(NumberFormatException nfe)
                    {
                        throw new IllegalStateException("Cannot parse rowId of " + pkVal);
                    }

                    cohort = StudyManager.getInstance().getCohortForRowId(getContainer(), getUser(), rowId);
                    if (cohort == null)
                        throw new IllegalStateException("Cannot find Cohort for rowId: " + rowId);

                    boolean labelChanged = (newLabel != null && !cohort.getLabel().equals(newLabel));
                    boolean enrolledChanged = cohort.isEnrolled() != newEnrolled;
                    boolean subjectCountChanged = !PropertyUtil.nullSafeEquals(cohort.getSubjectCount(), newSubjectCount);
                    boolean desciprtionChanged = !StringUtils.equals(cohort.getDescription(), newDescription);

                    if (labelChanged || enrolledChanged || subjectCountChanged || desciprtionChanged)
                    {
                        cohort = cohort.createMutable();

                        if (labelChanged)
                        {
                            // Check if there's a conflict
                            CohortImpl existingCohort = StudyManager.getInstance().getCohortByLabel(getContainer(), getUser(), newLabel);
                            if (existingCohort != null && existingCohort.getRowId() != cohort.getRowId())
                            {
                                errors.reject("insertCohort", "A cohort with the label '" + newLabel + "' already exists");
                                return false;
                            }
                            cohort.setLabel(newLabel);
                        }

                        cohort.setEnrolled(newEnrolled);
                        cohort.setSubjectCount(newSubjectCount);
                        cohort.setDescription(newDescription);

                        StudyManager.getInstance().updateCohort(getUser(), cohort);
                    }
                }
                cohort.savePropertyBag(dataMap);

                transaction.commit();
                return true;
            }
            catch (ValidationException e)
            {
                for (ValidationError error : e.getErrors())
                    errors.reject(SpringActionController.ERROR_MSG, PageFlowUtil.filter(error.getMessage()));
                return false;
            }
            catch (IllegalStateException e)
            {
                errors.reject("insertCohort", e.getMessage());
                return false;
            }
        }

        public ActionURL getSuccessURL(EditCohortForm form)
        {
            return new ActionURL(ManageCohortsAction.class, getContainer());
        }

    }

    public static class EditCohortForm
    {
        private int rowId;

        public int getRowId() {return rowId;}
        public void setRowId(int rowId) {this.rowId = rowId;}
    }

    @RequiresPermission(AdminPermission.class)
    public class InsertAction extends InsertUpdateAction
    {
        protected boolean isInsert()
        {
            return true;
        }

        protected NavTree appendExtraNavTrail(NavTree root)
        {
            root.addChild("Insert New Cohort");
            return root;
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class UpdateAction extends InsertAction
    {
        protected boolean isInsert()
        {
            return false;
        }

        protected NavTree appendExtraNavTrail(NavTree root)
        {
            root.addChild("Update Cohort: " + cohortLabel);
            return root;
        }
    }
}
