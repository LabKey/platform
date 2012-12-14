/*
 * Copyright (c) 2006-2012 LabKey Corporation
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
import org.labkey.api.data.ContainerForeignKey;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.NullColumnInfo;
import org.labkey.api.query.AliasedColumn;
import org.labkey.study.CohortForeignKey;
import org.labkey.study.StudySchema;
import org.labkey.study.model.StudyManager;

public class VisitTable extends BaseStudyTable
{
    public VisitTable(StudyQuerySchema schema)
    {
        super(schema, StudySchema.getInstance().getTableInfoVisit());
        addColumn(new AliasedColumn(this, "RowId", _rootTable.getColumn("RowId")));
        addColumn(new AliasedColumn(this, "TypeCode", _rootTable.getColumn("TypeCode")));
        addColumn(new AliasedColumn(this, "SequenceNumMin", _rootTable.getColumn("SequenceNumMin")));
        addColumn(new AliasedColumn(this, "SequenceNumMax", _rootTable.getColumn("SequenceNumMax")));
        addColumn(new AliasedColumn(this, "Label", _rootTable.getColumn("Label")));
        addColumn(new AliasedColumn(this, "Folder", _rootTable.getColumn("Container"))).setFk(new ContainerForeignKey(schema));
        addColumn(new AliasedColumn(this, "ShowByDefault", _rootTable.getColumn("ShowByDefault")));
        addWrapColumn(_rootTable.getColumn("DisplayOrder"));
        addWrapColumn(_rootTable.getColumn("ChronologicalOrder"));

        boolean showCohorts = StudyManager.getInstance().showCohorts(schema.getContainer(), schema.getUser());
        ColumnInfo cohortColumn;
        if (showCohorts)
        {
            cohortColumn = new AliasedColumn(this, "Cohort", _rootTable.getColumn("CohortId"));
        }
        else
        {
            cohortColumn = new NullColumnInfo(this, "Cohort", JdbcType.INTEGER);
        }
        cohortColumn.setFk(new CohortForeignKey(schema, showCohorts, cohortColumn.getLabel()));
        addColumn(cohortColumn);
        setTitleColumn("Label");
    }
}
