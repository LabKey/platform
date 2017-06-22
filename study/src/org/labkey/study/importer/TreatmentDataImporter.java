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
package org.labkey.study.importer;

import org.labkey.api.admin.ImportException;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbScope;
import org.labkey.api.writer.VirtualFile;
import org.labkey.study.StudySchema;
import org.labkey.study.model.StudyManager;
import org.labkey.study.query.StudyQuerySchema;
import org.labkey.study.writer.StudyArchiveDataTypes;
import org.labkey.study.writer.TreatmentDataWriter;
import org.labkey.study.xml.ExportDirType;
import org.springframework.validation.BindException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by klum on 1/22/14.
 */
public class TreatmentDataImporter extends DefaultStudyDesignImporter implements InternalStudyImporter
{
    // shared transform data structures
    Map<Object, Object> _productIdMap = new HashMap<>();
    Map<Object, Object> _productAntigenIdMap = new HashMap<>();
    Map<Object, Object> _treatmentIdMap = new HashMap<>();

    private SharedTableMapBuilder _productTableMapBuilder = new SharedTableMapBuilder(_productIdMap, "Label");
    private SharedTableMapBuilder _productAntigenTableMapBuilder = new SharedTableMapBuilder(_productAntigenIdMap, "GenBankId");
    private ProductAntigenTableTransform _productAntigenTableTransform = new ProductAntigenTableTransform();
    private NonSharedTableMapBuilder _treatmentTableMapBuilder = new NonSharedTableMapBuilder(_treatmentIdMap);
    private TreatmentProductTransform _treatmentProductTransform = new TreatmentProductTransform();

    private static final Set<String> _productAntigenFieldNames = new HashSet<>();
    static
    {
        _productAntigenFieldNames.add("ProductId");
        _productAntigenFieldNames.add("Gene");
        _productAntigenFieldNames.add("SubType");
        _productAntigenFieldNames.add("GenBankId");
        _productAntigenFieldNames.add("Sequence");
    }

    @Override
    public String getDescription()
    {
        return "treatment data";
    }

    public String getDataType()
    {
        return StudyArchiveDataTypes.TREATMENT_DATA;
    }

