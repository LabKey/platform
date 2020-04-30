package org.labkey.experiment.api.data;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.ColumnRenderProperties;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.query.FieldKey;
import org.labkey.data.xml.queryCustomView.OperatorType;

public class ImmediateChildOfCompareType extends CompareType
{
    public ImmediateChildOfCompareType()
    {
        super("Is Immediate Child Of", "exp:directchildof", "EXP_DIRECT_CHILD_OF", true, " is immediate child of", OperatorType.EXP_DIRECTCHILDOF);
    }

    @Override
    protected SimpleFilter.FilterClause createFilterClause(@NotNull FieldKey fieldKey, Object value)
    {
        return new ChildOfClause(fieldKey, value, 1);
    }

    @Override
    public boolean meetsCriteria(ColumnRenderProperties col, Object value, Object[] paramVals)
    {
        throw new UnsupportedOperationException("Conditional formatting not yet supported for EXP_DIRECT_CHILD_OF");
    }
}