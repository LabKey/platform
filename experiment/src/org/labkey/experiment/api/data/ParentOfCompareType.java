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
public class ParentOfCompareType extends CompareType
{
    public ParentOfCompareType()
    {
        super("Is Parent Of", "exp:parentof", "EXP_PARENT_OF", true, " is parent of", OperatorType.EXP_PARENTOF);
    }

    @Override
    protected SimpleFilter.FilterClause createFilterClause(@NotNull FieldKey fieldKey, Object value)
    {
        return new ParentOfClause(fieldKey, value);
    }

    @Override
    public boolean meetsCriteria(Object value, Object[] paramVals)
    {
        throw new UnsupportedOperationException("Conditional formatting not yet supported for EXP_PARENT_OF");
    }
}
