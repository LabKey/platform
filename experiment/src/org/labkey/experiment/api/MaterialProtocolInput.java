package org.labkey.experiment.api;

public class MaterialProtocolInput extends AbstractProtocolInput
{
    protected Integer _materialSourceId;

    public Integer getMaterialSourceId()
    {
        return _materialSourceId;
    }

    public void setMaterialSourceId(Integer materialSourceId)
    {
        _materialSourceId = materialSourceId;
    }

    @Override
    public String getObjectType()
    {
        return ExpMaterialImpl.DEFAULT_CPAS_TYPE;
    }
}
