/*
 * Copyright (c) 2014-2015 LabKey Corporation
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

import org.labkey.api.action.ApiAction;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.MutatingApiAction;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.data.DbScope;
import org.labkey.api.security.ActionNames;
import org.labkey.api.security.RequiresPermissionClass;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.study.Study;
import org.labkey.api.study.Visit;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.study.StudySchema;
import org.labkey.study.model.StudyTreatmentSchedule;
import org.labkey.study.model.CohortImpl;
import org.labkey.study.model.CohortManager;
import org.labkey.study.model.ProductAntigenImpl;
import org.labkey.study.model.ProductImpl;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.model.TreatmentImpl;
import org.labkey.study.model.TreatmentManager;
import org.labkey.study.model.TreatmentProductImpl;
import org.labkey.study.model.VisitImpl;
import org.labkey.study.security.permissions.ManageStudyPermission;
import org.labkey.study.visitmanager.VisitManager;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * User: cnathe
 * Date: 1/16/14
 */
public class StudyDesignController extends BaseStudyController
{
    private static final ActionResolver ACTION_RESOLVER = new DefaultActionResolver(StudyDesignController.class);

    public StudyDesignController()
    {
        super();
        setActionResolver(ACTION_RESOLVER);
    }

    @ActionNames("manageAssaySchedule, manageAssaySpecimen")
    @RequiresPermissionClass(UpdatePermission.class)
    public class ManageAssayScheduleAction extends SimpleViewAction<AssayScheduleForm>
    {
        public ModelAndView getView(AssayScheduleForm form, BindException errors) throws Exception
        {
            return new JspView<>("/org/labkey/study/view/studydesign/manageAssaySchedule.jsp", form);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            setHelpTopic("studyDesign#assay");
            if (getContainer().hasPermission(getUser(), ManageStudyPermission.class))
                root.addChild("Manage Study", new ActionURL(StudyController.ManageStudyAction.class, getContainer()));
            return root.addChild("Manage Assay Schedule");
        }
    }

    public static class AssayScheduleForm
    {
        private boolean useAlternateLookupFields;

        public boolean isUseAlternateLookupFields()
        {
            return useAlternateLookupFields;
        }

        public void setUseAlternateLookupFields(boolean useAlternateLookupFields)
        {
            this.useAlternateLookupFields = useAlternateLookupFields;
        }
    }

