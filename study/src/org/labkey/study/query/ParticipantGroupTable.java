package org.labkey.study.query;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.query.AliasedColumn;
import org.labkey.api.query.QueryForeignKey;
import org.labkey.api.study.StudyService;
import org.labkey.study.StudySchema;

/**
 * Copyright (c) 2008-2010 LabKey Corporation
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * <p/>
 * <p/>
 * User: brittp
 * Date: Jun 28, 2011 12:50:44 PM
 */
public class ParticipantGroupTable extends BaseStudyTable
{
    public ParticipantGroupTable(StudyQuerySchema schema)
    {
        super(schema, StudySchema.getInstance().getTableInfoParticipantGroup());
        setName(StudyService.get().getSubjectTableName(schema.getContainer()));

        ColumnInfo rowIdColumn = addWrapColumn(_rootTable.getColumn("RowId"));
        rowIdColumn.setHidden(true);
        rowIdColumn.setUserEditable(false);
        rowIdColumn.setKeyField(true);

        ColumnInfo classificationIdColumn = new AliasedColumn(this, "ClassificationId", _rootTable.getColumn("ClassificationId"));
        classificationIdColumn.setFk(new QueryForeignKey(_schema, StudyService.get().getSubjectClassificationTableName(getContainer()), "RowId", "Label"));
        addColumn(classificationIdColumn);

        addWrapColumn(_rootTable.getColumn("Label"));
    }
}
