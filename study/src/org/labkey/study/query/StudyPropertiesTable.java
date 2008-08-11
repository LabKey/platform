/*
 * Copyright (c) 2008 LabKey Corporation
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

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.query.FieldKey;
import org.labkey.study.StudySchema;
import org.labkey.study.model.Study;
import org.labkey.study.model.StudyManager;

import java.util.ArrayList;
import java.util.List;

/**
 * User: jgarms
 * Date: Aug 7, 2008
 * Time: 4:23:44 PM
 */
public class StudyPropertiesTable extends BaseStudyTable
{
    public StudyPropertiesTable(StudyQuerySchema schema)
    {
        super(schema, StudySchema.getInstance().getTableInfoStudy());

        ColumnInfo containerColumn = addWrapColumn(_rootTable.getColumn("container"));
        containerColumn.setUserEditable(false);
        containerColumn.setIsHidden(true);
        containerColumn.setKeyField(true);

        ColumnInfo lsidColumn = addWrapColumn(_rootTable.getColumn("LSID"));
        lsidColumn.setUserEditable(false);
        lsidColumn.setIsHidden(true);

        List<FieldKey> visibleColumns = new ArrayList<FieldKey>();

        String domainURI = StudyManager.getInstance().getDomainURI(schema.getContainer(), Study.class);

        Domain domain = PropertyService.get().getDomain(schema.getContainer(), domainURI);
        if (domain != null)
        {
            ColumnInfo[] extraColumns = domain.getColumns(this, lsidColumn, schema.getUser());
            for (ColumnInfo extraColumn : extraColumns)
            {
                safeAddColumn(extraColumn);
                visibleColumns.add(FieldKey.fromParts(extraColumn.getName()));
            }
        }

        setDefaultVisibleColumns(visibleColumns);
    }
}
