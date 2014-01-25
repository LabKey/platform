package org.labkey.study.importer;

import org.labkey.api.admin.ImportException;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.TableInfo;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.Visit;
import org.labkey.api.writer.VirtualFile;
import org.labkey.study.model.CohortImpl;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.query.StudyQuerySchema;
import org.labkey.study.xml.ExportDirType;
import org.springframework.validation.BindException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by klum on 1/22/14.
 */
public class TreatmentDataImporter extends DefaultStudyDesignImporter implements InternalStudyImporter
{
    // shared transform data structures
    Map<Integer, Integer> _productIdMap = new HashMap<>();
    Map<Integer, Integer> _treatmentIdMap = new HashMap<>();

    Map<String, CohortImpl> _cohortMap = new HashMap<>();
    Map<Double, Visit> _visitMap = new HashMap<>();

    private ProductTableTransform _productTableTransform = new ProductTableTransform();
    private TreatmentTableTransform _treatmentTableTransform = new TreatmentTableTransform();
    private TreatmentProductTransform _treatmentProductTransform = new TreatmentProductTransform();
    private TreatmentVisitMapTransform _treatmentVisitMapTransform = new TreatmentVisitMapTransform();

    @Override
    public String getDescription()
    {
        return "treatment data";
    }

    @Override
    public void process(StudyImportContext ctx, VirtualFile root, BindException errors) throws Exception
    {
        StudyImpl study = StudyManager.getInstance().getStudy(ctx.getContainer());
        ExportDirType dirType = ctx.getXml().getTreatmentData();

        if (dirType != null)
        {
            VirtualFile vf = root.getDir(dirType.getDir());
            if (vf != null)
            {
                // import the product table
                StudyQuerySchema schema = new StudyQuerySchema(StudyManager.getInstance().getStudy(ctx.getContainer()), ctx.getUser(), true);

                // add the treatment specific tables
                LOG.info("Importing treatment data tables");
                TableInfo productTable = schema.getTable(StudyQuerySchema.PRODUCT_TABLE_NAME);
                importTableData(ctx, vf, productTable, _productTableTransform, null);

                TableInfo productAntigenTable = schema.getTable(StudyQuerySchema.PRODUCT_ANTIGEN_TABLE_NAME);
                importTableData(ctx, vf, productAntigenTable, null, _productTableTransform);

                TableInfo treatmentTable = schema.getTable(StudyQuerySchema.TREATMENT_TABLE_NAME);
                importTableData(ctx, vf, treatmentTable, _treatmentTableTransform, null);

                TableInfo treatmentProductTable = schema.getTable(StudyQuerySchema.TREATMENT_PRODUCT_MAP_TABLE_NAME);
                importTableData(ctx, vf, treatmentProductTable, null, _treatmentProductTransform);

                TableInfo treatmentVisitMapTable = schema.getTable(StudyQuerySchema.TREATMENT_VISIT_MAP_TABLE_NAME);
                importTableData(ctx, vf, treatmentVisitMapTable, null, _treatmentVisitMapTransform);

                // study design tables
                LOG.info("Importing study design data tables");
                importTableData(ctx, vf, schema.getTable(StudyQuerySchema.STUDY_DESIGN_GENES_TABLE_NAME), null, null);
                importTableData(ctx, vf, schema.getTable(StudyQuerySchema.STUDY_DESIGN_LABS_TABLE_NAME), null, null);
                importTableData(ctx, vf, schema.getTable(StudyQuerySchema.STUDY_DESIGN_ROUTES_TABLE_NAME), null, null);
                importTableData(ctx, vf, schema.getTable(StudyQuerySchema.STUDY_DESIGN_IMMUNOGEN_TYPES_TABLE_NAME), null, null);
                importTableData(ctx, vf, schema.getTable(StudyQuerySchema.STUDY_DESIGN_SAMPLE_TYPES_TABLE_NAME), null, null);
                importTableData(ctx, vf, schema.getTable(StudyQuerySchema.STUDY_DESIGN_SUB_TYPES_TABLE_NAME), null, null);
            }
            else
                throw new ImportException("Unable to open the folder at : " + dirType.getDir());
        }
    }

    /**
     * Transform which manages foreign keys to the rowId of the Product table
     */
    private class ProductTableTransform implements TransformBuilder, TransformHelper
    {
        @Override
        public List<Map<String, Object>> transform(StudyImportContext ctx, List<Map<String, Object>> origRows)
        {
            List<Map<String, Object>> newRows = new ArrayList<>();

            for (Map<String, Object> row : origRows)
            {
                Map<String, Object> newRow = new CaseInsensitiveHashMap<>();
                newRows.add(newRow);
                newRow.putAll(row);

                if (newRow.containsKey("ProductId") && _productIdMap.containsKey(newRow.get("ProductId")))
                {
                    newRow.put("ProductId", _productIdMap.get(newRow.get("ProductId")));
                }
            }
            return newRows;
        }

