/*
 * Copyright (c) 2011-2016 LabKey Corporation
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
import org.labkey.api.query.AliasedColumn;
import org.labkey.api.query.QueryForeignKey;
import org.labkey.api.study.StudyService;
import org.labkey.study.StudySchema;

/**
 * User: brittp
 * Date: Jun 28, 2011 12:50:58 PM
 */
public class ParticipantGroupMapTable extends BaseStudyTable
{
    public ParticipantGroupMapTable(StudyQuerySchema schema)
    {
        super(schema, StudySchema.getInstance().getTableInfoParticipantGroupMap());
        setName(StudyService.get().getSubjectGroupMapTableName(schema.getContainer()));
        setDescription("This table contains study group membership information");

        ColumnInfo groupIdColumn = new AliasedColumn(this, "GroupId", _rootTable.getColumn("GroupId"));
        groupIdColumn.setFk(new QueryForeignKey(_userSchema, null, StudyService.get().getSubjectGroupTableName(getContainer()), "RowId", "Label"));
        addColumn(groupIdColumn);
        addWrapParticipantColumn("ParticipantId");
        addContainerColumn();
    }
}
