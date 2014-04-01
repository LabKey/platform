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
import org.labkey.api.data.DbScope;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.Visit;
import org.labkey.api.writer.VirtualFile;
import org.labkey.study.StudySchema;
import org.labkey.study.model.CohortImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.query.StudyQuerySchema;
import org.labkey.study.writer.TreatmentDataWriter;
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
    Map<Object, Object> _productIdMap = new HashMap<>();
    Map<Object, Object> _productAntigenIdMap = new HashMap<>();
    Map<Object, Object> _treatmentIdMap = new HashMap<>();

    Map<String, CohortImpl> _cohortMap = new HashMap<>();
    Map<Double, Visit> _visitMap = new HashMap<>();

    private SharedTableMapBuilder _productTableMapBuilder = new SharedTableMapBuilder(_productIdMap, "Label");
    private SharedTableMapBuilder _productAntigenTableMapBuilder = new SharedTableMapBuilder(_productAntigenIdMap, "GenBankId");
    private ProductAntigenTableTransform _productAntigenTableTransform = new ProductAntigenTableTransform();
    private NonSharedTableMapBuilder _treatmentTableMapBuilder = new NonSharedTableMapBuilder(_treatmentIdMap);
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
                DbScope scope = StudySchema.getInstance().getSchema().getScope();
                try (DbScope.Transaction transaction = scope.ensureTransaction())
                {
                    // import any custom treatment table properties
                    importTableinfo(ctx, vf, TreatmentDataWriter.SCHEMA_FILENAME);

                    // import project-level tables first, since study-level may reference them
                    StudyQuerySchema schema = StudyQuerySchema.createSchema(StudyManager.getInstance().getStudy(ctx.getContainer()), ctx.getUser(), true);
                    StudyQuerySchema projectSchema = ctx.isDataspaceProject() ? new StudyQuerySchema(StudyManager.getInstance().getStudy(ctx.getProject()), ctx.getUser(), true) : schema;

                    // study design tables
                    ctx.getLogger().info("Importing study design data tables");
                    List<String> studyDesignTableNames = new ArrayList<>();

                    studyDesignTableNames.add(StudyQuerySchema.STUDY_DESIGN_GENES_TABLE_NAME);
                    studyDesignTableNames.add(StudyQuerySchema.STUDY_DESIGN_ROUTES_TABLE_NAME);
                    studyDesignTableNames.add(StudyQuerySchema.STUDY_DESIGN_IMMUNOGEN_TYPES_TABLE_NAME);
                    studyDesignTableNames.add(StudyQuerySchema.STUDY_DESIGN_SUB_TYPES_TABLE_NAME);

                    for (String studyDesignTableName : studyDesignTableNames)
                    {
                        StudyQuerySchema.TablePackage tablePackage = schema.getTablePackage(ctx, projectSchema, studyDesignTableName);
                        importTableData(ctx, vf, tablePackage, null, new PreserveExistingProjectData(ctx.getUser(), tablePackage.getTableInfo(), "Name", null, null));
                    }

                    // add the treatment specific tables
                    ctx.getLogger().info("Importing treatment data tables");
                    StudyQuerySchema.TablePackage productTablePackage = schema.getTablePackage(ctx, projectSchema, StudyQuerySchema.PRODUCT_TABLE_NAME);
                    importTableData(ctx, vf, productTablePackage, _productTableMapBuilder,
                           ctx.isDataspaceProject() ? new PreserveExistingProjectData(ctx.getUser(), productTablePackage.getTableInfo(), "Label", "RowId", _productIdMap) : null);

                    StudyQuerySchema.TablePackage productAntigenTablePackage = schema.getTablePackage(ctx, projectSchema, StudyQuerySchema.PRODUCT_ANTIGEN_TABLE_NAME);
                    List<TransformHelper> transformHelpers = new ArrayList<>();
                    TransformHelper transformHelperComp = null;
                    if (ctx.isDataspaceProject())
                    {
//                        transformHelpers.add(new PreserveExistingProjectData(ctx.getUser(), productAntigenTablePackage.getTableInfo(), "GenBankId", "RowId", _productAntigenIdMap));   // TODO: this table needs a Label field or something
                    }
                    transformHelpers.add(_productAntigenTableTransform);
                    transformHelperComp = new TransformHelperComposition(transformHelpers);
                    importTableData(ctx, vf, productAntigenTablePackage, _productAntigenTableMapBuilder, transformHelperComp);

                    StudyQuerySchema.TablePackage treatmentTablePackage = schema.getTablePackage(ctx, projectSchema, StudyQuerySchema.TREATMENT_TABLE_NAME);
                    importTableData(ctx, vf, treatmentTablePackage, _treatmentTableMapBuilder, null);

                    StudyQuerySchema.TablePackage treatmentProductTablePackage = schema.getTablePackage(ctx, projectSchema, StudyQuerySchema.TREATMENT_PRODUCT_MAP_TABLE_NAME);
                    importTableData(ctx, vf, treatmentProductTablePackage, null, _treatmentProductTransform);

                    StudyQuerySchema.TablePackage treatmentVisitMapTablePackage = schema.getTablePackage(ctx, projectSchema, StudyQuerySchema.TREATMENT_VISIT_MAP_TABLE_NAME);
                    importTableData(ctx, vf, treatmentVisitMapTablePackage, null, _treatmentVisitMapTransform);

                    if (ctx.isDataspaceProject())
                    {
                        ctx.setProductIdMap(_productIdMap);
                        ctx.setProductAntigenIdMap(_productAntigenIdMap);
                        ctx.setTreatmentIdMap(_treatmentIdMap);
                    }

                    transaction.commit();
                }
            }
            else
                throw new ImportException("Unable to open the folder at : " + dirType.getDir());
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
