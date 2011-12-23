/*
 * Copyright (c) 2011 LabKey Corporation
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
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.query.UserIdForeignKey;
import org.labkey.api.query.UserIdRenderer;
import org.labkey.api.study.StudyService;
import org.labkey.study.StudySchema;

/**
 * User: brittp
 * Date: Jun 28, 2011 12:50:30 PM
 */
public class ParticipantCategoryTable extends BaseStudyTable
{
    public ParticipantCategoryTable(StudyQuerySchema schema)
    {
        super(schema, StudySchema.getInstance().getTableInfoParticipantCategory());
        setName(StudyService.get().getSubjectCategoryTableName(schema.getContainer()));

        ColumnInfo rowIdColumn = addWrapColumn(_rootTable.getColumn("RowId"));
        rowIdColumn.setHidden(true);
        rowIdColumn.setUserEditable(false);
        rowIdColumn.setKeyField(true);

        addWrapColumn(_rootTable.getColumn("Label"));
        addWrapColumn(_rootTable.getColumn("Type"));
        addWrapColumn(_rootTable.getColumn("Created"));

        ColumnInfo createdBy = wrapColumn("CreatedBy", getRealTable().getColumn("CreatedBy"));
        createdBy.setFk(new UserIdForeignKey());
        createdBy.setDisplayColumnFactory(new DisplayColumnFactory()
        {
            public DisplayColumn createRenderer(ColumnInfo colInfo)
            {
                return new UserIdRenderer(colInfo);
            }
        });
        addColumn(createdBy);
    }
}
