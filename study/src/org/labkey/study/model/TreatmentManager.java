/*
 * Copyright (c) 2014-2017 LabKey Corporation
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
package org.labkey.study.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.study.TimepointType;
import org.labkey.api.study.Visit;
import org.labkey.api.test.TestWhen;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.GUID;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.TestContext;
import org.labkey.study.StudySchema;
import org.labkey.study.query.StudyQuerySchema;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by cnathe on 1/24/14.
 */
public class TreatmentManager
{
    private static TreatmentManager _instance = new TreatmentManager();

    private TreatmentManager()
    {
    }

    public static TreatmentManager getInstance()
    {
        return _instance;
    }

    public List<ProductImpl> getStudyProducts(Container container, User user)
    {
        return getStudyProducts(container, user, null, null);
    }

    public List<ProductImpl> getStudyProducts(Container container, User user, @Nullable String role, @Nullable Integer rowId)
    {
        //Using a user schema so containerFilter will be created for us later (so don't need SimpleFilter.createContainerFilter)
        SimpleFilter filter = new SimpleFilter();
        if (role != null)
            filter.addCondition(FieldKey.fromParts("Role"), role);
        if (rowId != null)
            filter.addCondition(FieldKey.fromParts("RowId"), rowId);

        TableInfo ti = QueryService.get().getUserSchema(user, container, StudyQuerySchema.SCHEMA_NAME).getTable(StudyQuerySchema.PRODUCT_TABLE_NAME);
        return new TableSelector(ti, filter, new Sort("RowId")).getArrayList(ProductImpl.class);
    }

    public List<ProductImpl> getFilteredStudyProducts(Container container, User user, List<Integer> filterRowIds)
    {
        TableInfo ti = QueryService.get().getUserSchema(user, container, StudyQuerySchema.SCHEMA_NAME).getTable(StudyQuerySchema.PRODUCT_TABLE_NAME);

        //Using a user schema so containerFilter will be created for us later (so don't need SimpleFilter.createContainerFilter)
        SimpleFilter filter = new SimpleFilter();
        if (filterRowIds != null && !filterRowIds.isEmpty())
            filter.addCondition(FieldKey.fromParts("RowId"), filterRowIds, CompareType.NOT_IN);

        return new TableSelector(ti, filter, new Sort("RowId")).getArrayList(ProductImpl.class);
    }

    public List<ProductAntigenImpl> getStudyProductAntigens(Container container, User user, int productId)
    {
        SimpleFilter filter = new SimpleFilter();
        filter.addCondition(FieldKey.fromParts("ProductId"), productId);

        TableInfo ti = QueryService.get().getUserSchema(user, container, StudyQuerySchema.SCHEMA_NAME).getTable(StudyQuerySchema.PRODUCT_ANTIGEN_TABLE_NAME);
        return new TableSelector(ti, filter, new Sort("RowId")).getArrayList(ProductAntigenImpl.class);
    }

    public List<ProductAntigenImpl> getFilteredStudyProductAntigens(Container container, User user, @NotNull Integer productId, List<Integer> filterRowIds)
    {
        TableInfo ti = QueryService.get().getUserSchema(user, container, StudyQuerySchema.SCHEMA_NAME).getTable(StudyQuerySchema.PRODUCT_ANTIGEN_TABLE_NAME);

        //Using a user schema so containerFilter will be created for us later (so don't need SimpleFilter.createContainerFilter)
        SimpleFilter filter = new SimpleFilter();
        filter.addCondition(FieldKey.fromParts("ProductId"), productId);
        if (filterRowIds != null && !filterRowIds.isEmpty())
            filter.addCondition(FieldKey.fromParts("RowId"), filterRowIds, CompareType.NOT_IN);

        return new TableSelector(ti, filter, new Sort("RowId")).getArrayList(ProductAntigenImpl.class);
    }

    public Integer saveTreatment(Container container, User user, TreatmentImpl treatment) throws Exception
    {
        TableInfo treatmentTable = QueryService.get().getUserSchema(user, container, StudyQuerySchema.SCHEMA_NAME).getTable(StudyQuerySchema.TREATMENT_TABLE_NAME);
        return saveStudyDesignRow(container, user, treatmentTable, treatment.serialize(), treatment.isNew() ? null : treatment.getRowId(), "RowId");
    }

    public List<TreatmentImpl> getStudyTreatments(Container container, User user)
    {
        SimpleFilter filter = new SimpleFilter();
        TableInfo ti = QueryService.get().getUserSchema(user, container, StudyQuerySchema.SCHEMA_NAME).getTable(StudyQuerySchema.TREATMENT_TABLE_NAME);
        return new TableSelector(ti, filter, new Sort("RowId")).getArrayList(TreatmentImpl.class);
    }

