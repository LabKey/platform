/*
 * Copyright (c) 2014-2019 LabKey Corporation
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
package org.labkey.studydesign;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.action.ApiJsonForm;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.MutatingApiAction;
import org.labkey.api.action.ReadOnlyApiAction;
import org.labkey.api.action.ReturnUrlForm;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.ActionNames;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AbstractContainerScopingTest;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.study.Cohort;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.StudyUrls;
import org.labkey.api.study.Visit;
import org.labkey.api.study.model.CohortService;
import org.labkey.api.study.security.permissions.ManageStudyPermission;
import org.labkey.api.studydesign.StudyDesignUrls;
import org.labkey.api.studydesign.query.StudyDesignSchema;
import org.labkey.api.util.JsonUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.studydesign.model.AssaySpecimenConfigImpl;
import org.labkey.studydesign.model.AssaySpecimenVisitImpl;
import org.labkey.studydesign.model.DoseAndRoute;
import org.labkey.studydesign.model.ProductAntigenImpl;
import org.labkey.studydesign.model.ProductImpl;
import org.labkey.studydesign.model.StudyAssaySchedule;
import org.labkey.studydesign.model.StudyDesignCohort;
import org.labkey.studydesign.model.StudyTreatmentSchedule;
import org.labkey.studydesign.model.TreatmentImpl;
import org.labkey.studydesign.model.TreatmentManager;
import org.labkey.studydesign.model.TreatmentProductImpl;
import org.labkey.studydesign.model.TreatmentVisitMapImpl;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class StudyDesignController extends SpringActionController
{
    private static final SpringActionController.ActionResolver ACTION_RESOLVER = new SpringActionController.DefaultActionResolver(StudyDesignController.class);

    public StudyDesignController()
    {
        setActionResolver(ACTION_RESOLVER);
    }

    public static class StudyDesignUrlsImpl implements StudyDesignUrls
    {
        @Override
        public ActionURL getManageAssayScheduleURL(Container container, boolean useAlternateLookupFields)
        {
            ActionURL url = new ActionURL(ManageAssayScheduleAction.class, container);
            url.addParameter("useAlternateLookupFields", useAlternateLookupFields);
            return url;
        }

        @Override
        public ActionURL getManageStudyProductsURL(Container container)
        {
            return new ActionURL(ManageStudyProductsAction.class, container);
        }

        @Override
        public ActionURL getManageTreatmentsURL(Container container, boolean useSingleTableEditor)
        {
            ActionURL url = new ActionURL(ManageTreatmentsAction.class, container);
            url.addParameter("singleTable", useSingleTableEditor);
            return url;
        }
    }

    @Nullable
    private Study getStudy(Container container)
    {
        return StudyService.get().getStudy(container);
    }

    @ActionNames("manageAssaySchedule, manageAssaySpecimen")
    @RequiresPermission(UpdatePermission.class)
    public static class ManageAssayScheduleAction extends SimpleViewAction<AssayScheduleForm>
    {
        @Override
        public ModelAndView getView(AssayScheduleForm form, BindException errors)
        {
            return new JspView<>("/org/labkey/studydesign/view/manageAssaySchedule.jsp", form);
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            setHelpTopic("manageAssaySchedule");
            if (getContainer().hasPermission(getUser(), ManageStudyPermission.class))
                root.addChild("Manage Study", PageFlowUtil.urlProvider(StudyUrls.class).getManageStudyURL(getContainer()));
            root.addChild("Manage Assay Schedule");
        }
    }

    public static class AssayScheduleForm extends ReturnUrlForm
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

    @RequiresPermission(UpdatePermission.class)
    public static class ManageStudyProductsAction extends SimpleViewAction<ReturnUrlForm>
    {
        @Override
        public ModelAndView getView(ReturnUrlForm form, BindException errors)
        {
            return new JspView<>("/org/labkey/studydesign/view/manageStudyProducts.jsp", form);
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            setHelpTopic("studyProducts");
            if (getContainer().hasPermission(getUser(), ManageStudyPermission.class))
                root.addChild("Manage Study", PageFlowUtil.urlProvider(StudyUrls.class).getManageStudyURL(getContainer()));
            root.addChild("Manage Study Products");
        }
    }

    public static class ManageTreatmentsBean extends ReturnUrlForm
    {
        private boolean _singleTable;

        public boolean isSingleTable()
        {
            return _singleTable;
        }

        public void setSingleTable(boolean singleTable)
        {
            _singleTable = singleTable;
        }
    }

    @RequiresPermission(UpdatePermission.class)
    public static class ManageTreatmentsAction extends SimpleViewAction<ManageTreatmentsBean>
    {
        @Override
        public ModelAndView getView(ManageTreatmentsBean form, BindException errors)
        {
            // if the singleTable param is not explicitly set, do a container check
            if (getViewContext().getRequest().getParameter("singleTable") == null)
                form.setSingleTable(getContainer().hasActiveModuleByName("viscstudies"));

            return new JspView<>("/org/labkey/studydesign/view/manageTreatments.jsp", form);
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            setHelpTopic("manageTreatments");
            if (getContainer().hasPermission(getUser(), ManageStudyPermission.class))
                root.addChild("Manage Study", PageFlowUtil.urlProvider(StudyUrls.class).getManageStudyURL(getContainer()));
            root.addChild("Manage Treatments");
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class GetStudyProducts extends ReadOnlyApiAction<GetStudyProductsForm>
    {
        private Study _study;

        @Override
        public void validateForm(GetStudyProductsForm form, Errors errors)
        {
            _study = getStudy(getContainer());
            if (_study == null)
                errors.reject(SpringActionController.ERROR_MSG, "A study does not exist in this folder");
        }

        @Override
        public ApiResponse execute(GetStudyProductsForm form, BindException errors)
        {
            ApiSimpleResponse resp = new ApiSimpleResponse();

            List<Map<String, Object>> productList = new ArrayList<>();
            List<ProductImpl> studyProducts = TreatmentManager.getInstance().getStudyProducts(getContainer(), getUser(), form.getRole(), form.getRowId());
            for (ProductImpl product : studyProducts)
            {
                // note: we are currently only including the base fields for this extensible table
                Map<String, Object> productProperties = product.serialize();

                List<Map<String, Object>> productAntigenList = new ArrayList<>();
                List<ProductAntigenImpl> studyProductAntigens = TreatmentManager.getInstance().getStudyProductAntigens(getContainer(), getUser(), product.getRowId());
                for (ProductAntigenImpl antigen : studyProductAntigens)
                {
                    // note: we are currently only including the base fields for this extensible table
                    productAntigenList.add(antigen.serialize());
                }
                productProperties.put("Antigens", productAntigenList);

                // get dose and route information associated with this product
                List<Map<String, Object>> doseAndRoutes = TreatmentManager.getInstance().getStudyProductsDoseAndRoute(getContainer(), getUser(), product.getRowId())
                        .stream()
                        .map(DoseAndRoute::serialize)
                        .collect(Collectors.toList());
                productProperties.put("DoseAndRoute", doseAndRoutes);
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

    @RequiresPermission(ReadPermission.class)
    public class GetStudyTreatments extends ReadOnlyApiAction<GetStudyTreatmentsForm>
    {
        private Study _study;

        @Override
        public void validateForm(GetStudyTreatmentsForm form, Errors errors)
        {
            _study = getStudy(getContainer());
            if (_study == null)
                errors.reject(SpringActionController.ERROR_MSG, "A study does not exist in this folder");
        }

        @Override
        public ApiResponse execute(GetStudyTreatmentsForm form, BindException errors)
        {
            ApiSimpleResponse resp = new ApiSimpleResponse();

            List<Map<String, Object>> treatmentList = new ArrayList<>();
            List<TreatmentImpl> studyTreatments = TreatmentManager.getInstance().getStudyTreatments(getContainer(), getUser());
            for (TreatmentImpl treatment : studyTreatments)
            {
                if (form.getTreatmentId() > 0 && form.getTreatmentId() != treatment.getRowId())
                    continue;

                Map<String, Object> treatmentProperties = treatment.serialize();

                List<Map<String, Object>> treatmentProductList = new ArrayList<>();
                List<TreatmentProductImpl> studyTreatmentProducts = TreatmentManager.getInstance().getStudyTreatmentProducts(getContainer(), getUser(), treatment.getRowId(), treatment.getProductSort());
                for (TreatmentProductImpl treatmentProduct : studyTreatmentProducts)
                {
                    // note: we are currently only including the base fields for this extensible table
                    Map<String, Object> treatmentProductProperties = treatmentProduct.serialize();

                    // add the product label and role for convenience, to prevent the need for another round trip to the server
                    List<ProductImpl> products = TreatmentManager.getInstance().getStudyProducts(getContainer(), getUser(), null, treatmentProduct.getProductId());
                    if (products.size() == 1)
                    {
                        treatmentProductProperties.put("ProductId/Label", products.get(0).getLabel());
                        treatmentProductProperties.put("ProductId/Role", products.get(0).getRole());
                    }

                    treatmentProductList.add(treatmentProductProperties);
                }

                if (!form.isSplitByRole())
                {
                    treatmentProperties.put("Products", treatmentProductList);
                }
                else
                {
                    Map<String, List<Map<String, Object>>> treatmentProductsListByRole = new HashMap<>();
                    for (Map<String, Object> productProperties : treatmentProductList)
                    {
                        String role = productProperties.get("ProductId/Role").toString();
                        if (!treatmentProductsListByRole.containsKey(role))
                            treatmentProductsListByRole.put(role, new ArrayList<>());

                        treatmentProductsListByRole.get(role).add(productProperties);
                    }

                    for (Map.Entry<String, List<Map<String, Object>>> entry : treatmentProductsListByRole.entrySet())
                        treatmentProperties.put(entry.getKey(), entry.getValue());
                }

                treatmentList.add(treatmentProperties);
            }

            resp.put("success", true);
            resp.put("treatments", treatmentList);

            return resp;
        }
    }

    private static class GetStudyTreatmentsForm
    {
        private boolean _splitByRole;

        private int treatmentId;

        public boolean isSplitByRole()
        {
            return _splitByRole;
        }

        public void setSplitByRole(boolean splitByRole)
        {
            _splitByRole = splitByRole;
        }

        public int getTreatmentId()
        {
            return treatmentId;
        }

        public void setTreatmentId(int treatmentId)
        {
            this.treatmentId = treatmentId;
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class GetStudyTreatmentSchedule extends ReadOnlyApiAction<Object>
    {
        private Study _study;

        @Override
        public void validateForm(Object form, Errors errors)
        {
            _study = getStudy(getContainer());
            if (_study == null)
                errors.reject(SpringActionController.ERROR_MSG, "A study does not exist in this folder");
        }

        @Override
        public ApiResponse execute(Object form, BindException errors)
        {
            ApiSimpleResponse resp = new ApiSimpleResponse();
            StudyTreatmentSchedule treatmentSchedule = new StudyTreatmentSchedule(getContainer());

            // include all cohorts for the study, regardless of it they have associated visits or not
            resp.put("cohorts", treatmentSchedule.serializeCohortMapping(_study.getCohorts(getUser())));

            // include all visits from the study, ordered by visit display order
            treatmentSchedule.setVisits(_study.getVisits(Visit.Order.DISPLAY));
            resp.put("visits", treatmentSchedule.serializeVisits());

            resp.put("success", true);
            return resp;
        }
    }

    @RequiresPermission(UpdatePermission.class)
    public class UpdateStudyProductsAction extends MutatingApiAction<StudyProductsForm>
    {
        @Override
        public void validateForm(StudyProductsForm form, Errors errors)
        {
            if (form.getProducts() == null)
                errors.reject(SpringActionController.ERROR_MSG, "No study products provided.");

            // label field is required
            for (ProductImpl product : form.getProducts())
            {
                if (product.getLabel() == null || StringUtils.isEmpty(product.getLabel().trim()))
                {
                    errors.reject(SpringActionController.ERROR_MSG, "Label is a required field for all study products.");
                    break;
                }
            }
        }

        @Override
        public ApiResponse execute(StudyProductsForm form, BindException errors) throws Exception
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            Study study = getStudy(getContainer());

            if (study != null)
            {
                StudyDesignSchema schema = StudyDesignSchema.getInstance();

                try (DbScope.Transaction transaction = schema.getSchema().getScope().ensureTransaction())
                {
                    updateProducts(form.getProducts());
                    transaction.commit();
                }

                response.put("success", true);
                return response;
            }
            else
                throw new IllegalStateException("A study does not exist in this folder");
        }

        private void updateProducts(List<ProductImpl> products) throws Exception
        {
            // insert new study products and update any existing ones
            List<Integer> productRowIds = new ArrayList<>();
            for (ProductImpl product : products)
            {
                Integer updatedRowId = TreatmentManager.getInstance().saveStudyProduct(getContainer(), getUser(), product);
                if (updatedRowId != null)
                {
                    productRowIds.add(updatedRowId);

                    updateProductAntigens(updatedRowId, product.getAntigens());
                    updateProductDoseAndRoutes(updatedRowId, product.getDoseAndRoutes());
                }
            }

            // delete any other study products, not included in the insert/update list, by RowId for this container
            for (ProductImpl product : TreatmentManager.getInstance().getFilteredStudyProducts(getContainer(), getUser(), productRowIds))
                TreatmentManager.getInstance().deleteStudyProduct(getContainer(), getUser(), product.getRowId());
        }

        private void updateProductAntigens(int productId, List<ProductAntigenImpl> antigens) throws Exception
        {
            // insert new study products antigens and update any existing ones
            List<Integer> antigenRowIds = new ArrayList<>();
            for (ProductAntigenImpl antigen : antigens)
            {
                // make sure the productId is set based on the product rowId
                antigen.setProductId(productId);

                Integer updatedRowId = TreatmentManager.getInstance().saveStudyProductAntigen(getContainer(), getUser(), antigen);
                if (updatedRowId != null)
                    antigenRowIds.add(updatedRowId);
            }

            // delete any other study products antigens, not included in the insert/update list, for the given productId
            for (ProductAntigenImpl antigen : TreatmentManager.getInstance().getFilteredStudyProductAntigens(getContainer(), getUser(), productId, antigenRowIds))
                TreatmentManager.getInstance().deleteStudyProductAntigen(getContainer(), getUser(), antigen.getRowId());
        }

        private void updateProductDoseAndRoutes(int productId, List<DoseAndRoute> doseAndRoutes)
        {
            // get existing dose and routes
            Set<Integer> existingDoseAndRoutes = TreatmentManager.getInstance().getStudyProductsDoseAndRoute(getContainer(), getUser(), productId)
                    .stream()
                    .map(DoseAndRoute::getRowId)
                    .collect(Collectors.toSet());

            try (DbScope.Transaction transaction = StudyDesignSchema.getInstance().getScope().ensureTransaction())
            {
                for (DoseAndRoute doseAndRoute : doseAndRoutes)
                {
                    // dose and route both can't be blank
                    if (doseAndRoute.getDose() != null || doseAndRoute.getRoute() != null)
                    {
                        doseAndRoute.setProductId(productId);
                        existingDoseAndRoutes.remove(doseAndRoute.getRowId());
                        TreatmentManager.getInstance().saveStudyProductDoseAndRoute(getContainer(), getUser(), doseAndRoute);
                    }
                }

                // remove deleted dose and routes
                if (!existingDoseAndRoutes.isEmpty())
                {
                    SimpleFilter filter = new SimpleFilter();
                    filter.addInClause(FieldKey.fromParts("RowId"), existingDoseAndRoutes);
                    Table.delete(StudyDesignSchema.getInstance().getTableInfoDoseAndRoute(), filter);
                }
                transaction.commit();
            }
        }
    }

    public static class StudyProductsForm implements ApiJsonForm
    {
        private List<ProductImpl> _products;

        public List<ProductImpl> getProducts()
        {
            return _products;
        }

        public void setProducts(List<ProductImpl> products)
        {
            _products = products;
        }

        @Override
        public void bindJson(JSONObject json)
        {
            Container container = HttpView.currentContext().getContainer();

            JSONArray productsJSON = json.optJSONArray("products");
            if (productsJSON != null)
            {
                _products = new ArrayList<>();
                for (JSONObject product : JsonUtil.toJSONObjectList(productsJSON))
                    _products.add(ProductImpl.fromJSON(product, container));
            }
        }
    }

    @RequiresPermission(UpdatePermission.class)
    public class UpdateTreatmentsAction extends MutatingApiAction<StudyTreatmentSchedule>
    {
        @Override
        public ApiResponse execute(StudyTreatmentSchedule form, BindException errors) throws Exception
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            Study study = getStudy(getContainer());

            if (study != null)
            {
                StudyDesignSchema schema = StudyDesignSchema.getInstance();
                List<Integer> treatmentIds;
                try (DbScope.Transaction transaction = schema.getSchema().getScope().ensureTransaction())
                {
                    treatmentIds = updateTreatments(form.getTreatments());
                    transaction.commit();
                }

                response.put("success", true);
                response.put("treatmentIds", treatmentIds);
                return response;
            }
            else
                throw new IllegalStateException("A study does not exist in this folder");
        }

        private Integer getExistingTreatmentId(TreatmentImpl treatment)
        {
            if (treatment == null)
                return -1;
            List<TreatmentImpl> studyTreatments = TreatmentManager.getInstance().getStudyTreatments(getContainer(), getUser());
            for (TreatmentImpl existingTreatment : studyTreatments)
            {
                List<TreatmentProductImpl> studyTreatmentProducts = TreatmentManager.getInstance().getStudyTreatmentProducts(getContainer(), getUser(), existingTreatment.getRowId(), existingTreatment.getProductSort());
                for (TreatmentProductImpl product : studyTreatmentProducts)
                {
                    product.serialize();
                }
                existingTreatment.setTreatmentProducts(studyTreatmentProducts);
                if (treatment.isSameTreatmentProductsWith(existingTreatment))
                    return existingTreatment.getRowId();
            }
            return -1;
        }

        private List<Integer> updateTreatments(List<TreatmentImpl> treatments) throws Exception
        {
            List<Integer> updatedRowIds = new ArrayList<>();
            for (TreatmentImpl treatment : treatments)
            {
                Integer updatedRowId = getExistingTreatmentId(treatment);
                if (updatedRowId == null || updatedRowId <= 0)
                {
                    updatedRowId = TreatmentManager.getInstance().saveTreatment(getContainer(), getUser(), treatment);
                    if (updatedRowId != null)
                    {
                        TreatmentManager.getInstance().updateTreatmentProducts(updatedRowId, treatment.getTreatmentProducts(), getContainer(), getUser());
                    }
                }
                updatedRowIds.add(updatedRowId);
            }
            return updatedRowIds;
        }
    }

    @RequiresPermission(UpdatePermission.class)
    public class UpdateTreatmentScheduleAction extends MutatingApiAction<StudyTreatmentSchedule>
    {
        private final Map<String, Integer> _tempTreatmentIdMap = new HashMap<>();
        private final Set<Integer> usedTreatmentIds = new HashSet<>(); // treatmentIds referenced in single table Treatment Schedule UI
        private final List<Integer> treatmentRowIds = new ArrayList<>(); // treatmentIds defined in 2 table UI's Treatment section
        private final List<Integer> cohortRowIds = new ArrayList<>();

        @Override
        public void validateForm(StudyTreatmentSchedule form, Errors errors)
        {
            // validate that each treatment has a label
            for (TreatmentImpl treatment : form.getTreatments())
            {
                if (treatment.getLabel() == null || StringUtils.isEmpty(treatment.getLabel().trim()))
                    errors.reject(SpringActionController.ERROR_MSG, "Label is a required field for all treatments.");

                // validate that each treatment product mapping has a selected product
                for (TreatmentProductImpl treatmentProduct : treatment.getTreatmentProducts())
                {
                    if (treatmentProduct.getProductId() <= 0)
                        errors.reject(SpringActionController.ERROR_MSG, "Each treatment product must have a selected study product.");
                }
            }

            // validate that each cohort has a label, is unique, and has a valid subject count value
            for (StudyDesignCohort cohort : form.getCohorts())
            {
                if (cohort.getLabel() == null || StringUtils.isEmpty(cohort.getLabel().trim()))
                    errors.reject(SpringActionController.ERROR_MSG, "Label is a required field for all cohorts.");

                Cohort cohortByLabel = CohortService.get().getCohortByLabel(getContainer(), getUser(), cohort.getLabel());
                if (cohort.getRowId() > 0)
                {
                    Cohort cohortByRowId = CohortService.get().getCohortForRowId(getContainer(), getUser(), cohort.getRowId());
                    if (cohortByRowId != null && cohortByLabel != null && cohortByRowId.getRowId() != cohortByLabel.getRowId())
                        errors.reject(SpringActionController.ERROR_MSG, "A cohort with the label '" + cohort.getLabel() + "' already exists in this study.");
                }
                else if (cohortByLabel != null)
                {
                    errors.reject(SpringActionController.ERROR_MSG, "A cohort with the label '" + cohort.getLabel() + "' already exists in this study.");
                }

                if (cohort.getSubjectCount() != null)
                {
                    if (cohort.getSubjectCount() < 0)
                        errors.reject(SpringActionController.ERROR_MSG, "Cohort subject count values must be a positive integer.");
                    if (cohort.getSubjectCount() == Integer.MAX_VALUE)
                        errors.reject(SpringActionController.ERROR_MSG, "Cohort subject count value larger than the max value allowed.");
                }
            }
        }

        @Override
        public ApiResponse execute(StudyTreatmentSchedule form, BindException errors) throws Exception
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            Study study = getStudy(getContainer());

            if (study != null)
            {
                StudyDesignSchema schema = StudyDesignSchema.getInstance();

                try (DbScope.Transaction transaction = schema.getSchema().getScope().ensureTransaction())
                {
                    updateTreatments(form.getTreatments());
                    updateCohorts(form.getCohorts(), study);
                    cleanTreatments();
                    cleanCohorts();
                    transaction.commit();
                }

                response.put("success", true);
                return response;
            }
            else
                throw new IllegalStateException("A study does not exist in this folder");
        }

        private void cleanCohorts() throws ValidationException
        {
            // delete any other study cohorts, not included in the insert/update list, by RowId for this container
            for (Cohort existingCohort : CohortService.get().getCohorts(getContainer(), getUser()))
            {
                if (!cohortRowIds.contains(existingCohort.getRowId()))
                {
                    if (!existingCohort.isInUse())
                        CohortService.get().deleteCohort(existingCohort);
                    else
                        throw new ValidationException("Unable to delete in-use cohort: " + existingCohort.getLabel());
                }
            }

        }

        private void cleanTreatments()
        {
            // delete any other study treatments, not included in the insert/update list, by RowId for this container
            for (TreatmentImpl treatment : TreatmentManager.getInstance().getFilteredTreatments(getContainer(), getUser(), treatmentRowIds, usedTreatmentIds))
                TreatmentManager.getInstance().deleteTreatment(getContainer(), getUser(), treatment.getRowId());

        }

        private void updateTreatments(List<TreatmentImpl> treatments) throws Exception
        {
            // insert new study treatments and update any existing ones

            for (TreatmentImpl treatment : treatments)
            {
                Integer updatedRowId = TreatmentManager.getInstance().saveTreatment(getContainer(), getUser(), treatment);
                if (updatedRowId != null)
                {
                    treatmentRowIds.add(updatedRowId);

                    if (treatment.getTempRowId() != null)
                        _tempTreatmentIdMap.put(treatment.getTempRowId(), updatedRowId);

                    TreatmentManager.getInstance().updateTreatmentProducts(updatedRowId, treatment.getTreatmentProducts(), getContainer(), getUser());
                }
            }
        }

        private void updateCohorts(Collection<StudyDesignCohort> cohorts, Study study) throws ValidationException
        {
            // insert new cohorts and update any existing ones
            for (StudyDesignCohort cohort : cohorts)
            {
                int rowId = cohort.getRowId();
                if (rowId > 0)
                {
                    CohortService.get().updateCohort(getContainer(), getUser(), rowId, cohort.getLabel(), cohort.getSubjectCount());
                    cohortRowIds.add(rowId);
                }
                else
                {
                    Cohort newCohort = CohortService.get().createCohort(study, getUser(), cohort.getLabel(), true, cohort.getSubjectCount(), null);
                    cohortRowIds.add(newCohort.getRowId());
                    rowId = newCohort.getRowId();
                }

                updateTreatmentVisitMap(rowId, cohort.getTreatmentVisitMap());
            }
        }

        private void updateTreatmentVisitMap(int cohortId, List<TreatmentVisitMapImpl> treatmentVisitMaps)
        {
            for (TreatmentVisitMapImpl visitMap : treatmentVisitMaps)
            {
                usedTreatmentIds.add(visitMap.getTreatmentId());
            }

            // the mapping that is passed in will have all of the current treatment/visit maps, so we will compare
            // this set with the set from the DB and if they are different, replace all
            List<TreatmentVisitMapImpl> existingVisitMaps = TreatmentManager.getInstance().getStudyTreatmentVisitMap(getContainer(), cohortId);
            boolean visitMapsDiffer = existingVisitMaps.size() != treatmentVisitMaps.size();
            if (!visitMapsDiffer)
            {
                for (TreatmentVisitMapImpl newVisitMap : treatmentVisitMaps)
                {
                    newVisitMap.setContainer(getContainer());
                    newVisitMap.setCohortId(cohortId);

                    if (!existingVisitMaps.contains(newVisitMap))
                    {
                        visitMapsDiffer = true;
                        break;
                    }
                }
            }

            // if we have differences, replace all at this point
            if (visitMapsDiffer)
            {
                TreatmentManager.getInstance().deleteTreatmentVisitMapForCohort(getContainer(), cohortId);
                for (TreatmentVisitMapImpl newVisitMap : treatmentVisitMaps)
                {
                    // if the treatmentId used here was from a treatment that was created as part of this transaction,
                    // lookup the new treatment record RowId from the tempRowId
                    if (newVisitMap.getTempTreatmentId() != null && _tempTreatmentIdMap.containsKey(newVisitMap.getTempTreatmentId()))
                        newVisitMap.setTreatmentId(_tempTreatmentIdMap.get(newVisitMap.getTempTreatmentId()));

                    if (cohortId > 0 && newVisitMap.getVisitId() > 0 && newVisitMap.getTreatmentId() > 0)
                        TreatmentManager.getInstance().insertTreatmentVisitMap(getUser(), getContainer(), cohortId, newVisitMap.getVisitId(), newVisitMap.getTreatmentId());
                }
            }
        }
    }

    @RequiresPermission(UpdatePermission.class)
    public class UpdateAssayPlanAction extends MutatingApiAction<AssayPlanForm>
    {
        @Override
        public ApiResponse execute(AssayPlanForm form, BindException errors)
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            Study study = StudyService.get().getStudy(getContainer());
            if (study != null)
            {
                updateAssayPlan(getUser(), study, form.getAssayPlan());
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

    @RequiresPermission(UpdatePermission.class)
    public class UpdateAssayScheduleAction extends MutatingApiAction<StudyAssaySchedule>
    {
        @Override
        public void validateForm(StudyAssaySchedule form, Errors errors)
        {
            // validate that each assay configuration has an AssayName
            for (AssaySpecimenConfigImpl assay : form.getAssays())
            {
                if (assay.getAssayName() == null || StringUtils.isEmpty(assay.getAssayName().trim()))
                    errors.reject(SpringActionController.ERROR_MSG, "Assay Name is a required field for all assay configurations.");

                if (assay.getSampleQuantity() != null && assay.getSampleQuantity() < 0)
                    errors.reject(SpringActionController.ERROR_MSG, "Assay sample quantity value must be a positive number.");
            }
        }

        @Override
        public ApiResponse execute(StudyAssaySchedule form, BindException errors) throws Exception
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            Study study = getStudy(getContainer());

            if (study != null)
            {
                StudyDesignSchema schema = StudyDesignSchema.getInstance();

                try (DbScope.Transaction transaction = schema.getSchema().getScope().ensureTransaction())
                {
                    updateAssays(form.getAssays());
                    updateAssayPlan(getUser(), study, form.getAssayPlan());
                    transaction.commit();
                }

                response.put("success", true);
                return response;
            }
            else
                throw new IllegalStateException("A study does not exist in this folder");
        }

        private void updateAssays(List<AssaySpecimenConfigImpl> assays) throws Exception
        {
            // insert new assaySpecimens and update any existing ones
            List<Integer> assaySpecimenRowIds = new ArrayList<>();
            for (AssaySpecimenConfigImpl assay : assays)
            {
                Integer updatedRowId = TreatmentManager.getInstance().saveAssaySpecimen(getContainer(), getUser(), assay);
                if (updatedRowId != null)
                {
                    assaySpecimenRowIds.add(updatedRowId);

                    updateAssayVisitMap(updatedRowId, assay.getAssayVisitMap());
                }
            }

            // delete any other assaySpecimens, not included in the insert/update list, by RowId for this container
            for (AssaySpecimenConfigImpl assaySpecimen : TreatmentManager.getInstance().getFilteredAssaySpecimens(getContainer(), assaySpecimenRowIds))
                TreatmentManager.getInstance().deleteAssaySpecimen(getContainer(), getUser(), assaySpecimen.getRowId());
        }

        private void updateAssayVisitMap(int assaySpecimenId, List<AssaySpecimenVisitImpl> assayVisitMaps) throws Exception
        {
            List<Integer> assaySpecimenVisitIds = new ArrayList<>();
            if (assayVisitMaps != null && !assayVisitMaps.isEmpty())
            {
                for (AssaySpecimenVisitImpl assaySpecimenVisit : assayVisitMaps)
                {
                    assaySpecimenVisit.setAssaySpecimenId(assaySpecimenId);

                    Integer updatedRowId = TreatmentManager.getInstance().saveAssaySpecimenVisit(getContainer(), getUser(), assaySpecimenVisit);
                    assaySpecimenVisitIds.add(updatedRowId);
                }
            }

            // delete any other assaySpecimenVisits, not included in the insert/update list, by RowId for this container and assaySpecimenId
            for (AssaySpecimenVisitImpl assaySpecimenVisit : TreatmentManager.getInstance().getFilteredAssaySpecimenVisits(getContainer(), assaySpecimenId, assaySpecimenVisitIds))
                TreatmentManager.getInstance().deleteAssaySpecimenVisit(getContainer(), getUser(), assaySpecimenVisit.getRowId());
        }
    }

    private void updateAssayPlan(User user, Study study, String assayPlan)
    {
        if (study != null)
        {
            StudyService.get().updateAssayPlan(user, study, assayPlan);
        }
    }

    /**
     * Verifies the cross-container guard on the DoseAndRoute save path reached by UpdateStudyProductsAction. That
     * action's DoseAndRoute branch alone writes through the raw storage table keyed by a client-supplied RowId, so the
     * guard lives in TreatmentManager.saveStudyProductDoseAndRoute and is exercised directly here (building a full study
     * + product + JSON product payload to drive the action end-to-end would add a large fixture for the same assertion).
     */
    public static class ContainerScopingTestCase extends AbstractContainerScopingTest
    {
        @Test
        public void testSaveDoseAndRouteContainerScoping()
        {
            User admin = getAdmin();
            Container folderA = createContainer("A");
            Container folderB = createContainer("B");

            // A DoseAndRoute row that lives in folder B
            DoseAndRoute existing = Table.insert(admin, StudyDesignSchema.getInstance().getTableInfoDoseAndRoute(),
                    new DoseAndRoute("1 mg", "IV", 1, folderB));
            int rowId = existing.getRowId();

            // Deny: an update keyed by B's RowId while operating in folder A must be rejected, not silently
            // overwrite/re-home the foreign row. This is the path an Editor in folder A reaches via
            // UpdateStudyProductsAction by submitting a products[].DoseAndRoute[].RowId owned by folder B.
            DoseAndRoute attack = new DoseAndRoute("HACKED", "HACKED", 1, folderA);
            attack.setRowId(rowId);
            try
            {
                TreatmentManager.getInstance().saveStudyProductDoseAndRoute(folderA, admin, attack);
                fail("Expected NotFoundException updating a DoseAndRoute owned by another container");
            }
            catch (NotFoundException ignored) {}

            // The row in folder B must be untouched
            DoseAndRoute reloaded = new TableSelector(StudyDesignSchema.getInstance().getTableInfoDoseAndRoute())
                    .getObject(rowId, DoseAndRoute.class);
            assertNotNull("DoseAndRoute must still exist", reloaded);
            assertEquals("Dose must be unchanged after a cross-container update", "1 mg", reloaded.getDose());

            // Positive control: updating through the row's own container succeeds and persists the change
            DoseAndRoute ok = new DoseAndRoute("2 mg", "IM", 1, folderB);
            ok.setRowId(rowId);
            TreatmentManager.getInstance().saveStudyProductDoseAndRoute(folderB, admin, ok);
            DoseAndRoute afterOk = new TableSelector(StudyDesignSchema.getInstance().getTableInfoDoseAndRoute())
                    .getObject(rowId, DoseAndRoute.class);
            assertEquals("Dose should be updated by a same-container request", "2 mg", afterOk.getDose());
        }
    }
}