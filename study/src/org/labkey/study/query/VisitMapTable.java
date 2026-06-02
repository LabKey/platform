/*
 * Copyright (c) 2020-2026 LabKey Corporation
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
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.AliasedColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.query.QueryForeignKey;
import org.labkey.study.StudySchema;

public class VisitMapTable extends BaseStudyTable
{
    private QueryForeignKey.Builder studyFK()
    {
        return QueryForeignKey.from(_userSchema,getContainerFilter());
    }

    public VisitMapTable(StudyQuerySchema schema, ContainerFilter cf)
    {
        super(schema, StudySchema.getInstance().getTableInfoVisitMap(), cf);

        addFolderColumn();
        addWrapColumn(getRealTable().getColumn(FieldKey.fromParts("Required")));

        var visitIdColumn = wrapColumn("Visit", _rootTable.getColumn("VisitRowId"));
        LookupForeignKey visitIdFk = new LookupForeignKey()
        {
            @Override
            public TableInfo getLookupTableInfo()
            {
                return new VisitTable(_userSchema, getLookupContainerFilter());
            }
        };
        visitIdColumn.setFk(visitIdFk);
        addColumn(visitIdColumn);

        var dataSetColumn = new AliasedColumn(this, "DataSet", _rootTable.getColumn("DataSetId"));
        dataSetColumn.setFk(studyFK().to("DataSets", "DataSetId", "Name"));
        addColumn(dataSetColumn);
    }
}
