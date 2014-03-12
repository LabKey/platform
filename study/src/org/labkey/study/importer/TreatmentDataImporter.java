/*
 * Copyright (c) 2014 LabKey Corporation
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
import java.util.Set;

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
    private ProductAntigenTableTransform _productAntigenTableTransform = new ProductAntigenTableTransform();
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
        ExportDirType dirType = ctx.getXml().getTreatmentData();

        if (dirType != null)
        {
            VirtualFile vf = root.getDir(dirType.getDir());
            if (vf != null)
            {
                // import the product table
                StudyQuerySchema schema = StudyQuerySchema.createSchema(StudyManager.getInstance().getStudy(ctx.getContainer()), ctx.getUser(), true);

                // add the treatment specific tables
                ctx.getLogger().info("Importing treatment data tables");
                TableInfo productTable = schema.getTable(StudyQuerySchema.PRODUCT_TABLE_NAME);
                deleteData(ctx, productTable);
                importTableData(ctx, vf, productTable, _productTableTransform, null);

                TableInfo productAntigenTable = schema.getTable(StudyQuerySchema.PRODUCT_ANTIGEN_TABLE_NAME);
                deleteData(ctx, productAntigenTable);
                importTableData(ctx, vf, productAntigenTable, null, _productAntigenTableTransform);

                TableInfo treatmentTable = schema.getTable(StudyQuerySchema.TREATMENT_TABLE_NAME);
                deleteData(ctx, treatmentTable);
                importTableData(ctx, vf, treatmentTable, _treatmentTableTransform, null);

                TableInfo treatmentProductTable = schema.getTable(StudyQuerySchema.TREATMENT_PRODUCT_MAP_TABLE_NAME);
                deleteData(ctx, treatmentProductTable);
                importTableData(ctx, vf, treatmentProductTable, null, _treatmentProductTransform);

                TableInfo treatmentVisitMapTable = schema.getTable(StudyQuerySchema.TREATMENT_VISIT_MAP_TABLE_NAME);
                deleteData(ctx, treatmentVisitMapTable);
                importTableData(ctx, vf, treatmentVisitMapTable, null, _treatmentVisitMapTransform);

                // study design tables
                ctx.getLogger().info("Importing study design data tables");
                List<TableInfo> studyDesignTables = new ArrayList<>();

                studyDesignTables.add(schema.getTable(StudyQuerySchema.STUDY_DESIGN_GENES_TABLE_NAME));
                studyDesignTables.add(schema.getTable(StudyQuerySchema.STUDY_DESIGN_ROUTES_TABLE_NAME));
                studyDesignTables.add(schema.getTable(StudyQuerySchema.STUDY_DESIGN_IMMUNOGEN_TYPES_TABLE_NAME));
                studyDesignTables.add(schema.getTable(StudyQuerySchema.STUDY_DESIGN_SUB_TYPES_TABLE_NAME));

                for (TableInfo table : studyDesignTables)
                {
                    deleteData(ctx, table);
                    importTableData(ctx, vf, table, null, null);
                }
            }
            else
                throw new ImportException("Unable to open the folder at : " + dirType.getDir());
        }
    }

    /**
     * Transform which manages foreign keys to the rowId of the Product table
     */
    private class ProductTableTransform implements TransformBuilder
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
                    _productIdMap.put((Integer)orig.get("RowId"), (Integer)inserted.get("RowId"));
                }
            }
        }
    }

    private class ProductAntigenTableTransform implements TransformHelper
    {
        @Override
        public List<Map<String, Object>> transform(StudyImportContext ctx, List<Map<String, Object>> origRows) throws ImportException
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
                else
                    throw new ImportException("Unable to locate productId in the imported rows");
            }
            return newRows;
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
        public List<Map<String, Object>> transform(StudyImportContext ctx, List<Map<String, Object>> origRows) throws ImportException
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
                else
                    throw new ImportException("Unable to locate productId in the imported rows");

                if (newRow.containsKey("TreatmentId") && _treatmentIdMap.containsKey(newRow.get("TreatmentId")))
                {
                    newRow.put("TreatmentId", _treatmentIdMap.get(newRow.get("TreatmentId")));
                }
                else
                    throw new ImportException("Unable to locate treatmentId in the imported rows");
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
        public List<Map<String, Object>> transform(StudyImportContext ctx, List<Map<String, Object>> origRows) throws ImportException
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
                        ctx.getLogger().warn("No cohort found matching the label : " + newRow.get("cohortId.label"));

                    newRow.remove("cohortId.label");
                }

                if (newRow.containsKey("visitId") && newRow.containsKey("visitId.sequenceNumMin"))
                {
                    Visit visit = _visitMap.get(Double.parseDouble(String.valueOf(newRow.get("visitId.sequenceNumMin"))));
                    if (visit != null)
                        newRow.put("visitId", visit.getId());
                    else
                        ctx.getLogger().warn("No visit found matching the sequence num : " + newRow.get("visitId.sequenceNumMin"));

                    newRow.remove("visitId.sequenceNumMin");
                }

                if (newRow.containsKey("TreatmentId") && _treatmentIdMap.containsKey(newRow.get("TreatmentId")))
                {
                    newRow.put("TreatmentId", _treatmentIdMap.get(newRow.get("TreatmentId")));
                }
                else
                    throw new ImportException("Unable to locate treatmentId in the imported rows");
            }
            return newRows;
        }
    }
}