        @Override
        public void createTransformInfo(StudyImportContext ctx, List<Map<String, Object>> origRows, List<Map<String, Object>> insertedRows)
        {
            for (int i=0; i < origRows.size(); i++)
            {
                Map<String, Object> orig = origRows.get(i);
                Map<String, Object> inserted = insertedRows.get(i);

                if (orig.containsKey("RowId") && inserted.containsKey("RowId"))
                {
                    _productIdMap.put((Integer)orig.get("RowId"), (Integer)inserted.get("RowId"));
                }
            }
        }
    }

    /**
     * Transform which manages foreign keys to the Treatment table
     */
    private class TreatmentTableTransform implements TransformBuilder
    {
        @Override
        public void createTransformInfo(StudyImportContext ctx, List<Map<String, Object>> origRows, List<Map<String, Object>> insertedRows)
        {
            for (int i=0; i < origRows.size(); i++)
            {
                Map<String, Object> orig = origRows.get(i);
                Map<String, Object> inserted = insertedRows.get(i);

                if (orig.containsKey("RowId") && inserted.containsKey("RowId"))
                {
                    _treatmentIdMap.put((Integer)orig.get("RowId"), (Integer)inserted.get("RowId"));
                }
            }
        }
    }

    /**
     * Transform which manages foreign keys from the TreatmentProductMap table to the Treatment and Product tables
     */
    private class TreatmentProductTransform implements TransformHelper
    {
        @Override
        public List<Map<String, Object>> transform(StudyImportContext ctx, List<Map<String, Object>> origRows)
        {
            List<Map<String, Object>> newRows = new ArrayList<>();

            for (Map<String, Object> row : origRows)
            {
                Map<String, Object> newRow = new CaseInsensitiveHashMap<>();
                newRows.add(newRow);
                newRow.putAll(row);

                if (newRow.containsKey("ProductId") && _productIdMap.containsKey(newRow.get("ProductId")))
                {
                    newRow.put("ProductId", _productIdMap.get(newRow.get("ProductId")));
                }

                if (newRow.containsKey("TreatmentId") && _treatmentIdMap.containsKey(newRow.get("TreatmentId")))
                {
                    newRow.put("TreatmentId", _treatmentIdMap.get(newRow.get("TreatmentId")));
                }
            }
            return newRows;
        }
    }

    /**
     * Transform which manages cohort, visit, and treatment FKs from the TreatmentVisitMap table
     */
    private class TreatmentVisitMapTransform implements TransformHelper
    {
        private void initializeDataMaps(StudyImportContext ctx)
        {
            Study study = StudyService.get().getStudy(ctx.getContainer());

            if (_cohortMap.isEmpty())
            {
                for (CohortImpl cohort : StudyManager.getInstance().getCohorts(ctx.getContainer(), ctx.getUser()))
                {
                    _cohortMap.put(cohort.getLabel(), cohort);
                }
            }

            if (_visitMap.isEmpty())
            {
                for (Visit visit : StudyManager.getInstance().getVisits(study, Visit.Order.SEQUENCE_NUM))
                {
                    _visitMap.put(visit.getSequenceNumMin(), visit);
                }
            }
        }

        @Override
        public List<Map<String, Object>> transform(StudyImportContext ctx, List<Map<String, Object>> origRows)
        {
            List<Map<String, Object>> newRows = new ArrayList<>();
            initializeDataMaps(ctx);

            for (Map<String, Object> row : origRows)
            {
                Map<String, Object> newRow = new CaseInsensitiveHashMap<>();
                newRows.add(newRow);
                newRow.putAll(row);

                if (newRow.containsKey("cohortId") && newRow.containsKey("cohortId.label"))
                {
                    CohortImpl cohort = _cohortMap.get(newRow.get("cohortId.label"));
                    if (cohort != null)
                        newRow.put("cohortId", cohort.getRowId());
                    else
                        LOG.warn("No cohort found matching the label : " + newRow.get("cohortId.label"));

                    newRow.remove("cohortId.label");
                }

                if (newRow.containsKey("visitId") && newRow.containsKey("visitId.sequenceNumMin"))
                {
                    Visit visit = _visitMap.get(Double.parseDouble(String.valueOf(newRow.get("visitId.sequenceNumMin"))));
                    if (visit != null)
                        newRow.put("visitId", visit.getId());
                    else
                        LOG.warn("No visit found matching the sequence num : " + newRow.get("visitId.sequenceNumMin"));

                    newRow.remove("visitId.sequenceNumMin");
                }

                if (newRow.containsKey("TreatmentId") && _treatmentIdMap.containsKey(newRow.get("TreatmentId")))
                {
                    newRow.put("TreatmentId", _treatmentIdMap.get(newRow.get("TreatmentId")));
                }
            }
            return newRows;
        }
    }
}
