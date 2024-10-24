/*
 * Copyright (c) 2009-2019 LabKey Corporation
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
package org.labkey.study.query;

import org.labkey.api.data.ContainerFilter;
import org.labkey.api.specimen.SpecimenSchema;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.TimepointType;

public class SimpleSpecimenTable extends AbstractSpecimenTable
{
    public SimpleSpecimenTable(StudyQuerySchema schema, ContainerFilter cf)
    {
        super(schema, SpecimenSchema.get().getTableInfoSpecimen(schema.getContainer()), cf, true);
        setName("SimpleSpecimen");

        getMutableColumn(StudyService.get().getSubjectColumnName(getContainer())).clearFk();

        addSpecimenVisitColumn(TimepointType.DATE, true);

        addSpecimenTypeColumns();
    }
}
