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

import org.labkey.api.writer.VirtualFile;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.query.StudyQuerySchema;

import java.util.HashSet;
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
        writeTableData(ctx, dir, studyTableNames, schema, projectSchema, null);
    }
}
