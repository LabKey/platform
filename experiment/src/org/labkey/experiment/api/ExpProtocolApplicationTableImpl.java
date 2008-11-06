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
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.query.QuerySchema;

import java.sql.Types;

import org.labkey.api.exp.api.SamplesSchema;

public class ExpProtocolApplicationTableImpl extends ExpTableImpl<ExpProtocolApplicationTable.Column> implements ExpProtocolApplicationTable
{
    public ExpProtocolApplicationTableImpl(String alias, QuerySchema schema)
    {
        super(alias, ExperimentServiceImpl.get().getTinfoProtocolApplication(), schema);
    }

    public ColumnInfo createColumn(String alias, ExpProtocolApplicationTable.Column column)
    {
        switch (column)
        {
            case RowId:
                return wrapColumn(alias, _rootTable.getColumn("RowId"));
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
        ColumnInfo ret = new ExprColumn(this, alias, sql, Types.INTEGER);

        ret.setFk(schema.materialIdForeignKey(sampleSet));
        return ret;
    }

    public ColumnInfo createDataInputColumn(String alias, final ExpSchema schema, String... roleNames)
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
        ColumnInfo ret = new ExprColumn(this, alias, sql, Types.INTEGER);

        ret.setFk(new LookupForeignKey("RowId")
        {
            public TableInfo getLookupTableInfo()
            {
                ExpDataTable expDataTable = schema.createDatasTable("lookup");
                expDataTable.setContainerFilter(getContainerFilter());
                return expDataTable;
            }
        });
        return ret;
    }
}
