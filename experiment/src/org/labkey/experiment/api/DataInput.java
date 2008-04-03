package org.labkey.experiment.api;

public class DataInput
{
    private int _dataId;
    private int _targetApplicationId;
    private Integer _propertyId;

    public int getDataId()
    {
        return _dataId;
    }

    public void setDataId(int id)
    {
        _dataId = id;
    }

    public int getTargetApplicationId()
    {
        return _targetApplicationId;
    }

    public void setTargetApplicationId(int id)
    {
        _targetApplicationId = id;
    }

    public Integer getPropertyId()
    {
        return _propertyId;
    }

    public void setPropertyId(Integer pd)
    {
        _propertyId = pd;
    }
}
