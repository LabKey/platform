/*
 * Copyright (c) 2021-2026 LabKey Corporation
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
package org.labkey.api.specimen;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;

import java.util.Set;

public class SpecimenQuerySchema extends UserSchema
{
    public static final String SCHEMA_NAME = "Study";
    public static final String LOCATION_TABLE_NAME = "Location";

    private final UserSchema _studySchema;
    private final Study _study;

    public SpecimenQuerySchema(Study study, UserSchema studySchema)
    {
        super(studySchema.getName(), studySchema.getDescription(), studySchema.getUser(), studySchema.getContainer(), studySchema.getDbSchema());
        _study = study;
        _studySchema = studySchema;
    }

    public static SpecimenQuerySchema get(Study study, User user)
    {
        return new SpecimenQuerySchema(study, StudyService.get().getStudyQuerySchema(study, user));
    }

    public Study getStudy()
    {
        return _study;
    }

    @Override
    public @Nullable TableInfo createTable(String name, ContainerFilter cf)
    {
        return _studySchema.createTable(name, cf);
    }

    @Override
    public Set<String> getTableNames()
    {
        return _studySchema.getTableNames();
    }
}
