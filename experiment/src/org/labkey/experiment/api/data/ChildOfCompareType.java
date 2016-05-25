package org.labkey.experiment.api.data;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.query.FieldKey;
import org.labkey.data.xml.queryCustomView.OperatorType;

/**
 * User: kevink
 * Date: 5/23/16
 */
public class ChildOfCompareType extends CompareType
{
    public ChildOfCompareType()
    {
        super("Is Child Of", "exp:childof", "EXP_CHILD_OF", true, " is child of", OperatorType.EXP_CHILDOF);
    }

    @Override
    protected SimpleFilter.FilterClause createFilterClause(@NotNull FieldKey fieldKey, Object value)
    {
        return new ChildOfClause(fieldKey, value);
    }

    @Override
    public boolean meetsCriteria(Object value, Object[] paramVals)
    {
        throw new UnsupportedOperationException("Conditional formatting not yet supported for EXP_CHILD_OF");
    }
}