    public TreatmentImpl getStudyTreatmentByRowId(Container container, User user, int rowId)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("RowId"), rowId);
        TableInfo ti = QueryService.get().getUserSchema(user, container, StudyQuerySchema.SCHEMA_NAME).getTable(StudyQuerySchema.TREATMENT_TABLE_NAME);
        TreatmentImpl treatment = new TableSelector(ti, filter, null).getObject(TreatmentImpl.class);

        // attach the associated study products to the treatment object
        if (treatment != null)
        {
            List<TreatmentProductImpl> treatmentProducts = getStudyTreatmentProducts(container, user, treatment.getRowId(), treatment.getProductSort());
            for (TreatmentProductImpl treatmentProduct : treatmentProducts)
            {
                List<ProductImpl> products = getStudyProducts(container, user, null, treatmentProduct.getProductId());
                for (ProductImpl product : products)
                {
                    product.setDose(treatmentProduct.getDose());
                    product.setRoute(treatmentProduct.getRoute());
                    treatment.addProduct(product);
                }
            }
        }

        return treatment;
    }

    public List<TreatmentImpl> getFilteredTreatments(Container container, User user, List<Integer> definedTreatmentIds, Set<Integer> usedTreatmentIds)
    {
        List<Integer> filterRowIds = new ArrayList<>();
        filterRowIds.addAll(definedTreatmentIds);
        filterRowIds.addAll(usedTreatmentIds);
        TableInfo ti = QueryService.get().getUserSchema(user, container, StudyQuerySchema.SCHEMA_NAME).getTable(StudyQuerySchema.TREATMENT_TABLE_NAME);

        //Using a user schema so containerFilter will be created for us later (so don't need SimpleFilter.createContainerFilter)
        SimpleFilter filter = new SimpleFilter();
        if (filterRowIds != null && !filterRowIds.isEmpty())
            filter.addCondition(FieldKey.fromParts("RowId"), filterRowIds, CompareType.NOT_IN);

        return new TableSelector(ti, filter, new Sort("RowId")).getArrayList(TreatmentImpl.class);
    }

    public Integer saveTreatmentProductMapping(Container container, User user, TreatmentProductImpl treatmentProduct) throws Exception
    {
        TableInfo treatmentProductTable = QueryService.get().getUserSchema(user, container, StudyQuerySchema.SCHEMA_NAME).getTable(StudyQuerySchema.TREATMENT_PRODUCT_MAP_TABLE_NAME);
        return saveStudyDesignRow(container, user, treatmentProductTable, treatmentProduct.serialize(), treatmentProduct.isNew() ? null : treatmentProduct.getRowId(), "RowId");
    }

    public List<TreatmentProductImpl> getStudyTreatmentProducts(Container container, User user, int treatmentId)
    {
        return getStudyTreatmentProducts(container, user, treatmentId, new Sort("RowId"));
    }

    public List<TreatmentProductImpl> getStudyTreatmentProducts(Container container, User user, int treatmentId, Sort sort)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("TreatmentId"), treatmentId);

        TableInfo ti = QueryService.get().getUserSchema(user, container, StudyQuerySchema.SCHEMA_NAME).getTable(StudyQuerySchema.TREATMENT_PRODUCT_MAP_TABLE_NAME);
        return new TableSelector(ti, filter, sort).getArrayList(TreatmentProductImpl.class);
    }

    public List<TreatmentProductImpl> getFilteredTreatmentProductMappings(Container container, User user, @NotNull Integer treatmentId, List<Integer> filterRowIds)
    {
        TableInfo ti = QueryService.get().getUserSchema(user, container, StudyQuerySchema.SCHEMA_NAME).getTable(StudyQuerySchema.TREATMENT_PRODUCT_MAP_TABLE_NAME);

        //Using a user schema so containerFilter will be created for us later (so don't need SimpleFilter.createContainerFilter)
        SimpleFilter filter = new SimpleFilter();
        filter.addCondition(FieldKey.fromParts("TreatmentId"), treatmentId);
        if (filterRowIds != null && !filterRowIds.isEmpty())
            filter.addCondition(FieldKey.fromParts("RowId"), filterRowIds, CompareType.NOT_IN);

        return new TableSelector(ti, filter, new Sort("RowId")).getArrayList(TreatmentProductImpl.class);
    }

    public List<TreatmentVisitMapImpl> getStudyTreatmentVisitMap(Container container, @Nullable Integer cohortId)
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(container);
        if (cohortId != null)
            filter.addCondition(FieldKey.fromParts("CohortId"), cohortId);

        TableInfo ti = StudySchema.getInstance().getTableInfoTreatmentVisitMap();
        return new TableSelector(ti, filter, new Sort("CohortId")).getArrayList(TreatmentVisitMapImpl.class);
    }

    public List<VisitImpl> getVisitsForTreatmentSchedule(Container container)
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(container);
        List<Integer> visitRowIds = new TableSelector(StudySchema.getInstance().getTableInfoTreatmentVisitMap(),
                Collections.singleton("VisitId"), filter, new Sort("VisitId")).getArrayList(Integer.class);

        return StudyManager.getInstance().getSortedVisitsByRowIds(container, visitRowIds);
    }

    public TreatmentVisitMapImpl insertTreatmentVisitMap(User user, Container container, int cohortId, int visitId, int treatmentId)
    {
        TreatmentVisitMapImpl newMapping = new TreatmentVisitMapImpl();
        newMapping.setContainer(container);
        newMapping.setCohortId(cohortId);
        newMapping.setVisitId(visitId);
        newMapping.setTreatmentId(treatmentId);

        return Table.insert(user, StudySchema.getInstance().getTableInfoTreatmentVisitMap(), newMapping);
    }

    public void deleteTreatmentVisitMapForCohort(Container container, int rowId)
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(container);
        filter.addCondition(FieldKey.fromParts("CohortId"), rowId);
        Table.delete(StudySchema.getInstance().getTableInfoTreatmentVisitMap(), filter);
    }

    public void deleteTreatmentVisitMapForVisit(Container container, int rowId)
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(container);
        filter.addCondition(FieldKey.fromParts("VisitId"), rowId);
        Table.delete(StudySchema.getInstance().getTableInfoTreatmentVisitMap(), filter);
    }

    public void deleteTreatment(Container container, User user, int rowId)
    {
        StudySchema schema = StudySchema.getInstance();

        try (DbScope.Transaction transaction = schema.getSchema().getScope().ensureTransaction())
        {
            // delete the usages of this treatment in the TreatmentVisitMap
            SimpleFilter filter = SimpleFilter.createContainerFilter(container);
            filter.addCondition(FieldKey.fromParts("TreatmentId"), rowId);
            Table.delete(schema.getTableInfoTreatmentVisitMap(), filter);

            // delete the associated treatment study product mappings (provision table)
            filter = SimpleFilter.createContainerFilter(container);
            filter.addCondition(FieldKey.fromParts("TreatmentId"), rowId);
            deleteTreatmentProductMap(container, user, filter);

            // finally delete the record from the Treatment  (provision table)
            TableInfo treatmentTable = QueryService.get().getUserSchema(user, container, StudyQuerySchema.SCHEMA_NAME).getTable(StudyQuerySchema.TREATMENT_TABLE_NAME);
            if (treatmentTable != null)
            {
                QueryUpdateService qus = treatmentTable.getUpdateService();
                List<Map<String, Object>> keys = new ArrayList<>();
                ColumnInfo treatmentPk = treatmentTable.getColumn(FieldKey.fromParts("RowId"));
                keys.add(Collections.singletonMap(treatmentPk.getName(), rowId));
                qus.deleteRows(user, container, keys, null, null);
            }

            transaction.commit();
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    public void deleteStudyProduct(Container container, User user, int rowId)
    {
        StudySchema schema = StudySchema.getInstance();

        try (DbScope.Transaction transaction = schema.getSchema().getScope().ensureTransaction())
        {
            // delete the usages of this study product in the ProductAntigen table (provision table)
            deleteProductAntigens(container, user, rowId);

            // delete the associated doses and routes for this product
            Table.delete(StudySchema.getInstance().getTableInfoDoseAndRoute(), new SimpleFilter(FieldKey.fromParts("ProductId"), rowId));

            // delete the associated treatment study product mappings (provision table)
            SimpleFilter filter = SimpleFilter.createContainerFilter(container);
            filter.addCondition(FieldKey.fromParts("ProductId"), rowId);
            deleteTreatmentProductMap(container, user, filter);

            // finally delete the record from the Products  (provision table)
            TableInfo productTable = QueryService.get().getUserSchema(user, container, StudyQuerySchema.SCHEMA_NAME).getTable(StudyQuerySchema.PRODUCT_TABLE_NAME);
            if (productTable != null)
            {
                QueryUpdateService qus = productTable.getUpdateService();
                List<Map<String, Object>> keys = new ArrayList<>();
                ColumnInfo productPk = productTable.getColumn(FieldKey.fromParts("RowId"));
                keys.add(Collections.singletonMap(productPk.getName(), rowId));
                qus.deleteRows(user, container, keys, null, null);
            }
            else
                throw new IllegalStateException("Could not find table: " + StudyQuerySchema.PRODUCT_TABLE_NAME);

            transaction.commit();
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    public Integer saveStudyProduct(Container container, User user, ProductImpl product) throws Exception
    {
        TableInfo productTable = QueryService.get().getUserSchema(user, container, StudyQuerySchema.SCHEMA_NAME).getTable(StudyQuerySchema.PRODUCT_TABLE_NAME);
        return saveStudyDesignRow(container, user, productTable, product.serialize(), product.isNew() ? null : product.getRowId(), "RowId");
    }

    public Integer saveStudyProductAntigen(Container container, User user, ProductAntigenImpl antigen) throws Exception
    {
        TableInfo productAntigenTable = QueryService.get().getUserSchema(user, container, StudyQuerySchema.SCHEMA_NAME).getTable(StudyQuerySchema.PRODUCT_ANTIGEN_TABLE_NAME);
        return saveStudyDesignRow(container, user, productAntigenTable, antigen.serialize(), antigen.isNew() ? null : antigen.getRowId(), "RowId");
    }

    public DoseAndRoute saveStudyProductDoseAndRoute(Container container, User user, DoseAndRoute doseAndRoute)
    {
        if (doseAndRoute.isNew())
            return Table.insert(user, StudySchema.getInstance().getTableInfoDoseAndRoute(), doseAndRoute);
        else
            return Table.update(user, StudySchema.getInstance().getTableInfoDoseAndRoute(), doseAndRoute, doseAndRoute.getRowId());
    }

    public Collection<DoseAndRoute> getStudyProductsDoseAndRoute(Container container, User user, int productId)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("ProductId"), productId);
        return new TableSelector(StudySchema.getInstance().getTableInfoDoseAndRoute(), filter, null).getCollection(DoseAndRoute.class);
    }

    @Nullable
    public DoseAndRoute getDoseAndRoute(Container container, String dose, String route, int productId)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("ProductId"), productId);
        if (dose != null)
            filter.addCondition(FieldKey.fromParts("Dose"), dose);
        else
            filter.addCondition(FieldKey.fromParts("Dose"), null, CompareType.ISBLANK);
        if (route != null)
            filter.addCondition(FieldKey.fromParts("Route"), route);
        else
            filter.addCondition(FieldKey.fromParts("Route"), null, CompareType.ISBLANK);
        Collection<DoseAndRoute> doseAndRoutes = new TableSelector(StudySchema.getInstance().getTableInfoDoseAndRoute(), filter, null).getCollection(DoseAndRoute.class);

        if (!doseAndRoutes.isEmpty())
        {
            return doseAndRoutes.iterator().next();
        }
        return null;
    }

    public Integer saveAssaySpecimen(Container container, User user, AssaySpecimenConfigImpl assaySpecimen) throws Exception
    {
        TableInfo assaySpecimenTable = QueryService.get().getUserSchema(user, container, StudyQuerySchema.SCHEMA_NAME).getTable(StudyQuerySchema.ASSAY_SPECIMEN_TABLE_NAME);
        return saveStudyDesignRow(container, user, assaySpecimenTable, assaySpecimen.serialize(), assaySpecimen.isNew() ? null : assaySpecimen.getRowId(), "RowId", true);
    }

    public Integer saveAssaySpecimenVisit(Container container, User user, AssaySpecimenVisitImpl assaySpecimenVisit) throws Exception
    {
        TableInfo assaySpecimenVIsitTable = QueryService.get().getUserSchema(user, container, StudyQuerySchema.SCHEMA_NAME).getTable(StudyQuerySchema.ASSAY_SPECIMEN_VISIT_TABLE_NAME);
        return saveStudyDesignRow(container, user, assaySpecimenVIsitTable, assaySpecimenVisit.serialize(), assaySpecimenVisit.isNew() ? null : assaySpecimenVisit.getRowId(), "RowId", true);
    }

    public List<AssaySpecimenConfigImpl> getFilteredAssaySpecimens(Container container, List<Integer> filterRowIds)
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(container);
        if (filterRowIds != null && !filterRowIds.isEmpty())
            filter.addCondition(FieldKey.fromParts("RowId"), filterRowIds, CompareType.NOT_IN);

        return new TableSelector(StudySchema.getInstance().getTableInfoAssaySpecimen(), filter, new Sort("RowId")).getArrayList(AssaySpecimenConfigImpl.class);
    }

    public List<AssaySpecimenVisitImpl> getFilteredAssaySpecimenVisits(Container container, int assaySpecimenId, List<Integer> filterRowIds)
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(container);
        filter.addCondition(FieldKey.fromParts("AssaySpecimenId"), assaySpecimenId);
        if (filterRowIds != null && !filterRowIds.isEmpty())
            filter.addCondition(FieldKey.fromParts("RowId"), filterRowIds, CompareType.NOT_IN);

        return new TableSelector(StudySchema.getInstance().getTableInfoAssaySpecimenVisit(), filter, new Sort("RowId")).getArrayList(AssaySpecimenVisitImpl.class);
    }

    public Integer saveStudyDesignRow(Container container, User user, TableInfo tableInfo, Map<String, Object> row, Integer key, String pkColName) throws Exception
    {
        return saveStudyDesignRow(container, user, tableInfo, row, key, pkColName, false);
    }

    public Integer saveStudyDesignRow(Container container, User user, TableInfo tableInfo, Map<String, Object> row, Integer key, String pkColName, boolean includeContainerKey) throws Exception
    {
        QueryUpdateService qus = tableInfo != null ? tableInfo.getUpdateService() : null;
        if (qus != null)
        {
            BatchValidationException errors = new BatchValidationException();
            List<Map<String, Object>> updatedRows;

            if (key == null)
            {
                updatedRows = qus.insertRows(user, container, Collections.singletonList(row), errors, null, null);
            }
            else
            {
                Map<String, Object> oldKey = new HashMap<>();
                oldKey.put(pkColName, key);
                if (includeContainerKey)
                    oldKey.put("Container", container.getId());

                updatedRows = qus.updateRows(user, container, Collections.singletonList(row), Collections.singletonList(oldKey), null, null);
            }

            if (errors.hasErrors())
                throw errors.getLastRowError();

            if (updatedRows.size() == 1)
                return (Integer) updatedRows.get(0).get(pkColName);
        }

        return null;
    }

    public void deleteStudyProductAntigen(Container container, User user, int rowId) throws Exception
    {
        TableInfo productAntigenTable = QueryService.get().getUserSchema(user, container, StudyQuerySchema.SCHEMA_NAME).getTable(StudyQuerySchema.PRODUCT_ANTIGEN_TABLE_NAME);
        if (productAntigenTable != null)
        {
            QueryUpdateService qus = productAntigenTable.getUpdateService();
            if (qus != null)
            {
                List<Map<String, Object>> keys = Collections.singletonList(Collections.singletonMap("RowId", rowId));
                qus.deleteRows(user, container, keys, null, null);
            }
            else
                throw new IllegalStateException("Could not find query update service for table: " + StudyQuerySchema.PRODUCT_ANTIGEN_TABLE_NAME);
        }
        else
            throw new IllegalStateException("Could not find table: " + StudyQuerySchema.PRODUCT_ANTIGEN_TABLE_NAME);
    }

    public void deleteProductAntigens(Container container, User user, int productId) throws Exception
    {
        TableInfo productAntigenTable = QueryService.get().getUserSchema(user, container, StudyQuerySchema.SCHEMA_NAME).getTable(StudyQuerySchema.PRODUCT_ANTIGEN_TABLE_NAME);
        if (productAntigenTable != null)
        {
            SimpleFilter filter = SimpleFilter.createContainerFilter(container);
            filter.addCondition(FieldKey.fromParts("ProductId"), productId);
            TableSelector selector = new TableSelector(productAntigenTable, Collections.singleton("RowId"), filter, null);
            Integer[] productAntigenIds = selector.getArray(Integer.class);

            QueryUpdateService qus = productAntigenTable.getUpdateService();
            if (qus != null)
            {
                List<Map<String, Object>> keys = new ArrayList<>();
                ColumnInfo productAntigenPk = productAntigenTable.getColumn(FieldKey.fromParts("RowId"));
                for (Integer productAntigenId : productAntigenIds)
                {
                    keys.add(Collections.singletonMap(productAntigenPk.getName(), productAntigenId));
                }

                qus.deleteRows(user, container, keys, null, null);
            }
            else
                throw new IllegalStateException("Could not find query update service for table: " + StudyQuerySchema.PRODUCT_ANTIGEN_TABLE_NAME);
        }
        else
            throw new IllegalStateException("Could not find table: " + StudyQuerySchema.PRODUCT_ANTIGEN_TABLE_NAME);
    }

    public void deleteTreatmentProductMap(Container container, User user, SimpleFilter filter) throws Exception
    {
        TableInfo productMapTable = QueryService.get().getUserSchema(user, container, StudyQuerySchema.SCHEMA_NAME).getTable(StudyQuerySchema.TREATMENT_PRODUCT_MAP_TABLE_NAME);
        if (productMapTable != null)
        {
            TableSelector selector = new TableSelector(productMapTable, Collections.singleton("RowId"), filter, null);
            deleteTreatmentProductMap(container, user, selector.getArrayList(Integer.class));
        }
        else
            throw new IllegalStateException("Could not find table: " + StudyQuerySchema.TREATMENT_PRODUCT_MAP_TABLE_NAME);
    }

    public void deleteTreatmentProductMap(Container container, User user, List<Integer> rowIds) throws Exception
    {
        TableInfo productMapTable = QueryService.get().getUserSchema(user, container, StudyQuerySchema.SCHEMA_NAME).getTable(StudyQuerySchema.TREATMENT_PRODUCT_MAP_TABLE_NAME);
        if (productMapTable != null)
        {
            QueryUpdateService qus = productMapTable.getUpdateService();
            if (qus != null)
            {
                List<Map<String, Object>> keys = new ArrayList<>();
                ColumnInfo productMapPk = productMapTable.getColumn(FieldKey.fromParts("RowId"));
                for (Integer rowId : rowIds)
                    keys.add(Collections.singletonMap(productMapPk.getName(), rowId));

                qus.deleteRows(user, container, keys, null, null);
            }
            else
                throw new IllegalStateException("Could not find query update service for table: " + StudyQuerySchema.TREATMENT_PRODUCT_MAP_TABLE_NAME);
        }
        else
            throw new IllegalStateException("Could not find table: " + StudyQuerySchema.TREATMENT_PRODUCT_MAP_TABLE_NAME);
    }

    public void deleteAssaySpecimen(Container container, User user, int rowId) throws Exception
    {
        // first delete any usages of the AssaySpecimenId in the AssaySpecimenVisit table
        SimpleFilter filter = SimpleFilter.createContainerFilter(container);
        filter.addCondition(FieldKey.fromParts("AssaySpecimenId"), rowId);
        Table.delete(StudySchema.getInstance().getTableInfoAssaySpecimenVisit(), filter);

        // the delete the AssaySpecimen record by RowId
        filter = SimpleFilter.createContainerFilter(container);
        filter.addCondition(FieldKey.fromParts("RowId"), rowId);
        Table.delete(StudySchema.getInstance().getTableInfoAssaySpecimen(), filter);
    }

    public void deleteAssaySpecimenVisit(Container container, User user, int rowId) throws Exception
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(container);
        filter.addCondition(FieldKey.fromParts("RowId"), rowId);
        Table.delete(StudySchema.getInstance().getTableInfoAssaySpecimenVisit(), filter);
    }

    public String getStudyDesignRouteLabelByName(Container container, String name)
    {
        return StudyManager.getInstance().getStudyDesignLabelByName(container, StudySchema.getInstance().getTableInfoStudyDesignRoutes(), name);
    }

    public String getStudyDesignImmunogenTypeLabelByName(Container container, String name)
    {
        return StudyManager.getInstance().getStudyDesignLabelByName(container, StudySchema.getInstance().getTableInfoStudyDesignImmunogenTypes(), name);
    }

    public String getStudyDesignGeneLabelByName(Container container, String name)
    {
        return StudyManager.getInstance().getStudyDesignLabelByName(container, StudySchema.getInstance().getTableInfoStudyDesignGenes(), name);
    }

    public String getStudyDesignSubTypeLabelByName(Container container, String name)
    {
        return StudyManager.getInstance().getStudyDesignLabelByName(container, StudySchema.getInstance().getTableInfoStudyDesignSubTypes(), name);
    }

    public void updateTreatmentProducts(int treatmentId, List<TreatmentProductImpl> treatmentProducts, Container container, User user) throws Exception
    {
        // insert new study treatment product mappings and update any existing ones
        List<Integer> treatmentProductRowIds = new ArrayList<>();
        for (TreatmentProductImpl treatmentProduct : treatmentProducts)
        {
            // make sure the treatmentId is set based on the treatment rowId
            treatmentProduct.setTreatmentId(treatmentId);

            Integer updatedRowId = TreatmentManager.getInstance().saveTreatmentProductMapping(container, user, treatmentProduct);
            if (updatedRowId != null)
                treatmentProductRowIds.add(updatedRowId);
        }

        // delete any other treatment product mappings, not included in the insert/update list, for the given treatmentId
        for (TreatmentProductImpl treatmentProduct : TreatmentManager.getInstance().getFilteredTreatmentProductMappings(container, user, treatmentId, treatmentProductRowIds))
            TreatmentManager.getInstance().deleteTreatmentProductMap(container, user, Collections.singletonList(treatmentProduct.getRowId()));
    }

    /****
     *
     *
     *
     * TESTING
     *
     *
     */

    @TestWhen(TestWhen.When.BVT)
    public static class TreatmentDataTestCase extends Assert
    {
        TestContext _context = null;
        User _user = null;
        Container _container = null;
        StudyImpl _junitStudy = null;
        TreatmentManager _manager = TreatmentManager.getInstance();
        UserSchema _schema = null;

        Map<String, String> _lookups = new HashMap<>();
        List<ProductImpl> _products = new ArrayList<>();
        List<TreatmentImpl> _treatments = new ArrayList<>();
        List<CohortImpl> _cohorts = new ArrayList<>();
        List<VisitImpl> _visits = new ArrayList<>();

        @Test
        public void test() throws Throwable
        {
            try
            {
                createStudy();
                _user = _context.getUser();
                _container = _junitStudy.getContainer();
                _schema = QueryService.get().getUserSchema(_user, _container, StudyQuerySchema.SCHEMA_NAME);

                populateLookupTables();
                populateStudyProducts();
                populateTreatments();
                populateTreatmentSchedule();

                verifyStudyProducts();
                verifyTreatments();
                verifyTreatmentSchedule();
                verifyCleanUpTreatmentData();
            }
            finally
            {
                tearDown();
            }
        }

        private void verifyCleanUpTreatmentData() throws Exception
        {
            // remove cohort and verify delete of TreatmentVisitMap
            StudyManager.getInstance().deleteCohort(_cohorts.get(0));
            verifyTreatmentVisitMapRecords(4);

            // remove visit and verify delete of TreatmentVisitMap
            StudyManager.getInstance().deleteVisit(_junitStudy, _visits.get(0), _user);
            verifyTreatmentVisitMapRecords(2);

            // we should still have all of our treatments and study products
            verifyTreatments();
            verifyStudyProducts();

            // remove treatment visit map records via visit
            _manager.deleteTreatmentVisitMapForVisit(_container, _visits.get(1).getRowId());
            verifyTreatmentVisitMapRecords(0);

            // remove treatment and verify delete of TreatmentProductMap
            _manager.deleteTreatment(_container, _user, _treatments.get(0).getRowId());
            verifyTreatmentProductMapRecords(_treatments.get(0).getRowId(), 0);
            verifyTreatmentProductMapRecords(_treatments.get(1).getRowId(), 4);

            // remove product and verify delete of TreatmentProductMap and ProductAntigen
            _manager.deleteStudyProduct(_container, _user, _products.get(0).getRowId());
            verifyTreatmentProductMapRecords(_treatments.get(1).getRowId(), 3);
            verifyStudyProductAntigens(_products.get(0).getRowId(), 0);
            verifyStudyProductAntigens(_products.get(1).getRowId(), 1);

            // directly delete product antigen
            _manager.deleteProductAntigens(_container, _user, _products.get(1).getRowId());
            verifyStudyProductAntigens(_products.get(1).getRowId(), 0);

            // delete treatment product map by productId and then treatmentId
            SimpleFilter filter = SimpleFilter.createContainerFilter(_container);
            filter.addCondition(FieldKey.fromParts("ProductId"), _products.get(1).getRowId());
            _manager.deleteTreatmentProductMap(_container, _user, filter);
            verifyTreatmentProductMapRecords(_treatments.get(1).getRowId(), 2);
            filter = SimpleFilter.createContainerFilter(_container);
            filter.addCondition(FieldKey.fromParts("TreatmentId"), _treatments.get(1).getRowId());
            _manager.deleteTreatmentProductMap(_container, _user, filter);
            verifyTreatmentProductMapRecords(_treatments.get(1).getRowId(), 0);
        }

        private void verifyTreatmentSchedule()
        {
            verifyTreatmentVisitMapRecords(8);

            _visits.add(StudyManager.getInstance().createVisit(_junitStudy, _user, new VisitImpl(_container, 3.0, "Visit 3", Visit.Type.FINAL_VISIT)));
            assertEquals("Unexpected number of treatment schedule visits", 2, _manager.getVisitsForTreatmentSchedule(_container).size());
        }

        private void verifyTreatments()
        {
            List<TreatmentImpl> treatments = _manager.getStudyTreatments(_container, _user);
            assertEquals("Unexpected study treatment count", 2, treatments.size());

            for (TreatmentImpl treatment : treatments)
            {
                verifyTreatmentProductMapRecords(treatment.getRowId(), 4);

                treatment = _manager.getStudyTreatmentByRowId(_container, _user, treatment.getRowId());
                assertEquals("Unexpected number of treatment products", 4, treatment.getProducts().size());

                for (ProductImpl product : treatment.getProducts())
                {
                    assertEquals("Unexpected product dose value", "Test Dose", product.getDose());
                    assertEquals("Unexpected product route value", _lookups.get("Route"), product.getRoute());
                }
            }

        }

        private void verifyStudyProducts()
        {
            List<ProductImpl> products = _manager.getStudyProducts(_container, _user);
            assertEquals("Unexpected study product count", 4, products.size());

            for (ProductImpl product : products)
                verifyStudyProductAntigens(product.getRowId(), 1);

            assertEquals("Unexpected study product count by role", 2, _manager.getStudyProducts(_container, _user, "Immunogen", null).size());
            assertEquals("Unexpected study product count by role", 2, _manager.getStudyProducts(_container, _user, "Adjuvant", null).size());
            assertEquals("Unexpected study product count by role", 0, _manager.getStudyProducts(_container, _user, "UNK", null).size());

            for (ProductImpl immunogen : _manager.getStudyProducts(_container, _user, "Immunogen", null))
                assertEquals("Unexpected product lookup value", _lookups.get("ImmunogenType"), immunogen.getType());
        }

        private void populateTreatmentSchedule() throws ValidationException
        {
            _cohorts.add(CohortManager.getInstance().createCohort(_junitStudy, _user, "Cohort1", true, 10, null));
            _cohorts.add(CohortManager.getInstance().createCohort(_junitStudy, _user, "Cohort2", true, 20, null));
            assertEquals(_cohorts.size(), 2);

            _visits.add(StudyManager.getInstance().createVisit(_junitStudy, _user, new VisitImpl(_container, 1.0, "Visit 1", Visit.Type.BASELINE)));
            _visits.add(StudyManager.getInstance().createVisit(_junitStudy, _user, new VisitImpl(_container, 2.0, "Visit 2", Visit.Type.SCHEDULED_FOLLOWUP)));
            assertEquals(_visits.size(), 2);

            for (CohortImpl cohort : _cohorts)
            {
                for (VisitImpl visit : _visits)
                {
                    for (TreatmentImpl treatment : _treatments)
                    {
                        _manager.insertTreatmentVisitMap(_user, _container, cohort.getRowId(), visit.getRowId(), treatment.getRowId());
                    }
                }
            }

            verifyTreatmentVisitMapRecords(_cohorts.size() * _visits.size() * _treatments.size());
        }

        private void populateTreatments() throws SQLException
        {
            TableInfo treatmentTable = _schema.getTable(StudyQuerySchema.TREATMENT_TABLE_NAME);
            if (treatmentTable != null)
            {
                TableInfo ti = ((FilteredTable)treatmentTable).getRealTable();

                TreatmentImpl treatment1 = new TreatmentImpl(_container, "Treatment1", "Treatment1 description");
                treatment1 = Table.insert(_user, ti, treatment1);
                addProductsForTreatment(treatment1.getRowId());
                _treatments.add(treatment1);

                TreatmentImpl treatment2 = new TreatmentImpl(_container, "Treatment2", "Treatment2 description");
                treatment2 = Table.insert(_user, ti, treatment2);
                addProductsForTreatment(treatment2.getRowId());
                _treatments.add(treatment2);
            }

            assertEquals(_treatments.size(), 2);
        }

        private void addProductsForTreatment(int treatmentId) throws SQLException
        {
            TableInfo treatmentProductTable = _schema.getTable(StudyQuerySchema.TREATMENT_PRODUCT_MAP_TABLE_NAME);
            if (treatmentProductTable != null)
            {
                TableInfo ti = ((FilteredTable)treatmentProductTable).getRealTable();

                for (ProductImpl product : _products)
                {
                    TreatmentProductImpl tp = new TreatmentProductImpl(_container, treatmentId, product.getRowId());
                    tp.setDose("Test Dose");
                    tp.setRoute(_lookups.get("Route"));
                    Table.insert(_user, ti, tp);
                }
            }

            verifyTreatmentProductMapRecords(treatmentId, _products.size());
        }

        private void populateStudyProducts() throws SQLException
        {
            TableInfo productTable = _schema.getTable(StudyQuerySchema.PRODUCT_TABLE_NAME);
            if (productTable != null)
            {
                TableInfo ti = ((FilteredTable)productTable).getRealTable();

                ProductImpl product1 = new ProductImpl(_container, "Immunogen1", "Immunogen");
                product1.setType(_lookups.get("ImmunogenType"));
                _products.add(Table.insert(_user, ti, product1));

                ProductImpl product2 = new ProductImpl(_container, "Immunogen2", "Immunogen");
                product2.setType(_lookups.get("ImmunogenType"));
                _products.add(Table.insert(_user, ti, product2));

                ProductImpl product3 = new ProductImpl(_container, "Adjuvant1", "Adjuvant");
                _products.add(Table.insert(_user, ti, product3));

                ProductImpl product4 = new ProductImpl(_container, "Adjuvant2", "Adjuvant");
                _products.add(Table.insert(_user, ti, product4));
            }

            assertEquals(_products.size(), 4);

            for (ProductImpl product : _products)
                addAntigenToProduct(product.getRowId());
        }

        private void addAntigenToProduct(int productId) throws SQLException
        {
            TableInfo productAntigenTable = _schema.getTable(StudyQuerySchema.PRODUCT_ANTIGEN_TABLE_NAME);
            if (productAntigenTable != null)
            {
                TableInfo ti = ((FilteredTable)productAntigenTable).getRealTable();

                ProductAntigenImpl productAntigen = new ProductAntigenImpl(_container, productId, _lookups.get("Gene"), _lookups.get("SubType"));
                Table.insert(_user, ti, productAntigen);
            }

            verifyStudyProductAntigens(productId, 1);
        }

        private void populateLookupTables() throws SQLException
        {
            String name, label;

            Map<String, String> data = new HashMap<>();
            data.put("Container", _container.getId());

            data.put("Name", name = "Test Immunogen Type");
            data.put("Label", label = "Test Immunogen Type Label");
            Table.insert(_user, StudySchema.getInstance().getTableInfoStudyDesignImmunogenTypes(), data);
            assertEquals("Unexpected study design lookup label", label, _manager.getStudyDesignImmunogenTypeLabelByName(_container, name));
            assertNull("Unexpected study design lookup label", _manager.getStudyDesignImmunogenTypeLabelByName(_container, "UNK"));
            _lookups.put("ImmunogenType", name);

            data.put("Name", name = "Test Gene");
            data.put("Label", label = "Test Gene Label");
            Table.insert(_user, StudySchema.getInstance().getTableInfoStudyDesignGenes(), data);
            assertEquals("Unexpected study design lookup label", label, _manager.getStudyDesignGeneLabelByName(_container, name));
            assertNull("Unexpected study design lookup label", _manager.getStudyDesignGeneLabelByName(_container, "UNK"));
            _lookups.put("Gene", name);

            data.put("Name", name = "Test SubType");
            data.put("Label", label = "Test SubType Label");
            Table.insert(_user, StudySchema.getInstance().getTableInfoStudyDesignSubTypes(), data);
            assertEquals("Unexpected study design lookup label", label, _manager.getStudyDesignSubTypeLabelByName(_container, name));
            assertNull("Unexpected study design lookup label", _manager.getStudyDesignSubTypeLabelByName(_container, "UNK"));
            _lookups.put("SubType", name);

            data.put("Name", name = "Test Route");
            data.put("Label", label = "Test Route Label");
            Table.insert(_user, StudySchema.getInstance().getTableInfoStudyDesignRoutes(), data);
            assertEquals("Unexpected study design lookup label", label, _manager.getStudyDesignRouteLabelByName(_container, name));
            assertNull("Unexpected study design lookup label", _manager.getStudyDesignRouteLabelByName(_container, "UNK"));
            _lookups.put("Route", name);

            assertEquals(_lookups.keySet().size(), 4);
        }

        private void verifyTreatmentVisitMapRecords(int expectedCount)
        {
            List<TreatmentVisitMapImpl> rows = _manager.getStudyTreatmentVisitMap(_container, null);
            assertEquals("Unexpected number of study.TreatmentVisitMap rows", expectedCount, rows.size());
        }

        private void verifyTreatmentProductMapRecords(int treatmentId, int expectedCount)
        {
            List<TreatmentProductImpl> rows = _manager.getStudyTreatmentProducts(_container, _user, treatmentId);
            assertEquals("Unexpected number of study.TreatmentProductMap rows", expectedCount, rows.size());
        }

        private void verifyStudyProductAntigens(int productId, int expectedCount)
        {
            List<ProductAntigenImpl> rows = _manager.getStudyProductAntigens(_container, _user, productId);
            assertEquals("Unexpected number of study.ProductAntigen rows", expectedCount, rows.size());

            for (ProductAntigenImpl row : rows)
            {
                assertEquals("Unexpected antigen lookup value", _lookups.get("Gene"), row.getGene());
                assertEquals("Unexpected antigen lookup value", _lookups.get("SubType"), row.getSubType());
            }
        }

        private void createStudy() throws SQLException
        {
            _context = TestContext.get();
            Container junit = JunitUtil.getTestContainer();

            String name = GUID.makeHash();
            Container c = ContainerManager.createContainer(junit, name);
            StudyImpl s = new StudyImpl(c, "Junit Study");
            s.setTimepointType(TimepointType.VISIT);
            s.setStartDate(new Date(DateUtil.parseDateTime(c, "2014-01-01")));
            s.setSubjectColumnName("SubjectID");
            s.setSubjectNounPlural("Subjects");
            s.setSubjectNounSingular("Subject");
            s.setSecurityType(SecurityType.BASIC_WRITE);
            _junitStudy = StudyManager.getInstance().createStudy(_context.getUser(), s);
        }

        private void tearDown()
        {
            if (null != _junitStudy)
            {
                assertTrue(ContainerManager.delete(_junitStudy.getContainer(), _context.getUser()));
            }
        }
    }
}
