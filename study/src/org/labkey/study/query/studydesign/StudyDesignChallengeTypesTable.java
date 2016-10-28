/*
 * Copyright (c) 2016 LabKey Corporation
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
package org.labkey.study.query.studydesign;

import org.labkey.api.data.ContainerFilter;
import org.labkey.study.StudySchema;
import org.labkey.study.query.StudyQuerySchema;

public class StudyDesignChallengeTypesTable extends StudyDesignLookupBaseTable
{
    public StudyDesignChallengeTypesTable(StudyQuerySchema schema, ContainerFilter filter)
    {
        super(schema, StudySchema.getInstance().getTableInfoStudyDesignChallengeTypes(), filter);
        setName("StudyDesignChallengeTypes");
    }
}
