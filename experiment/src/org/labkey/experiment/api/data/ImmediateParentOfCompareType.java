package org.labkey.experiment.api.data;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.ColumnRenderProperties;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.query.FieldKey;
import org.labkey.data.xml.queryCustomView.OperatorType;

public class ImmediateParentOfCompareType extends CompareType
{
    public ImmediateParentOfCompareType()
    {
        super("Is Immediate Parent Of", "exp:directparentof", "EXP_DIRECT_PARENT_OF", true, " is immediate parent of", OperatorType.EXP_DIRECTPARENTOF);
    }

    @Override
    protected SimpleFilter.FilterClause createFilterClause(@NotNull FieldKey fieldKey, Object value)
    {
        return new ParentOfClause(fieldKey, value, 0);
    }

    @Override
    public boolean meetsCriteria(ColumnRenderProperties col, Object value, Object[] paramVals)
    {
        throw new UnsupportedOperationException("Conditional formatting not yet supported for EXP_DIRECT_PARENT_OF");
    }
}
