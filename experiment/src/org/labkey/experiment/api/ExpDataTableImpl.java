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

import org.labkey.api.exp.api.*;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.RowIdForeignKey;
import org.labkey.api.query.DetailsURL;

import java.sql.Types;
import java.util.Collections;

import org.labkey.api.exp.api.SamplesSchema;
import org.labkey.api.view.ActionURL;

public class ExpDataTableImpl extends ExpTableImpl<ExpDataTable.Column> implements ExpDataTable
{
    protected ExpExperiment _experiment;
    protected boolean _runSpecified;
    protected ExpRun _run;
    protected DataType _type;
    public ExpDataTableImpl(String alias)
    {
        super(alias, ExperimentServiceImpl.get().getTinfoData());
    }

    public void populate(ExpSchema schema)
    {
        if (schema.isRestrictContainer())
        {
            setContainer(schema.getContainer());
        }
        addColumn(Column.RowId).setIsHidden(true);
        addColumn(Column.Name);
        addColumn(Column.Run).setFk(schema.getRunIdForeignKey());
        addColumn(Column.LSID).setIsHidden(true);
        addColumn(Column.DataFileUrl);
        addColumn(Column.Protocol).setIsHidden(true);
        setTitleColumn("Name");
        ActionURL detailsURL = new ActionURL("Experiment", "showData", schema.getContainer().getPath());
        setDetailsURL(new DetailsURL(detailsURL, Collections.singletonMap("rowId", "RowId")));
        addDetailsURL(new DetailsURL(detailsURL, Collections.singletonMap("LSID", "LSID")));
    }

    public ColumnInfo createColumn(String alias, Column column)
    {
        switch (column)
        {
            case Container:
                return wrapColumn(alias, _rootTable.getColumn("Container"));
            case Created:
                return wrapColumn(alias, _rootTable.getColumn("Created"));
            case DataFileUrl:
                return wrapColumn(alias, _rootTable.getColumn("DataFileUrl"));
            case LSID:
                return wrapColumn(alias, _rootTable.getColumn("LSID"));
            case Name:
                return wrapColumn(alias, _rootTable.getColumn("Name"));
            case Protocol:
                return wrapColumn(alias, _rootTable.getColumn("SourceProtocolLSID"));
            case RowId:
            {
                ColumnInfo ret = wrapColumn(alias, _rootTable.getColumn("RowId"));
                ret.setFk(new RowIdForeignKey(ret));
                ret.setIsHidden(true);
                return ret;
            }
            case Run:
                return wrapColumn(alias, _rootTable.getColumn("RunId"));
            case Flag:
                return createFlagColumn(alias);
            default:
                throw new IllegalArgumentException("Unknown column " + column);
        }
    }

    public void setExperiment(ExpExperiment experiment)
    {
        if (getExperiment() != null)
            throw new IllegalArgumentException("Attempt to unset experiment");
        if (experiment == null)
            return;
        SQLFragment condition = new SQLFragment("exp.Data.RunId IN "
                + " ( SELECT ExperimentRunId FROM exp.RunList "
                + " WHERE ExperimentId = ? )");
        condition.add(experiment.getRowId());
        addCondition(condition);
        _experiment = experiment;
    }

    public ExpExperiment getExperiment()
    {
        return _experiment;
    }

    public void setRun(ExpRun run)
    {
        if (_runSpecified)
            throw new IllegalArgumentException("Cannot unset run");
        _runSpecified = true;
        _run = run;
        if (run == null)
        {
            addCondition(new SQLFragment("(RunId IS NULL)"), "RunId");
        }
        else
        {
            addCondition(_rootTable.getColumn("RunId"), run.getRowId());
        }
    }

    public ExpRun getRun()
    {
        return _run;
    }

    public ColumnInfo addDataInputColumn(String alias, PropertyDescriptor pd)
    {
        SQLFragment sql = new SQLFragment("(SELECT MIN(exp.datainput.dataid)" +
                "\nFROM exp.datainput" +
                "\nWHERE " + ExprColumn.STR_TABLE_ALIAS +  ".SourceApplicationId = exp.datainput.TargetApplicationId" +
                "\nAND ");
        if (pd == null)
        {
            sql.append("1 = 0");
        }
        else
        {
            sql.append("exp.datainput.propertyid = " + pd.getPropertyId());
        }
        sql.append(")");
        ExprColumn ret = new ExprColumn(this, alias, sql, Types.INTEGER);
        return doAdd(ret);
    }

    public ColumnInfo addMaterialInputColumn(String alias, SamplesSchema schema, PropertyDescriptor pdRole, final ExpSampleSet ss)
    {
        SQLFragment sql = new SQLFragment("(SELECT MIN(InputMaterial.RowId)" +
            "\nFROM exp.materialInput" +
            "\nINNER JOIN exp.material AS InputMaterial ON exp.materialInput.materialId = InputMaterial.RowId" +
            "\nWHERE " + ExprColumn.STR_TABLE_ALIAS + ".SourceApplicationId = exp.materialInput.TargetApplicationId");
        if (ss != null)
        {
            sql.append("\nAND InputMaterial.CPASType = ?");
            sql.add(ss.getLSID());
        }
        sql.append(")");
        ExprColumn ret = new ExprColumn(this, alias, sql, Types.INTEGER);
        ret.setFk(schema.materialIdForeignKey(ss));
        return doAdd(ret);
    }

    public DataType getDataType()
    {
        return _type;
    }

    public void setDataType(DataType type)
    {
        _type = type;
    }

    public String urlFlag(boolean flagged)
    {
        String ret = null;
        DataType type = getDataType();
        if (type != null)
            ret = type.urlFlag(flagged);
        if (ret != null)
            return ret;
        return super.urlFlag(flagged);
    }


    public ColumnInfo addInputRunCountColumn(String alias)
    {
        SQLFragment sql = new SQLFragment("(SELECT COUNT(DISTINCT exp.ProtocolApplication.RunId) \n" +
                "FROM exp.ProtocolApplication INNER JOIN Exp.DataInput ON exp.ProtocolApplication.RowId = Exp.DataInput.TargetApplicationId\n" +
                "WHERE Exp.DataInput.DataId = " + ExprColumn.STR_TABLE_ALIAS + ".RowId)");
        ColumnInfo ret = new ExprColumn(this, alias, sql, Types.INTEGER);
        return doAdd(ret);
    }
}
