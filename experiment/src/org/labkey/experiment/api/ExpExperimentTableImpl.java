/*
 * Copyright (c) 2006-2008 LabKey Corporation
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

import org.labkey.api.exp.api.ExpExperimentTable;
import org.labkey.api.exp.api.ExpSchema;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.query.RowIdForeignKey;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.view.ActionURL;
import org.labkey.experiment.controllers.exp.ExperimentMembershipDisplayColumnFactory;

import java.sql.Types;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

public class ExpExperimentTableImpl extends ExpTableImpl<ExpExperimentTable.Column> implements ExpExperimentTable
{
    public ExpExperimentTableImpl(String alias)
    {
        super(alias, ExperimentServiceImpl.get().getTinfoExperiment());
    }

    public ColumnInfo createColumn(String alias, Column column)
    {
        switch (column)
        {
            case Comments:
                return wrapColumn(alias, _rootTable.getColumn("Comments"));
            case Container:
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
        result.setCaption("");
        result.setDisplayColumnFactory(new ExperimentMembershipDisplayColumnFactory(expRowIdCol, run == null ? -1 : run.getRowId()));
        addColumn(result);
    }

    public void populate(ExpSchema schema)
    {
        ColumnInfo colRowId = addColumn(Column.RowId);
        colRowId.setIsHidden(true);
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
        List<FieldKey> defaultCols = new ArrayList<FieldKey>();
        defaultCols.add(FieldKey.fromParts(Column.Name.toString()));
        defaultCols.add(FieldKey.fromParts(Column.Hypothesis.toString()));
        defaultCols.add(FieldKey.fromParts(Column.Comments.toString()));
        defaultCols.add(FieldKey.fromParts(Column.RunCount.toString()));
        setDefaultVisibleColumns(defaultCols);
        addColumn(Column.LSID).setIsHidden(true);
        setTitleColumn("Name");

        ActionURL detailsURL = new ActionURL("Experiment", "details", schema.getContainer().getPath());
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
