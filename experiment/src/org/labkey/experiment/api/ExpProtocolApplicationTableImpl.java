/*
 * Copyright (c) 2006-2016 LabKey Corporation
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

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.api.ExpSampleSet;
import org.labkey.api.exp.query.ExpDataTable;
import org.labkey.api.exp.query.ExpProtocolApplicationTable;
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.exp.query.SamplesSchema;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.UserSchema;

public class ExpProtocolApplicationTableImpl extends ExpTableImpl<ExpProtocolApplicationTable.Column> implements ExpProtocolApplicationTable
{
    public ExpProtocolApplicationTableImpl(String name, UserSchema schema)
    {
        super(name, ExperimentServiceImpl.get().getTinfoProtocolApplication(), schema, new ExpProtocolApplicationImpl(new ProtocolApplication()));

    }

    @Override
    protected void applyContainerFilter(ContainerFilter filter)
    {
        // We don't have our own Container column, so filter based on the Container column of this ProtocolApplication's run
        FieldKey containerFK = FieldKey.fromParts("Container");
        clearConditions(containerFK);
        SQLFragment sqlFragment = new SQLFragment("(SELECT er.Container FROM ");
        sqlFragment.append(ExperimentServiceImpl.get().getTinfoExperimentRun(), "er");
        sqlFragment.append(" WHERE er.RowId = RunId)    ");

        addCondition(getContainerFilter().getSQLFragment(getSchema(), sqlFragment, getContainer(), false), containerFK);
    }

    public ColumnInfo createColumn(String alias, ExpProtocolApplicationTable.Column column)
    {
        switch (column)
        {
            case RowId:
                ColumnInfo rowIdColumnInfo = wrapColumn(alias, _rootTable.getColumn("RowId"));
                rowIdColumnInfo.setHidden(true);
                return rowIdColumnInfo;
            case Name:
                return wrapColumn(alias, _rootTable.getColumn("Name"));
            case LSID:
                return wrapColumn(alias, _rootTable.getColumn("LSID"));
            case Run:
                ColumnInfo runColumnInfo = wrapColumn(alias, _rootTable.getColumn("RunId"));
                runColumnInfo.setFk(getExpSchema().getRunIdForeignKey());
                return runColumnInfo;
            case ActionSequence:
                return wrapColumn(alias, _rootTable.getColumn("ActionSequence"));
            case Type:
                return wrapColumn(alias, _rootTable.getColumn("CpasType"));
            case Protocol:
                ColumnInfo columnInfo = wrapColumn(alias, _rootTable.getColumn("ProtocolLSID"));
                columnInfo.setFk(getExpSchema().getProtocolForeignKey("LSID"));
                return columnInfo;
        }
        throw new IllegalArgumentException("Unknown column " + column);
    }

    public ColumnInfo createMaterialInputColumn(String alias, SamplesSchema schema, ExpSampleSet sampleSet, String... roleNames)
    {
        SQLFragment sql = new SQLFragment("(SELECT MIN(exp.MaterialInput.MaterialId) FROM exp.MaterialInput\nWHERE ");

        sql.append(ExprColumn.STR_TABLE_ALIAS + ".RowId = exp.MaterialInput.TargetApplicationId");
        if (roleNames.length != 0)
        {
            sql.append("\nAND (");
            String strOr = "";
            for (String roleName : roleNames)
            {
                sql.append(strOr);
                strOr = " OR ";
                sql.append("exp.MaterialInput.Role = ?");
                sql.add(roleName);
            }
            sql.append(")");
        }
        sql.append(")");
        ColumnInfo ret = new ExprColumn(this, alias, sql, JdbcType.INTEGER);

        ret.setFk(schema.materialIdForeignKey(sampleSet, null));
        return ret;
    }

    public ColumnInfo createDataInputColumn(String name, final ExpSchema schema, String... roleNames)
    {
        SQLFragment sql = new SQLFragment("(SELECT MIN(exp.DataInput.DataId) FROM exp.DataInput\nWHERE ");
        sql.append(ExprColumn.STR_TABLE_ALIAS +".RowId = exp.DataInput.TargetApplicationId");
        if (roleNames.length != 0)
        {
            sql.append("\nAND (");
            String strOr = "";
            for (String roleName : roleNames)
            {
                sql.append(strOr);
                strOr = " OR ";
                sql.append("\nexp.DataInput.Role = ?");
                sql.add(roleName);
            }
            sql.append(")");
        }
        sql.append(")");
        ColumnInfo ret = new ExprColumn(this, name, sql, JdbcType.INTEGER);

        ret.setFk(new ExpSchema.ExperimentLookupForeignKey("RowId")
        {
            public TableInfo getLookupTableInfo()
            {
                ExpDataTable expDataTable = (ExpDataTable)schema.getTable(ExpSchema.TableType.Data.name(), true);
                expDataTable.setContainerFilter(getContainerFilter());
                return expDataTable;
            }
        });
        return ret;
    }

    public void populate()
    {
        addColumn(Column.RowId);
        addColumn(Column.Name);
        setTitleColumn(Column.Name.toString());
        addColumn(Column.Run);
        addColumn(Column.LSID).setHidden(true);
        addColumn(Column.Protocol);
        addColumn(Column.Type);
        addColumn(Column.ActionSequence).setHidden(true);
    }
}
