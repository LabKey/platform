package org.labkey.experiment.api;

import org.jetbrains.annotations.Nullable;

/**
 * User: kevink
 * Date: 9/21/15
 */
public class DataClass extends IdentifiableEntity
{
    private String _description;
    private String _nameExpression;
    private Integer _materialSourceId;

    public String getDescription()
    {
        return _description;
    }

    public void setDescription(String description)
    {
        _description = description;
    }

    public String getNameExpression()
    {
        return _nameExpression;
    }

    public void setNameExpression(String nameExpression)
    {
        _nameExpression = nameExpression;
    }

    @Nullable
    public Integer getMaterialSourceId()
    {
        return _materialSourceId;
    }

    public void setMaterialSourceId(@Nullable Integer materialSourceId)
    {
        _materialSourceId = materialSourceId;
    }

}
