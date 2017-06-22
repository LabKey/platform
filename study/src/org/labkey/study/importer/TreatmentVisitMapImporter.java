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
import org.labkey.api.data.DbScope;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.Visit;
import org.labkey.api.writer.VirtualFile;
import org.labkey.study.StudySchema;
import org.labkey.study.model.CohortImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.query.StudyQuerySchema;
import org.labkey.study.writer.StudyArchiveDataTypes;
import org.labkey.study.xml.ExportDirType;
import org.springframework.validation.BindException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by cnathe on 4/4/14.
 */
public class TreatmentVisitMapImporter extends DefaultStudyDesignImporter implements InternalStudyImporter
{
    Map<Object, Object> _treatmentIdMap = new HashMap<>();
    Map<String, CohortImpl> _cohortMap = new HashMap<>();
    Map<Double, Visit> _visitMap = new HashMap<>();

    private TreatmentVisitMapTransform _treatmentVisitMapTransform = new TreatmentVisitMapTransform();

    @Override
    public String getDescription()
    {
        return "treatment visit map data";
    }

    @Override
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

            ctx.getLogger().info("Loading " + getDescription());

            VirtualFile vf = root.getDir(dirType.getDir());
            if (vf != null)
            {
                DbScope scope = StudySchema.getInstance().getSchema().getScope();
                try (DbScope.Transaction transaction = scope.ensureTransaction())
                {
                    StudyQuerySchema schema = StudyQuerySchema.createSchema(ctx.getStudy(), ctx.getUser(), true);
                    StudyQuerySchema projectSchema = ctx.isDataspaceProject() ? new StudyQuerySchema(StudyManager.getInstance().getStudy(ctx.getProject()), ctx.getUser(), true) : schema;

                    // Note: TreatmentVisitMap info needs to import after cohorts are loaded (issue 19947).
                    StudyQuerySchema.TablePackage treatmentVisitMapTablePackage = schema.getTablePackage(ctx, projectSchema, StudyQuerySchema.TREATMENT_VISIT_MAP_TABLE_NAME);
                    importTableData(ctx, vf, treatmentVisitMapTablePackage, null, _treatmentVisitMapTransform);

                    transaction.commit();
                }
            }
            else
                throw new ImportException("Unable to open the folder at : " + dirType.getDir());

            ctx.getLogger().info("Done importing " + getDescription());
        }
    }

    @Override
    public boolean isValidForImportArchive(StudyImportContext ctx, VirtualFile root) throws ImportException
    {
        return ctx.getXml() != null && ctx.getXml().getTreatmentData() != null;
    }

    private class TreatmentVisitMapTransform implements TransformHelper
    {
        private void initializeDataMaps(StudyImportContext ctx)
        {
            Study study = StudyService.get().getStudy(ctx.getContainer());

            _treatmentIdMap = ctx.getTableIdMap("Treatment");

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
                    Object cohortObj = newRow.get("cohortId.label");
                    if (null != cohortObj)
                    {
                        CohortImpl cohort = _cohortMap.get(cohortObj);
                        if (cohort != null)
                            newRow.put("cohortId", cohort.getRowId());
                        else
                            ctx.getLogger().warn("No cohort found matching the label : " + newRow.get("cohortId.label"));
                    }
                    else
                        ctx.getLogger().warn("Null cohortId found.");

                    newRow.remove("cohortId.label");
                }

                if (newRow.containsKey("visitId") && newRow.containsKey("visitId.sequenceNumMin"))
                {
                    Object sequenceObj = newRow.get("visitId.sequenceNumMin");
                    if (null != sequenceObj)
                    {
                        Visit visit = _visitMap.get(Double.parseDouble(String.valueOf(sequenceObj)));
                        if (visit != null)
                            newRow.put("visitId", visit.getId());
                        else
                            ctx.getLogger().warn("No visit found matching the sequence num : " + newRow.get("visitId.sequenceNumMin"));
                    }
                    else
                        ctx.getLogger().warn("Null sequence num found.");

                    newRow.remove("visitId.sequenceNumMin");
                }

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