    @RequiresPermissionClass(UpdatePermission.class)
    public class ManageStudyProductsAction extends SimpleViewAction<Object>
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            return new JspView<>("/org/labkey/study/view/studydesign/manageStudyProducts.jsp", o);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            setHelpTopic("studyDesign#setup");
            if (getContainer().hasPermission(getUser(), ManageStudyPermission.class))
                root.addChild("Manage Study", new ActionURL(StudyController.ManageStudyAction.class, getContainer()));
            return root.addChild("Manage Study Products");
        }
    }

    @RequiresPermissionClass(UpdatePermission.class)
    public class ManageTreatmentsAction extends SimpleViewAction<Object>
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            return new JspView<>("/org/labkey/study/view/studydesign/manageTreatments.jsp", o);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            setHelpTopic("studyDesign#immun");
            if (getContainer().hasPermission(getUser(), ManageStudyPermission.class))
                root.addChild("Manage Study", new ActionURL(StudyController.ManageStudyAction.class, getContainer()));
            return root.addChild("Manage Treatments");
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class GetStudyProducts extends ApiAction<GetStudyProductsForm>
    {
        private StudyImpl _study;

        @Override
        public void validateForm(GetStudyProductsForm form, Errors errors)
        {
            _study = getStudy(getContainer());
            if (_study == null)
                errors.reject(ERROR_MSG, "A study does not exist in this folder");
        }

        @Override
        public ApiResponse execute(GetStudyProductsForm form, BindException errors) throws Exception
        {
            ApiSimpleResponse resp = new ApiSimpleResponse();

            List<Map<String, Object>> productList = new ArrayList<>();
            List<ProductImpl> studyProducts = TreatmentManager.getInstance().getStudyProducts(getContainer(), getUser(), form.getRole(), form.getRowId());
            for (ProductImpl product : studyProducts)
            {
                // note: we are currently only inclusing the base fields for this extensible table
                Map<String, Object> productProperties = product.serialize();

                List<Map<String, Object>> productAntigenList = new ArrayList<>();
                List<ProductAntigenImpl> studyProductAntigens = TreatmentManager.getInstance().getStudyProductAntigens(getContainer(), getUser(), product.getRowId());
                for (ProductAntigenImpl antigen : studyProductAntigens)
                {
                    // note: we are currently only inclusing the base fields for this extensible table
                    productAntigenList.add(antigen.serialize());
                }
                productProperties.put("Antigens", productAntigenList);

                productList.add(productProperties);
            }

            resp.put("success", true);
            resp.put("products", productList);

            return resp;
        }
    }

    public static class GetStudyProductsForm
    {
        private Integer _rowId;
        private String _role;

        public Integer getRowId()
        {
            return _rowId;
        }

        public void setRowId(Integer rowId)
        {
            _rowId = rowId;
        }

        public String getRole()
        {
            return _role;
        }

        public void setRole(String role)
        {
            _role = role;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class GetStudyTreatments extends ApiAction<Object>
    {
        private StudyImpl _study;

        @Override
        public void validateForm(Object form, Errors errors)
        {
            _study = getStudy(getContainer());
            if (_study == null)
                errors.reject(ERROR_MSG, "A study does not exist in this folder");
        }

        @Override
        public ApiResponse execute(Object form, BindException errors) throws Exception
        {
            ApiSimpleResponse resp = new ApiSimpleResponse();

            List<Map<String, Object>> treatmentList = new ArrayList<>();
            List<TreatmentImpl> studyTreatments = TreatmentManager.getInstance().getStudyTreatments(getContainer(), getUser());
            for (TreatmentImpl treatment : studyTreatments)
            {
                // note: we are currently only inclusing the base fields for this extensible table
                Map<String, Object> treatmentProperties = treatment.serialize();

                List<Map<String, Object>> treatmentProductList = new ArrayList<>();
                List<TreatmentProductImpl> studyTreatmentProducts = TreatmentManager.getInstance().getStudyTreatmentProducts(getContainer(), getUser(), treatment.getRowId(), treatment.getProductSort());
                for (TreatmentProductImpl treatmentProduct : studyTreatmentProducts)
                {
                    // note: we are currently only inclusing the base fields for this extensible table
                    Map<String, Object> treatmentProductProperties = treatmentProduct.serialize();

                    // add the product label for convenience, to prevent the need for another round trip to the server
                    List<ProductImpl> products = TreatmentManager.getInstance().getStudyProducts(getContainer(), getUser(), null, treatmentProduct.getProductId());
                    if (products.size() == 1)
                        treatmentProductProperties.put("ProductId/Label", products.get(0).getLabel());

                    treatmentProductList.add(treatmentProductProperties);
                }
                treatmentProperties.put("Products", treatmentProductList);

                treatmentList.add(treatmentProperties);
            }

            resp.put("success", true);
            resp.put("treatments", treatmentList);

            return resp;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class GetStudyTreatmentSchedule extends ApiAction<Object>
    {
        private StudyImpl _study;

        @Override
        public void validateForm(Object form, Errors errors)
        {
            _study = getStudy(getContainer());
            if (_study == null)
                errors.reject(ERROR_MSG, "A study does not exist in this folder");
        }

        @Override
        public ApiResponse execute(Object form, BindException errors) throws Exception
        {
            ApiSimpleResponse resp = new ApiSimpleResponse();
            StudyTreatmentSchedule treatmentSchedule = new StudyTreatmentSchedule(getContainer());

            // include all cohorts for the study, regardless of it they have associated visits or not
            treatmentSchedule.setCohorts(StudyManager.getInstance().getCohorts(getContainer(), getUser()));

            // include all visits from the study, ordered by visit display order
            treatmentSchedule.setVisits(StudyManager.getInstance().getVisits(_study, Visit.Order.DISPLAY));

            // include all treatments for the study
            treatmentSchedule.setTreatments(TreatmentManager.getInstance().getStudyTreatments(getContainer(), getUser()));

            resp.put("mapping", treatmentSchedule.serializeCohortMapping());
            resp.put("visits", treatmentSchedule.serializeVisits());
            resp.put("treatments", treatmentSchedule.serializeTreatments());
            resp.put("success", true);

            return resp;
        }
    }

    @RequiresPermissionClass(DeletePermission.class)
    public class DeleteTreatmentAction extends MutatingApiAction<IdForm>
    {
        @Override
        public ApiResponse execute(IdForm form, BindException errors) throws Exception
        {
            if (form.getId() != 0)
            {
                TreatmentManager.getInstance().deleteTreatment(getContainer(), getUser(), form.getId());
                return new ApiSimpleResponse("success", true);
            }
            return new ApiSimpleResponse("success", false);
        }
    }

    @RequiresPermissionClass(DeletePermission.class)
    public class DeleteStudyProductAction extends MutatingApiAction<IdForm>
    {
        @Override
        public ApiResponse execute(IdForm form, BindException errors) throws Exception
        {
            if (form.getId() != 0)
            {
                TreatmentManager.getInstance().deleteStudyProduct(getContainer(), getUser(), form.getId());
                return new ApiSimpleResponse("success", true);
            }
            return new ApiSimpleResponse("success", false);
        }
    }

    @RequiresPermissionClass(UpdatePermission.class)
    public class UpdateStudyTreatmentScheduleAction extends MutatingApiAction<StudyTreatmentSchedule>
    {
        @Override
        public void validateForm(StudyTreatmentSchedule form, Errors errors)
        {
            if (form.getCohortLabel() == null)
                errors.reject(ERROR_MSG, "Cohort label is required.");

            CohortImpl cohortByLabel = StudyManager.getInstance().getCohortByLabel(getContainer(), getUser(), form.getCohortLabel());
            if (form.getCohortRowId() != null)
            {
                CohortImpl cohortByRowId = StudyManager.getInstance().getCohortForRowId(getContainer(), getUser(), form.getCohortRowId());
                if (cohortByRowId != null && cohortByLabel != null && cohortByRowId.getRowId() != cohortByLabel.getRowId())
                    errors.reject(ERROR_MSG, "A cohort with the label '" + form.getCohortLabel() + "' already exists");
            }
            else if (cohortByLabel != null)
            {
                errors.reject(ERROR_MSG, "A cohort with the label '" + form.getCohortLabel() + "' already exists");
            }
        }

        @Override
        public ApiResponse execute(StudyTreatmentSchedule form, BindException errors) throws Exception
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            Study study = StudyManager.getInstance().getStudy(getContainer());

            if (study != null)
            {
                CohortImpl cohort = insertOrUpdateCohort(form, study);
                response.put("cohortId", cohort.getRowId());

                updateTreatmentVisitMapping(form, cohort);

                response.put("success", true);
                return response;
            }
            else
                throw new IllegalStateException("A study does not exist in this folder");
        }

        private CohortImpl insertOrUpdateCohort(StudyTreatmentSchedule form, Study study) throws Exception
        {
            CohortImpl cohort;
            if (form.getCohortRowId() != null)
            {
                cohort = StudyManager.getInstance().getCohortForRowId(getContainer(), getUser(), form.getCohortRowId());
                cohort = cohort.createMutable();
                cohort.setLabel(form.getCohortLabel());
                cohort.setSubjectCount(form.getCohortSubjectCount());
                StudyManager.getInstance().updateCohort(getUser(), cohort);
            }
            else
            {
                cohort = CohortManager.getInstance().createCohort(study, getUser(), form.getCohortLabel(), true, form.getCohortSubjectCount(), null);
            }

            return cohort;
        }

        private void updateTreatmentVisitMapping(StudyTreatmentSchedule form, CohortImpl cohort)
        {
            if (cohort != null)
            {
                StudySchema schema = StudySchema.getInstance();

                try (DbScope.Transaction transaction = schema.getSchema().getScope().ensureTransaction())
                {
                    // the mapping that is passed in will have all of the current treatment/visit maps, so we will
                    // delete all of the existing records and then insert the new ones
                    TreatmentManager.getInstance().deleteTreatmentVisitMapForCohort(getContainer(), cohort.getRowId());

                    for (Map.Entry<Integer, Integer> treatmentVisitMap : form.getTreatmentVisitMap().entrySet())
                    {
                        // map entry key = visitId and value = treatmentId
                        TreatmentManager.getInstance().insertTreatmentVisitMap(getUser(), getContainer(), cohort.getRowId(), treatmentVisitMap.getKey(), treatmentVisitMap.getValue());
                    }

                    transaction.commit();
                }
            }
        }
    }

    @RequiresPermissionClass(DeletePermission.class)
    public class DeleteCohortAction extends MutatingApiAction<IdForm>
    {
        @Override
        public ApiResponse execute(IdForm form, BindException errors) throws Exception
        {
            if (form.getId() != 0)
            {
                CohortImpl cohort = StudyManager.getInstance().getCohortForRowId(getContainer(), getUser(), form.getId());
                if (cohort != null)
                {
                    if (!cohort.isInUse())
                    {
                        StudyManager.getInstance().deleteCohort(cohort);
                        return new ApiSimpleResponse("success", true);
                    }
                    else
                    {
                        errors.reject(ERROR_MSG, "Unable to delete in-use cohort: " + cohort.getLabel());
                    }
                }
                else
                {
                    errors.reject(ERROR_MSG, "Unable to find cohort for id " + form.getId());
                }
            }
            return new ApiSimpleResponse("success", false);
        }
    }

    @RequiresPermissionClass(UpdatePermission.class)
    public class CreateVisitAction extends MutatingApiAction<VisitForm>
    {
        @Override
        public void validateForm(VisitForm form, Errors errors)
        {
            StudyImpl study = getStudyRedirectIfNull();

            form.validate(errors, study);
            if (errors.getErrorCount() > 0)
                return;

            //check for overlapping visits
            VisitManager visitMgr = StudyManager.getInstance().getVisitManager(study);
            if (null != visitMgr)
            {
                if (visitMgr.isVisitOverlapping(form.getBean()))
                    errors.reject(null, "Visit range overlaps an existing visit in this study. Please enter a different range.");
            }
        }

        @Override
        public ApiResponse execute(VisitForm form, BindException errors) throws Exception
        {
            ApiSimpleResponse response = new ApiSimpleResponse();

            VisitImpl visit = form.getBean();
            visit = StudyManager.getInstance().createVisit(getStudyThrowIfNull(), getUser(), visit);

            response.put("RowId", visit.getRowId());
            response.put("Label", visit.getDisplayString());
            response.put("SortOrder", visit.getDisplayOrder());
            response.put("Included", true);
            response.put("success", true);
            return response;
        }
    }

    @RequiresPermissionClass(UpdatePermission.class)
    public class UpdateAssayPlanAction extends MutatingApiAction<AssayPlanForm>
    {
        @Override
        public ApiResponse execute(AssayPlanForm form, BindException errors) throws Exception
        {
            ApiSimpleResponse response = new ApiSimpleResponse();

            StudyImpl study = StudyManager.getInstance().getStudy(getContainer());
            if (study != null)
            {
                study = study.createMutable();
                study.setAssayPlan(form.getAssayPlan());
                StudyManager.getInstance().updateStudy(getUser(), study);
                response.put("success", true);
            }
            else
            {
                response.put("success", false);
            }

            return response;
        }
    }

    private static class AssayPlanForm
    {
        private String _assayPlan;

        public AssayPlanForm()
        {}

        public String getAssayPlan()
        {
            return _assayPlan;
        }

        public void setAssayPlan(String assayPlan)
        {
            _assayPlan = assayPlan;
        }
    }
}
