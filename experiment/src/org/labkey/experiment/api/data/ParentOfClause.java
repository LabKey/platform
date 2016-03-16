package org.labkey.experiment.api.data;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.exp.api.ExpLineageOptions;
import org.labkey.api.query.FieldKey;

/**
 * User: kevink
 * Date: 3/16/16
 */
public class ParentOfClause extends LineageClause
{
    public ParentOfClause(@NotNull FieldKey fieldKey, Object value)
    {
        super(fieldKey, value);
    }

    protected ExpLineageOptions createOptions()
    {
        ExpLineageOptions options = new ExpLineageOptions();
        options.setParents(true);
        options.setChildren(false);
        return options;
    }

    protected String getLsidColumn()
    {
        return "parent_lsid";
    }

    protected String filterTextType()
    {
        return "parent of";
    }

}
