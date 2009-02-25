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
import org.labkey.api.util.StringExpressionFactory;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.study.model.DataSetDefinition;
import org.labkey.study.StudySchema;

public class ParticipantVisitTable extends FilteredTable
{
    StudyQuerySchema _schema;
    SqlDialect _dialect;

    // NOTE: _pvForeign is so we can 'skip' this table when joining from one dataset to another
    ColumnInfo _pvForeign;

    public ParticipantVisitTable(StudyQuerySchema schema, ColumnInfo pv)
    {
        super(StudySchema.getInstance().getTableInfoParticipantVisit(), schema.getContainer());
        _schema = schema;
        _dialect = _schema.getDbSchema().getSqlDialect();
        _pvForeign = pv;

        ColumnInfo keyPV = new ParticipantVisitColumn("ParticipantVisit", this, _rootTable.getColumn("ParticipantId"), _rootTable.getColumn("SequenceNum"));
        keyPV.setIsHidden(true);
        keyPV.setIsUnselectable(true);
        addColumn(keyPV);
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
            else  if (_pvForeign == null)
                addWrapColumn(col);
            else
            {
                ColumnInfo lookupCol = new LookupColumn(pv, keyPV, col);
                lookupCol.setName(col.getName());
                addColumn(lookupCol);
            }
        }

        if (null == _pvForeign)
        {
            _pvForeign = new ParticipantVisitColumn(
                    "ParticipantVisit",
                    new AliasedColumn("ParticipantId",getColumn("ParticipantId")),
                    new AliasedColumn("SequenceNum",getColumn("SequenceNum")));
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

            ColumnInfo datasetColumn = createDataSetColumn(name, dataset);
            addColumn(datasetColumn);
        }
    }


    protected ColumnInfo createDataSetColumn(String name, final DataSetDefinition dsd)
    {
        ColumnInfo ret = new AliasedColumn(name, _pvForeign);
        ret.setAlias(AliasManager.makeLegalName(name, _dialect));
        ret.setFk(new PVForeignKey(dsd));
        ret.setCaption(dsd.getLabel());
        ret.setIsUnselectable(true);
        return ret;
    }


    private class PVForeignKey extends AbstractForeignKey
    {
        private final DataSetDefinition dsd;

        public PVForeignKey(DataSetDefinition dsd)
        {
            this.dsd = dsd;
        }

        public ColumnInfo createLookupColumn(ColumnInfo foreignKey, String displayField)
        {
            DataSetTable table = getLookupTableInfo();
            if (table == null)
                return null;
            if (displayField == null)
                return null;

            ParticipantVisitColumn lookupKey = new ParticipantVisitColumn("ParticipantVisit", table.getColumn("ParticipantId"), table.getColumn("SequenceNum"));
            ColumnInfo lookupColumn = table.getColumn(displayField);
            if (lookupColumn == null)
                return null;
            LookupColumn look = new PVLookupColumn(foreignKey, lookupKey, lookupColumn);
            return look;
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

        public StringExpressionFactory.StringExpression getURL(ColumnInfo parent)
        {
            return null;
        }
    }

    
    private class PVLookupColumn extends LookupColumn
    {
        public PVLookupColumn(ColumnInfo foreignKey, ParticipantVisitColumn lookupKey, ColumnInfo lookupColumn)
        {
            super(foreignKey, lookupKey, lookupColumn);
            copyAttributesFrom(lookupColumn);
            setCaption(foreignKey.getCaption() + " " + lookupColumn.getCaption());
        }

        public SQLFragment getJoinCondition()
        {
            ColumnInfo fk = foreignKey;
            if (fk instanceof AliasedColumn)
                fk = ((AliasedColumn)fk).getColumn();
            ColumnInfo lk = lookupKey;
            if (lk instanceof AliasedColumn)
                lk = ((AliasedColumn)lk).getColumn();

            if (!(fk instanceof ParticipantVisitColumn) || !(lk instanceof ParticipantVisitColumn))
                return super.getJoinCondition();

            ParticipantVisitColumn pvForeign = (ParticipantVisitColumn) fk;
            ParticipantVisitColumn pvLookup = (ParticipantVisitColumn) lk;

            SQLFragment condition = new SQLFragment();
            condition.append("(");
            condition.append(pvForeign._participantColumn.getValueSql());
            condition.append(" = ");
            condition.append(pvLookup._participantColumn.getValueSql(getTableAlias()));
            condition.append(" AND " );
            condition.append(pvForeign._visitColumn.getValueSql());
            condition.append(" = ");
            condition.append(pvLookup._visitColumn.getValueSql(getTableAlias()));
            condition.append(")");
            return condition;
        }
    }
}

