/*
 * Copyright (c) 2013-2014 LabKey Corporation
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
import org.labkey.api.query.FieldKey;
import org.labkey.study.StudySchema;
import org.labkey.study.query.StudyQuerySchema;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * User: cnathe
 * Date: 7/24/13
 */
public class StudyDesignAssaysTable extends StudyDesignLookupBaseTable
{
    public StudyDesignAssaysTable(StudyQuerySchema schema, ContainerFilter filter)
    {
        super(schema, StudySchema.getInstance().getTableInfoStudyDesignAssays(), filter);
        setName("StudyDesignAssays");

        List<FieldKey> defaultColumns = new ArrayList<>(Arrays.asList(
                FieldKey.fromParts("Name"),
                FieldKey.fromParts("Label"),
                FieldKey.fromParts("Description"),
                FieldKey.fromParts("Inactive"),
                FieldKey.fromParts("Type"),
                FieldKey.fromParts("TargetType"),
                FieldKey.fromParts("TargetSubtype"),
                FieldKey.fromParts("Platform"),
                FieldKey.fromParts("Category"),
                FieldKey.fromParts("TargetFunction"),
                FieldKey.fromParts("LeadContributor"),
                FieldKey.fromParts("Contact"),
                FieldKey.fromParts("Summary"),
                FieldKey.fromParts("Keywords"),
                FieldKey.fromParts("Editorial"),
                FieldKey.fromParts("AlternateName"),
                FieldKey.fromParts("Lab"),
                FieldKey.fromParts("LabPI")
        ));
        setDefaultVisibleColumns(defaultColumns);
    }
}
