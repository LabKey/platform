package org.labkey.study.model;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.security.User;
import org.labkey.study.StudySchema;
import org.labkey.study.query.StudyQuerySchema;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

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
        SimpleFilter filter = SimpleFilter.createContainerFilter(container);
        if (role != null)
            filter.addCondition(FieldKey.fromParts("Role"), role);
        if (rowId != null)
            filter.addCondition(FieldKey.fromParts("RowId"), rowId);

        TableInfo ti = QueryService.get().getUserSchema(user, container, StudyQuerySchema.SCHEMA_NAME).getTable(StudyQuerySchema.PRODUCT_TABLE_NAME);
        return new TableSelector(ti, filter, new Sort("RowId")).getArrayList(ProductImpl.class);
    }

    public List<ProductAntigenImpl> getStudyProductAntigens(Container container, User user, int productId)
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(container);
        filter.addCondition(FieldKey.fromParts("ProductId"), productId);

        TableInfo ti = QueryService.get().getUserSchema(user, container, StudyQuerySchema.SCHEMA_NAME).getTable(StudyQuerySchema.PRODUCT_ANTIGEN_TABLE_NAME);
        return new TableSelector(ti, filter, new Sort("RowId")).getArrayList(ProductAntigenImpl.class);
    }

    public List<TreatmentImpl> getStudyTreatments(Container container, User user)
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(container);
        TableInfo ti = QueryService.get().getUserSchema(user, container, StudyQuerySchema.SCHEMA_NAME).getTable(StudyQuerySchema.TREATMENT_TABLE_NAME);
        return new TableSelector(ti, filter, new Sort("RowId")).getArrayList(TreatmentImpl.class);
    }

    public TreatmentImpl getStudyTreatmentByRowId(Container container, User user, int rowId)
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(container);
        filter.addCondition(FieldKey.fromParts("RowId"), rowId);
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

    public List<TreatmentProductImpl> getStudyTreatmentProducts(Container container, User user, int treatmentId)
    {
        return getStudyTreatmentProducts(container, user, treatmentId, new Sort("RowId"));
    }

    public List<TreatmentProductImpl> getStudyTreatmentProducts(Container container, User user, int treatmentId, Sort sort)
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(container);
        filter.addCondition(FieldKey.fromParts("TreatmentId"), treatmentId);

        TableInfo ti = QueryService.get().getUserSchema(user, container, StudyQuerySchema.SCHEMA_NAME).getTable(StudyQuerySchema.TREATMENT_PRODUCT_MAP_TABLE_NAME);
        return new TableSelector(ti, filter, sort).getArrayList(TreatmentProductImpl.class);
    }

    public List<TreatmentVisitMapImpl> getStudyTreatmentVisitMap(Container container, @Nullable Integer cohortId)
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(container);
        if (cohortId != null)
            filter.addCondition(FieldKey.fromParts("CohortId"), cohortId);

        TableInfo ti = StudySchema.getInstance().getTableInfoTreatmentVisitMap();
        return new TableSelector(ti, filter, new Sort("CohortId")).getArrayList(TreatmentVisitMapImpl.class);
    }

    public List<VisitImpl> getVisitsForImmunizationSchedule(Container container)
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(container);
        List<Integer> visitRowIds = new TableSelector(StudySchema.getInstance().getTableInfoTreatmentVisitMap(),
                Collections.singleton("VisitId"), filter, new Sort("VisitId")).getArrayList(Integer.class);

        return StudyManager.getInstance().getSortedVisitsByRowIds(container, visitRowIds);
    }

    public TreatmentVisitMapImpl insertTreatmentVisitMap(User user, Container container, int cohortId, int visitId, int treatmentId) throws SQLException
    {
        TreatmentVisitMapImpl newMapping = new TreatmentVisitMapImpl();
        newMapping.setContainer(container);
        newMapping.setCohortId(cohortId);
        newMapping.setVisitId(visitId);
        newMapping.setTreatmentId(treatmentId);

        return Table.insert(user, StudySchema.getInstance().getTableInfoTreatmentVisitMap(), newMapping);
    }

    public void deleteTreatmentVisitMapForCohort(Container container, int rowId) throws SQLException
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(container);
        filter.addCondition(FieldKey.fromParts("CohortId"), rowId);
        Table.delete(StudySchema.getInstance().getTableInfoTreatmentVisitMap(), filter);
    }

    public void deleteTreatmentVisitMapForVisit(Container container, int rowId) throws SQLException
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
            // delete the uages of this treatment in the TreatmentVisitMap
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
                keys.add(Collections.<String, Object>singletonMap(treatmentPk.getName(), rowId));
                qus.deleteRows(user, container, keys, null);
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
            // delete the uages of this study procut in the ProductAntigen table (provision table)
            deleteProductAntigens(container, user, rowId);

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
                keys.add(Collections.<String, Object>singletonMap(productPk.getName(), rowId));
                qus.deleteRows(user, container, keys, null);
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

    public void deleteProductAntigens(Container container, User user, int rowId) throws Exception
    {
        TableInfo productAntigenTable = QueryService.get().getUserSchema(user, container, StudyQuerySchema.SCHEMA_NAME).getTable(StudyQuerySchema.PRODUCT_ANTIGEN_TABLE_NAME);
        if (productAntigenTable != null)
        {
            SimpleFilter filter = SimpleFilter.createContainerFilter(container);
            filter.addCondition(FieldKey.fromParts("ProductId"), rowId);
            TableSelector selector = new TableSelector(productAntigenTable, Collections.singleton("RowId"), filter, null);
            Integer[] productAntigenIds = selector.getArray(Integer.class);

            QueryUpdateService qus = productAntigenTable.getUpdateService();
            if (qus != null)
            {
                List<Map<String, Object>> keys = new ArrayList<>();
                ColumnInfo productAntigenPk = productAntigenTable.getColumn(FieldKey.fromParts("RowId"));
                for (Integer productAntigenId : productAntigenIds)
                {
                    keys.add(Collections.<String, Object>singletonMap(productAntigenPk.getName(), productAntigenId));
                }

                qus.deleteRows(user, container, keys, null);
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
            Integer[] productMapIds = selector.getArray(Integer.class);

            QueryUpdateService qus = productMapTable.getUpdateService();
            if (qus != null)
            {
                List<Map<String, Object>> keys = new ArrayList<>();
                ColumnInfo productMapPk = productMapTable.getColumn(FieldKey.fromParts("RowId"));
                for (Integer productMapId : productMapIds)
                {
                    keys.add(Collections.<String, Object>singletonMap(productMapPk.getName(), productMapId));
                }

                qus.deleteRows(user, container, keys, null);
            }
            else
                throw new IllegalStateException("Could not find query update service for table: " + StudyQuerySchema.TREATMENT_PRODUCT_MAP_TABLE_NAME);
        }
        else
            throw new IllegalStateException("Could not find table: " + StudyQuerySchema.TREATMENT_PRODUCT_MAP_TABLE_NAME);
    }

    public String getStudyDesignRouteLabelByName(Container container, String name)
    {
        return getStudyDesignLabelByName(container, StudySchema.getInstance().getTableInfoStudyDesignRoutes(), name);
    }

    public String getStudyDesignImmunogenTypeLabelByName(Container container, String name)
    {
        return getStudyDesignLabelByName(container, StudySchema.getInstance().getTableInfoStudyDesignImmunogenTypes(), name);
    }

    private String getStudyDesignLabelByName(Container container, TableInfo tableInfo, String name)
    {
        // first look in the current container for the StudyDesign record, then look for it at the project level
        SimpleFilter filter = SimpleFilter.createContainerFilter(container);
        filter.addCondition(FieldKey.fromParts("Name"), name);
        String label = new TableSelector(tableInfo, Collections.singleton("Label"), filter, null).getObject(String.class);
        if (label == null && !container.isProject())
        {
            filter = SimpleFilter.createContainerFilter(container.getProject());
            filter.addCondition(FieldKey.fromParts("Name"), name);
            label = new TableSelector(tableInfo, Collections.singleton("Label"), filter, null).getObject(String.class);
        }

        return label;
    }
}
