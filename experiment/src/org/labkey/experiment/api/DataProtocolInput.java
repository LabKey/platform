package org.labkey.experiment.api;

public class DataProtocolInput extends AbstractProtocolInput
{
    protected Integer _dataClassId;

    public Integer getDataClassId()
    {
        return _dataClassId;
    }

    public void setDataClassId(Integer dataClassId)
    {
        _dataClassId = dataClassId;
    }

    @Override
    public String getObjectType()
    {
        return ExpDataImpl.DEFAULT_CPAS_TYPE;
    }
}
