package org.labkey.experiment.api.flag;

import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.PropertyForeignKey;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.exp.property.ExperimentProperty;
import org.labkey.api.exp.PropertyDescriptor;

import java.sql.Types;

public class FlagColumn extends ExprColumn
{
    String _urlFlagged;
    String _urlUnflagged;
    public FlagColumn(ColumnInfo parent, String urlFlagged, String urlUnflagged)
    {
        super(parent.getParentTable(), parent.getName() + "$", null, Types.VARCHAR, parent);
        setAlias(parent.getAlias() + "$");
        PropertyDescriptor pd = ExperimentProperty.COMMENT.getPropertyDescriptor();
        SQLFragment sql = PropertyForeignKey.getValueSql(parent,
                PropertyForeignKey.getValueSql(pd.getPropertyType()),
                pd.getPropertyId(), true);
        setValueSQL(sql);
        _urlFlagged = urlFlagged;
        _urlUnflagged = urlUnflagged;
    }
    public String urlFlag(boolean flagged)
    {
        return flagged ? _urlFlagged : _urlUnflagged;
    }
}
