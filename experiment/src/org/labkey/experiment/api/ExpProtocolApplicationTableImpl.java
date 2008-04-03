package org.labkey.experiment.api;

import org.labkey.api.exp.api.*;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.query.ExprColumn;

import java.sql.Types;
import java.util.Set;
import java.util.HashSet;

import org.labkey.api.exp.api.SamplesSchema;

public class ExpProtocolApplicationTableImpl extends ExpTableImpl<ExpProtocolApplicationTable.Column> implements ExpProtocolApplicationTable
{
    public ExpProtocolApplicationTableImpl(String alias)
    {
        super(alias, ExperimentServiceImpl.get().getTinfoProtocolApplication());
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

    public ColumnInfo createMaterialInputColumn(String alias, SamplesSchema schema, ExpSampleSet sampleSet, PropertyDescriptor... pds)
    {
        SQLFragment sql = new SQLFragment("(SELECT MIN(exp.MaterialInput.MaterialId) FROM exp.MaterialInput\nWHERE ");

        sql.append(ExprColumn.STR_TABLE_ALIAS + ".RowId = exp.MaterialInput.TargetApplicationId");
        if (pds.length != 0)
        {
            sql.append("\nAND (");
            String strOr = "";
            for (PropertyDescriptor pd : pds)
            {
                sql.append(strOr);
                strOr = " OR ";
                if (pd == null)
                {
                    sql.append("exp.MaterialInput.PropertyId IS NULL");
                }
                else
                {
                    sql.append("exp.MaterialInput.PropertyId = " + pd.getPropertyId());
                }
            }
            sql.append(")");
        }
        sql.append(")");
        ColumnInfo ret = new ExprColumn(this, alias, sql, Types.INTEGER);

        ret.setFk(schema.materialIdForeignKey(sampleSet));
        return ret;
    }

    public ColumnInfo createDataInputColumn(String alias, ExpSchema schema, PropertyDescriptor... pds)
    {
        SQLFragment sql = new SQLFragment("(SELECT MIN(exp.DataInput.DataId) FROM exp.DataInput\nWHERE ");
        sql.append(ExprColumn.STR_TABLE_ALIAS +".RowId = exp.DataInput.TargetApplicationId");
        if (pds.length != 0)
        {
            sql.append("\nAND (");
            String strOr = "";
            for (PropertyDescriptor pd : pds)
            {
                sql.append(strOr);
                strOr = " OR ";
                if (pd == null)
                {
                    sql.append("\nexp.DataInput.PropertyId IS NULL");
                }
                else
                {
                    sql.append("\nexp.DataInput.PropertyId = " + pd.getPropertyId());
                }
            }
            sql.append(")");
        }
        sql.append(")");
        ColumnInfo ret = new ExprColumn(this, alias, sql, Types.INTEGER);
        ret.setFk(schema.getDataIdForeignKey());
        return ret;
    }
}
