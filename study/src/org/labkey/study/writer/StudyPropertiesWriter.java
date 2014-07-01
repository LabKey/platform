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
package org.labkey.study.writer;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.writer.VirtualFile;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.query.StudyQuerySchema;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by klum on 3/17/14.
 */
public class StudyPropertiesWriter extends DefaultStudyDesignWriter
{
    public static final String SCHEMA_FILENAME = "study_metadata.xml";

    /**
     * Exports additional study related properties into the properties sub folder
     */
    public void write(StudyImpl study, StudyExportContext ctx, VirtualFile dir) throws Exception
    {
        Set<String> studyTableNames = new HashSet<>();
        StudyQuerySchema schema = StudyQuerySchema.createSchema(study, ctx.getUser(), true);
        StudyQuerySchema projectSchema = ctx.isDataspaceProject() ? new StudyQuerySchema(StudyManager.getInstance().getStudy(ctx.getProject()), ctx.getUser(), true) : schema;

        studyTableNames.add(StudyQuerySchema.PERSONNEL_TABLE_NAME);
        studyTableNames.add(StudyQuerySchema.PROPERTIES_TABLE_NAME);
        writeTableInfos(ctx, dir, studyTableNames, schema, projectSchema, SCHEMA_FILENAME);

        studyTableNames.add(StudyQuerySchema.OBJECTIVE_TABLE_NAME);
        studyTableNames.remove(StudyQuerySchema.PERSONNEL_TABLE_NAME);
        writeTableData(ctx, dir, studyTableNames, schema, projectSchema, null);
        writePersonnelData(ctx, dir);
    }

    private void writePersonnelData(StudyExportContext ctx, VirtualFile vf) throws Exception
    {
        StudyQuerySchema schema = StudyQuerySchema.createSchema(StudyManager.getInstance().getStudy(ctx.getContainer()), ctx.getUser(), true);
        TableInfo tableInfo = schema.getTable(StudyQuerySchema.PERSONNEL_TABLE_NAME);

        // we want to include the user display name so we can resolve during import
        FieldKey fieldKey = FieldKey.fromParts("userId", "displayName");

        Map<FieldKey, ColumnInfo> extraColumns = QueryService.get().getColumns(tableInfo, Collections.singletonList(fieldKey));
        List<ColumnInfo> columns = getDefaultColumns(ctx, tableInfo);
        columns.add(extraColumns.get(fieldKey));

        writeTableData(ctx, vf, tableInfo, columns, null);
    }
}
