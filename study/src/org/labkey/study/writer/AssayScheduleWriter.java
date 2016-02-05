/*
 * Copyright (c) 2014-2016 LabKey Corporation
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
package org.labkey.study.writer;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.writer.VirtualFile;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.query.StudyQuerySchema;
import org.labkey.study.xml.ExportDirType;
import org.labkey.study.xml.StudyDocument;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by klum on 1/21/14.
 */
public class AssayScheduleWriter extends DefaultStudyDesignWriter implements InternalStudyWriter
{
    private static final Logger LOG = Logger.getLogger(AssayScheduleWriter.class);
    private static final String DEFAULT_DIRECTORY = "assaySchedule";

    @Nullable
    @Override
    public String getDataType()
    {
        return StudyArchiveDataTypes.ASSAY_SCHEDULE;
    }

    @Override
    public void write(StudyImpl object, StudyExportContext ctx, VirtualFile root) throws Exception
    {
        StudyDocument.Study studyXml = ctx.getXml();

        ExportDirType dir = studyXml.addNewAssaySchedule();
        dir.setDir(DEFAULT_DIRECTORY);

        VirtualFile vf = root.getDir(DEFAULT_DIRECTORY);

        StudyQuerySchema schema = StudyQuerySchema.createSchema(StudyManager.getInstance().getStudy(ctx.getContainer()), ctx.getUser(), true);
        StudyQuerySchema projectSchema = ctx.isDataspaceProject() ? new StudyQuerySchema(StudyManager.getInstance().getStudy(ctx.getProject()), ctx.getUser(), true) : schema;

        // add the assay schedule specific tables
        TableInfo assaySpecimenTable = schema.getTable(StudyQuerySchema.ASSAY_SPECIMEN_TABLE_NAME);

        writeTableData(ctx, vf, assaySpecimenTable, getDefaultColumns(ctx, assaySpecimenTable), null);
        writeAssaySpecimenVisitMap(ctx, vf);

        // assay schedule lookup values can have data stored at both the project and folder level
        ContainerFilter containerFilter = new ContainerFilter.CurrentPlusProject(ctx.getUser());

        // export the study design tables (no need to export tableinfo's as these are non-extensible)
        Set<String> designTableNames = new HashSet<>();
        designTableNames.add(StudyQuerySchema.STUDY_DESIGN_ASSAYS_TABLE_NAME);
        designTableNames.add(StudyQuerySchema.STUDY_DESIGN_LABS_TABLE_NAME);
        designTableNames.add(StudyQuerySchema.STUDY_DESIGN_SAMPLE_TYPES_TABLE_NAME);
        designTableNames.add(StudyQuerySchema.STUDY_DESIGN_UNITS_TABLE_NAME);

        writeTableData(ctx, vf, designTableNames, schema, projectSchema, containerFilter);
    }

    private void writeAssaySpecimenVisitMap(StudyExportContext ctx, VirtualFile vf) throws Exception
    {
        StudyQuerySchema schema = StudyQuerySchema.createSchema(StudyManager.getInstance().getStudy(ctx.getContainer()), ctx.getUser(), true);
        TableInfo tableInfo = schema.getTable(StudyQuerySchema.ASSAY_SPECIMEN_VISIT_TABLE_NAME);

        List<FieldKey> fields = new ArrayList<>();
        fields.addAll(tableInfo.getDefaultVisibleColumns());

        // we want to include the visit sequence number so we can resolve during import
        fields.add(FieldKey.fromParts("visitId", "sequenceNumMin"));

        Map<FieldKey, ColumnInfo> columns = QueryService.get().getColumns(tableInfo, fields);
        writeTableData(ctx, vf, tableInfo, new ArrayList<>(columns.values()), null);
    }
}