    @Override
    public void process(StudyImportContext ctx, VirtualFile root, BindException errors) throws Exception
    {
        if (!ctx.isDataTypeSelected(getDataType()))
            return;

        if (isValidForImportArchive(ctx, root))
        {
            ExportDirType dirType = ctx.getXml().getTreatmentData();

            VirtualFile vf = root.getDir(dirType.getDir());
            if (vf != null)
            {
                ctx.getLogger().info("Loading treatment data tables");

                Container c = ctx.getContainer();
                boolean isDataspaceProject = c.getProject() != null && c.getProject().isDataspace() && !c.isDataspace();

                DbScope scope = StudySchema.getInstance().getSchema().getScope();
                try (DbScope.Transaction transaction = scope.ensureTransaction())
                {
                    // import any custom treatment table properties
                    importTableinfo(ctx, vf, TreatmentDataWriter.SCHEMA_FILENAME);

                    // import project-level tables first, since study-level may reference them
                    StudyQuerySchema schema = StudyQuerySchema.createSchema(ctx.getStudy(), ctx.getUser(), true);
                    StudyQuerySchema projectSchema = ctx.isDataspaceProject() ? new StudyQuerySchema(StudyManager.getInstance().getStudy(ctx.getProject()), ctx.getUser(), true) : schema;

                    // study design tables
                    List<String> studyDesignTableNames = new ArrayList<>();
                    studyDesignTableNames.add(StudyQuerySchema.STUDY_DESIGN_GENES_TABLE_NAME);
                    studyDesignTableNames.add(StudyQuerySchema.STUDY_DESIGN_ROUTES_TABLE_NAME);
                    studyDesignTableNames.add(StudyQuerySchema.STUDY_DESIGN_IMMUNOGEN_TYPES_TABLE_NAME);
                    studyDesignTableNames.add(StudyQuerySchema.STUDY_DESIGN_SUB_TYPES_TABLE_NAME);
                    // this table was added in 16.3, so any archive created prior to that will not have this tsv file to import
                    if (ctx.getArchiveVersion() >= 16.3)
                        studyDesignTableNames.add(StudyQuerySchema.STUDY_DESIGN_CHALLENGE_TYPES_TABLE_NAME);

                    for (String studyDesignTableName : studyDesignTableNames)
                    {
                        StudyQuerySchema.TablePackage tablePackage = schema.getTablePackage(ctx, projectSchema, studyDesignTableName);
                        importTableData(ctx, vf, tablePackage, null, new PreserveExistingProjectData(ctx.getUser(), tablePackage.getTableInfo(), "Name", null, null));
                    }

                    // add the treatment specific tables
                    StudyQuerySchema.TablePackage productTablePackage = schema.getTablePackage(ctx, projectSchema, StudyQuerySchema.PRODUCT_TABLE_NAME);
                    PreserveExistingProjectData productTransform = !isDataspaceProject ? null //Issue 28858
                            : new PreserveExistingProjectData(ctx.getUser(), productTablePackage.getTableInfo(), "Label", "RowId", _productIdMap);
                    importTableData(ctx, vf, productTablePackage, _productTableMapBuilder, productTransform);

                    // product antigen table
                    StudyQuerySchema.TablePackage productAntigenTablePackage = schema.getTablePackage(ctx, projectSchema, StudyQuerySchema.PRODUCT_ANTIGEN_TABLE_NAME);
                    List<TransformHelper> transformHelpers = new ArrayList<>();
                    TransformHelper transformHelperComp;

                    transformHelpers.add(_productAntigenTableTransform);        // Transform ProductIds first
                    transformHelpers.add(new PreserveExistingProjectData(ctx.getUser(), productAntigenTablePackage.getTableInfo(), _productAntigenFieldNames));
                    transformHelperComp = new TransformHelperComposition(transformHelpers);
                    importTableData(ctx, vf, productAntigenTablePackage, _productAntigenTableMapBuilder, transformHelperComp);

                    // dose and route table, we can reuse the product antigen transform since we are doing the same operation : productId translation
                    // this table was added in 16.3, so any archive created prior to that will not have this tsv file to import
                    if (ctx.getArchiveVersion() >= 16.3)
                    {
                        StudyQuerySchema.TablePackage doseAndRouteTablePackage = schema.getTablePackage(ctx, projectSchema, StudyQuerySchema.DOSE_AND_ROUTE_TABLE_NAME);
                        importTableData(ctx, vf, doseAndRouteTablePackage, null, new TransformHelperComposition(Collections.singletonList(_productAntigenTableTransform)));
                    }

                    StudyQuerySchema.TablePackage treatmentTablePackage = schema.getTablePackage(ctx, projectSchema, StudyQuerySchema.TREATMENT_TABLE_NAME);
                    importTableData(ctx, vf, treatmentTablePackage, _treatmentTableMapBuilder, null);

                    StudyQuerySchema.TablePackage treatmentProductTablePackage = schema.getTablePackage(ctx, projectSchema, StudyQuerySchema.TREATMENT_PRODUCT_MAP_TABLE_NAME);
                    importTableData(ctx, vf, treatmentProductTablePackage, null, _treatmentProductTransform);

                    // Note: TreatmentVisitMap info needs to import after cohorts are loaded (issue 19947).
                    // That part of the TreatmentDataImporter will happen separately and be called accordingly (see TreatmentVisitMapImporter)
                    ctx.addTableIdMap("Treatment", _treatmentIdMap);
                    ctx.addTableIdMap("Product", _productIdMap);
//                    ctx.addTableIdMap("ProductAntigen", _productAntigenIdMap);        // TODO: this goes with the transform above that populates the map

                    transaction.commit();
                }

                ctx.getLogger().info("Done importing treatment data tables");
            }
            else
                throw new ImportException("Unable to open the folder at : " + dirType.getDir());
        }
    }

    @Override
    public boolean isValidForImportArchive(StudyImportContext ctx, VirtualFile root) throws ImportException
    {
        return ctx.getXml() != null && ctx.getXml().getTreatmentData() != null;
    }

    private class ProductAntigenTableTransform implements TransformHelper
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
                else
                    ctx.getLogger().warn("Unable to locate productId in the imported rows, this record will be ignored");
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
                else
                    ctx.getLogger().warn("Unable to locate productId in the imported rows, this record will be ignored");

                if (newRow.containsKey("TreatmentId") && _treatmentIdMap.containsKey(newRow.get("TreatmentId")))
                {
                    newRow.put("TreatmentId", _treatmentIdMap.get(newRow.get("TreatmentId")));
                }
                else
                    ctx.getLogger().warn("Unable to locate treatmentId in the imported rows, this record will be ignored");
            }
            return newRows;
        }
    }
}
