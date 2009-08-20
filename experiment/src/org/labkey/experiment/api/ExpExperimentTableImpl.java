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

package org.labkey.experiment.api;

import org.labkey.api.exp.api.*;
import org.labkey.api.exp.query.ExpExperimentTable;
import org.labkey.api.data.*;
import org.labkey.api.query.*;
import org.labkey.api.view.ActionURL;
import org.labkey.experiment.controllers.exp.ExperimentMembershipDisplayColumnFactory;
import org.labkey.experiment.controllers.exp.ExperimentController;

import java.sql.Types;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

public class ExpExperimentTableImpl extends ExpTableImpl<ExpExperimentTable.Column> implements ExpExperimentTable
{
    public ExpExperimentTableImpl(String name, UserSchema schema)
    {
        super(name, ExperimentServiceImpl.get().getTinfoExperiment(), schema);
        addCondition(new SQLFragment("Hidden = ?", Boolean.FALSE), "Hidden");
    }

    public ColumnInfo createColumn(String alias, Column column)
    {
        switch (column)
        {
            case Comments:
                return wrapColumn(alias, _rootTable.getColumn("Comments"));
            case Folder:
                return wrapColumn(alias, _rootTable.getColumn("Container"));
            case Created:
                return wrapColumn(alias, _rootTable.getColumn("Created"));
            case CreatedBy:
                return createUserColumn(alias, _rootTable.getColumn("CreatedBy"));
            case Hypothesis:
                return wrapColumn(alias, _rootTable.getColumn("Hypothesis"));
            case LSID:
                return wrapColumn(alias, _rootTable.getColumn("LSID"));
            case Modified:
                return wrapColumn(alias, _rootTable.getColumn("Modified"));
            case ModifiedBy:
                return createUserColumn(alias, _rootTable.getColumn("ModifiedBy"));
            case Name:
                return wrapColumn(alias, _rootTable.getColumn("Name"));
            case RowId:
                return wrapColumn(alias, _rootTable.getColumn("RowId"));
            case RunCount:
                return new ExprColumn(this, "RunCount", new SQLFragment("(SELECT COUNT(*) FROM " + ExperimentServiceImpl.get().getTinfoRunList() + " WHERE ExperimentId = " + ExprColumn.STR_TABLE_ALIAS + ".RowId)"), Types.INTEGER);
            case BatchProtocolId:
            {
                ColumnInfo batchProtocolCol = wrapColumn(alias, _rootTable.getColumn("BatchProtocolId"));
                batchProtocolCol.setLabel("Batch Protocol");
                batchProtocolCol.setFk(getExpSchema().getProtocolForeignKey("RowId"));
                return batchProtocolCol;
            }
            default:
                throw new UnsupportedOperationException("Unknown column " + column);
        }
    }

    public void addExperimentMembershipColumn(ExpRun run)
    {
        SQLFragment sql;
        if (run != null)
        {
            sql = new SQLFragment("(SELECT CAST((CASE WHEN (SELECT MAX(ExperimentId) FROM ");
            sql.append(ExperimentServiceImpl.get().getTinfoRunList());
            sql.append(" WHERE ExperimentRunId = " + run.getRowId() + " AND ExperimentId = " + ExprColumn.STR_TABLE_ALIAS + ".RowId)");
            sql.append(" IS NOT NULL THEN 1 ELSE NULL END) AS ");
            sql.append(getSqlDialect().getBooleanDatatype());
            sql.append("))");
        }
        else
        {
            sql = new SQLFragment("DUMMY SQL - SHOULD NOT BE EXECUTED");
        }

        ColumnInfo expRowIdCol = getColumn(Column.RowId);
        ExprColumn result = new ExprColumn(this, "RunMembership", sql, Types.BOOLEAN, expRowIdCol);
        result.setLabel("");
        result.setDisplayColumnFactory(new ExperimentMembershipDisplayColumnFactory(expRowIdCol, run == null ? -1 : run.getRowId()));
        addColumn(result);
    }

    public void setBatchProtocol(ExpProtocol protocol)
    {
        SimpleFilter filter = new SimpleFilter();
        if (protocol != null)
        {
            filter.addClause(new CompareType.CompareClause(Column.BatchProtocolId.name(), CompareType.EQUAL, protocol.getRowId()));
        }
        else
        {
            filter.addClause(new CompareType.CompareClause(Column.BatchProtocolId.name(), CompareType.ISBLANK, null));
        }
        addCondition(filter);
    }

    public void populate()
    {
        ColumnInfo colRowId = addColumn(Column.RowId);
        colRowId.setHidden(true);
        colRowId.setKeyField(true);
        colRowId.setFk(new RowIdForeignKey(colRowId));
        addColumn(Column.Name);
        addColumn(Column.Hypothesis);
        addColumn(Column.Comments);
        addColumn(Column.Created);
        addColumn(Column.CreatedBy);
        addColumn(Column.Modified);
        addColumn(Column.ModifiedBy);
        addColumn(Column.RunCount);
        addColumn(Column.BatchProtocolId);
        addContainerColumn(Column.Folder, new ActionURL(ExperimentController.ShowRunGroupsAction.class, getContainer()));
        List<FieldKey> defaultCols = new ArrayList<FieldKey>();
        defaultCols.add(FieldKey.fromParts(Column.Name.toString()));
        defaultCols.add(FieldKey.fromParts(Column.Hypothesis.toString()));
        defaultCols.add(FieldKey.fromParts(Column.Comments.toString()));
        defaultCols.add(FieldKey.fromParts(Column.RunCount.toString()));
        defaultCols.add(FieldKey.fromParts(Column.Folder.toString()));
        setDefaultVisibleColumns(defaultCols);
        addColumn(Column.LSID).setHidden(true);
        setTitleColumn("Name");

        ActionURL detailsURL = new ActionURL(ExperimentController.DetailsAction.class, _schema.getContainer());
        setDetailsURL(new DetailsURL(detailsURL, Collections.singletonMap("rowId", "RowId")));
    }

    public ColumnInfo createRunCountColumn(String alias, ExpProtocol parentProtocol, ExpProtocol childProtocol)
    {
        SQLFragment sql = new SQLFragment("(SELECT COUNT(exp.experimentrun.rowid) FROM exp.experimentrun" +
                "\nINNER JOIN exp.runlist ON exp.experimentrun.rowid = exp.runlist.experimentrunid" +
                "\nAND exp.runlist.experimentid = " + ExprColumn.STR_TABLE_ALIAS + ".rowid");
        if (parentProtocol != null)
        {
            sql.append("\nAND exp.experimentrun.protocollsid = ");
            sql.appendStringLiteral(parentProtocol.getLSID());
        }
        if (childProtocol != null)
        {
            sql.append("\nAND exp.experimentrun.rowid IN" +
                    "\n(SELECT exp.protocolapplication.runid" +
                    "\nFROM exp.protocolapplication WHERE exp.protocolapplication.protocollsid = ");
            sql.appendStringLiteral(childProtocol.getLSID());
            sql.append(")");
        }
        sql.append(")");
        return new ExprColumn(this, alias, sql, Types.INTEGER);
    }
}
