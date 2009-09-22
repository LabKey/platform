/*
 * Copyright (c) 2006-2009 LabKey Corporation
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

import org.labkey.api.data.*;
import org.labkey.api.query.*;
import org.labkey.api.util.StringExpression;
import org.labkey.api.view.ActionURL;
import org.labkey.api.study.Study;
import org.labkey.study.StudySchema;
import org.labkey.study.model.StudyManager;

import java.util.Collections;
import java.util.Map;

public class ParticipantTable extends FilteredTable
{
    StudyQuerySchema _schema;

    public ParticipantTable(StudyQuerySchema schema)
    {
        super(StudySchema.getInstance().getTableInfoParticipant(), schema.getContainer());
        _schema = schema;
        ColumnInfo rowIdColumn = new AliasedColumn(this, "ParticipantId", _rootTable.getColumn("ParticipantId"));
        rowIdColumn.setFk(new TitleForeignKey(getBaseDetailsURL(), null, null, "participantId"));

        addColumn(rowIdColumn);
        ColumnInfo datasetColumn = new AliasedColumn(this, "DataSet", _rootTable.getColumn("ParticipantId"));
        datasetColumn.setIsUnselectable(true);
        datasetColumn.setLabel("DataSet");
        datasetColumn.setFk(new AbstractForeignKey()
        {
            public ColumnInfo createLookupColumn(ColumnInfo parent, String displayField)
            {
                if (displayField == null)
                    return null;
                return new ParticipantDataSetTable(_schema, parent).getColumn(displayField);
            }

            public TableInfo getLookupTableInfo()
            {
                return new ParticipantDataSetTable(_schema, null);
            }

            public StringExpression getURL(ColumnInfo parent)
            {
                return null;
            }
        }
        );
        addColumn(datasetColumn);

        if (StudyManager.getInstance().showCohorts(schema.getContainer(), schema.getUser()))
        {
            ColumnInfo currentCohortColumn = new AliasedColumn(this, "Cohort", _rootTable.getColumn("CurrentCohortId"));
            currentCohortColumn.setFk(new LookupForeignKey("RowId")
            {
                public TableInfo getLookupTableInfo()
                {
                    return new CohortTable(_schema);
                }
            });
            addColumn(currentCohortColumn);

            Study study = StudyManager.getInstance().getStudy(schema.getContainer());

            if (study.isAdvancedCohorts())
            {
                ColumnInfo initialCohortColumn = new AliasedColumn(this, "InitialCohort", _rootTable.getColumn("InitialCohortId"));
                initialCohortColumn.setFk(new LookupForeignKey("RowId")
                {
                    public TableInfo getLookupTableInfo()
                    {
                        return new CohortTable(_schema);
                    }
                });
                addColumn(initialCohortColumn);
            }
        }
        
        ForeignKey fkSite = SiteTable.fkFor(_schema);
        addWrapColumn(_rootTable.getColumn("EnrollmentSiteId")).setFk(fkSite);
        addWrapColumn(_rootTable.getColumn("CurrentSiteId")).setFk(fkSite);
        addWrapColumn(_rootTable.getColumn("StartDate"));
        setTitleColumn("ParticipantId");
    }

    public ActionURL getBaseDetailsURL()
    {
        return new ActionURL("Study", "participant", _schema.getContainer());
    }

    public StringExpression getDetailsURL(Map<String, ColumnInfo> columns, Container c)
    {
        ColumnInfo colRowId = columns.get("ParticipantId");
        if (colRowId == null)
            return null;
        return new LookupURLExpression(getBaseDetailsURL(), Collections.singletonMap("participantId", colRowId));
    }
}
