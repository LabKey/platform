package org.labkey.experiment.api;

public class MaterialInput
{
    private int _materialId;
    private int _targetApplicationId;
    private Integer _propertyId;
    
    public int getMaterialId()
    {
        return _materialId;
    }

    public void setMaterialId(int materialId)
    {
        this._materialId = materialId;
    }

    public int getTargetApplicationId()
    {
        return _targetApplicationId;
    }

    public void setTargetApplicationId(int targetApplicationId)
    {
        this._targetApplicationId = targetApplicationId;
    }

    public Integer getPropertyId()
    {
        return _propertyId;
    }

    public void setPropertyId(Integer id)
    {
        this._propertyId = id;
    }
}
