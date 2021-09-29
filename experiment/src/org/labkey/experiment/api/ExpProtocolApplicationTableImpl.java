/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

import org.labkey.api.data.BaseColumnInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.MutableColumnInfo;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.api.ExpSampleType;
import org.labkey.api.exp.query.ExpDataTable;
import org.labkey.api.exp.query.ExpProtocolApplicationTable;
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.exp.query.SamplesSchema;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.UserSchema;

import java.util.List;

public class ExpProtocolApplicationTableImpl extends ExpTableImpl<ExpProtocolApplicationTable.Column> implements ExpProtocolApplicationTable
{
    public ExpProtocolApplicationTableImpl(String name, UserSchema schema, ContainerFilter cf)
    {
        super(name, ExperimentServiceImpl.get().getTinfoProtocolApplication(), schema, cf);

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

        addCondition(getContainerFilter().getSQLFragment(getSchema(), sqlFragment, false), containerFK);
    }

    @Override
    public MutableColumnInfo createColumn(String alias, ExpProtocolApplicationTable.Column column)
    {
        switch (column)
        {
            case RowId:
                var rowIdColumnInfo = wrapColumn(alias, _rootTable.getColumn("RowId"));
                rowIdColumnInfo.setHidden(true);
                return rowIdColumnInfo;
            case Name:
                return wrapColumn(alias, _rootTable.getColumn("Name"));
            case Comments:
                return wrapColumn(alias, _rootTable.getColumn("Comments"));
            case LSID:
                return wrapColumn(alias, _rootTable.getColumn("LSID"));
            case Run:
                var runColumnInfo = wrapColumn(alias, _rootTable.getColumn("RunId"));
                runColumnInfo.setFk(getExpSchema().getRunIdForeignKey(getContainerFilter()));
                return runColumnInfo;
            case ActionSequence:
                return wrapColumn(alias, _rootTable.getColumn("ActionSequence"));
            case Type:
                return wrapColumn(alias, _rootTable.getColumn("CpasType"));
            case Protocol:
                var columnInfo = wrapColumn(alias, _rootTable.getColumn("ProtocolLSID"));
                columnInfo.setFk(getExpSchema().getProtocolForeignKey(getContainerFilter(), "LSID"));
                return columnInfo;
            case ActivityDate:
                return wrapColumn(alias, _rootTable.getColumn("ActivityDate"));
            case StartTime:
                return wrapColumn(alias, _rootTable.getColumn("StartTime"));
            case EndTime:
                return wrapColumn(alias, _rootTable.getColumn("EndTime"));
            case RecordCount:
                return wrapColumn(alias, _rootTable.getColumn("RecordCount"));
            case Properties:
                return (BaseColumnInfo) createPropertiesColumn(alias);
            case EntityId:
                return wrapColumn(alias, _rootTable.getColumn("EntityId"));
        }
        throw new IllegalArgumentException("Unknown column " + column);
    }

    @Override
    public BaseColumnInfo createMaterialInputColumn(String alias, SamplesSchema schema, ExpSampleType sampleType, String... roleNames)
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
        var ret = new ExprColumn(this, alias, sql, JdbcType.INTEGER);

        ret.setFk(schema.materialIdForeignKey(sampleType, null));
        return ret;
    }

    @Override
    public BaseColumnInfo createDataInputColumn(String name, final ExpSchema schema, String... roleNames)
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
        var ret = new ExprColumn(this, name, sql, JdbcType.INTEGER);

        // TODO add ContainerFilter to ExperimentLookupForeignKey() constructor
        ret.setFk(new ExpSchema.ExperimentLookupForeignKey("RowId")
        {
            @Override
            public TableInfo getLookupTableInfo()
            {
                ExpDataTable expDataTable;
                expDataTable = (ExpDataTable)schema.getTable(ExpSchema.TableType.Data.name(), getLookupContainerFilter(), true, false);
                return expDataTable;
            }
        });
        return ret;
    }

    @Override
    protected void populateColumns()
    {
        addColumn(Column.RowId);
        addColumn(Column.Name);
        setTitleColumn(Column.Name.toString());
        addColumn(Column.Comments);
        addColumn(Column.Run);
        addColumn(Column.LSID).setHidden(true);
        addColumn(Column.Protocol);
        addColumn(Column.Type);
        addColumn(Column.ActionSequence).setHidden(true);
        addColumn(Column.ActivityDate);
        addColumn(Column.StartTime);
        addColumn(Column.EndTime);
        addColumn(Column.RecordCount);
        addColumn(Column.EntityId);

        addColumn(Column.Properties).setHidden(true);
        addVocabularyDomains();

        setDefaultVisibleColumns(List.of(
                FieldKey.fromParts(Column.Name),
                FieldKey.fromParts(Column.Run),
                FieldKey.fromParts(Column.Protocol),
                FieldKey.fromParts(Column.Type)
        ));

        _populated = true;
    }

    @Override
    protected ColumnInfo resolveColumn(String name)
    {
        ColumnInfo result = super.resolveColumn(name);
        // Issue 39301: if there are no material inputs and you try to filter on a paticular material input
        // the filter is ignored and you get an unfiltered view unless we can provide a Material column.
        if ("Material".equalsIgnoreCase(name) && result == null)
        {
            ExpSchema expSchema = new ExpSchema(_userSchema.getUser(), _userSchema.getContainer());
            return createMaterialInputColumn(name, expSchema.getSamplesSchema(), null, "Material");
        }
        return result;
     }
}
