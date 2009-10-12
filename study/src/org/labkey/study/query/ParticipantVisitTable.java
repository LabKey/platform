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
import org.labkey.api.query.AliasedColumn;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.AliasManager;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.util.StringExpression;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.study.model.DataSetDefinition;
import org.labkey.study.model.StudyManager;
import org.labkey.study.StudySchema;

public class ParticipantVisitTable extends FilteredTable
{
    StudyQuerySchema _schema;
    SqlDialect _dialect;

    public ParticipantVisitTable(StudyQuerySchema schema)
    {
        super(StudySchema.getInstance().getTableInfoParticipantVisit(), schema.getContainer());
        _schema = schema;
        _dialect = _schema.getDbSchema().getSqlDialect();

        /*
        ColumnInfo keyPV = new ParticipantVisitColumn("ParticipantVisit", this, _rootTable.getColumn("ParticipantId"), _rootTable.getColumn("SequenceNum"));
        keyPV.setHidden(true);
        keyPV.setIsUnselectable(true);
        addColumn(keyPV);
        */
        ColumnInfo participantSequenceKeyColumn = null;
        for (ColumnInfo col : _rootTable.getColumns())
        {
            if ("Container".equalsIgnoreCase(col.getName()))
                continue;
            else if ("VisitRowId".equalsIgnoreCase(col.getName()))
            {
                ColumnInfo visitColumn = new AliasedColumn(this, "Visit", col);
                visitColumn.setFk(new LookupForeignKey("RowId")
                {
                    public TableInfo getLookupTableInfo()
                    {
                        return new VisitTable(_schema);
                    }
                });
                addColumn(visitColumn);
            }
            else if ("CohortID".equalsIgnoreCase(col.getName()))
            {
                if (StudyManager.getInstance().showCohorts(getContainer(), schema.getUser()))
                {
                    ColumnInfo cohortColumn = new AliasedColumn(this, "Cohort", col);
                    cohortColumn.setFk(new LookupForeignKey("RowId")
                    {
                        public TableInfo getLookupTableInfo()
                        {
                            return new CohortTable(_schema);
                        }
                    });
                    addColumn(cohortColumn);
                }
            }
            else if ("ParticipantSequenceKey".equalsIgnoreCase(col.getName()))
            {
                participantSequenceKeyColumn = addWrapColumn(col);
                participantSequenceKeyColumn.setHidden(true);
            }
            else
                addWrapColumn(col);
        }

// it would be nice to avoid lookup back to source table
// however, getLookupTableInfo does not pass in the foreigh key column
//        int foreignDatasetId = -1;
//        if (pv instanceof ParticipantVisitColumn && pv.getParentTable() instanceof DataSetTable)
//            foreignDatasetId = ((DataSetTable)pv.getParentTable()).getDatasetDefinition().getDataSetId();

        for (DataSetDefinition dataset : _schema.getStudy().getDataSets())
        {
// don't do a lookup back to myself
//            if (dataset.getDataSetId() == foreignDatasetId)
//                continue;

            String name = _schema.decideTableName(dataset);
            if (name == null)
                continue;

            // duplicate labels! see BUG 2206
            if (getColumn(name) != null)
                continue;

            // if not keyed by Participant/SequenceNum it is not a lookup
            if (dataset.getKeyPropertyName() != null)
                continue;

            ColumnInfo datasetColumn = createDataSetColumn(name, dataset, participantSequenceKeyColumn);
            addColumn(datasetColumn);
        }
    }


    protected ColumnInfo createDataSetColumn(String name, final DataSetDefinition dsd, ColumnInfo participantSequenceKeyColumn)
    {
        ColumnInfo ret = new AliasedColumn(name, AliasManager.makeLegalName(name, _dialect), participantSequenceKeyColumn);
        ret.setFk(new PVForeignKey(dsd));
        ret.setLabel(dsd.getLabel());
        ret.setIsUnselectable(true);
        return ret;
    }


    private class PVForeignKey extends LookupForeignKey
    {
        private final DataSetDefinition dsd;

        public PVForeignKey(DataSetDefinition dsd)
        {
            super("ParticipantVisit");
            this.dsd = dsd;
        }
        
        public DataSetTable getLookupTableInfo()
        {
            try
            {
                DataSetTable ret = new DataSetTable(_schema, dsd);
                ret.hideParticipantLookups();
                return ret;
            }
            catch (UnauthorizedException e)
            {
                return null;
            }
        }

        public StringExpression getURL(ColumnInfo parent)
        {
            return null;
        }
    }
}

