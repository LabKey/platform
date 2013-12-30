package org.labkey.study.model;

import org.labkey.api.study.TreatmentProduct;

/**
 * User: cnathe
 * Date: 12/27/13
 */
public class TreatmentProductImpl implements TreatmentProduct
{
    private int _rowId;
    private int _treatmentId;
    private int _productId;
    private String _dose;
    private String _route;

    public TreatmentProductImpl()
    {
    }

    public boolean isNew()
    {
        return _rowId == 0;
    }

    public Object getPrimaryKey()
    {
        return getRowId();
    }

    public int getRowId()
    {
        return _rowId;
    }

    public void setRowId(int rowId)
    {
        _rowId = rowId;
    }

    public int getTreatmentId()
    {
        return _treatmentId;
    }

    public void setTreatmentId(int treatmentId)
    {
        _treatmentId = treatmentId;
    }

    public int getProductId()
    {
        return _productId;
    }

    public void setProductId(int productId)
    {
        _productId = productId;
    }

    public String getDose()
    {
        return _dose;
    }

    public void setDose(String dose)
    {
        _dose = dose;
    }

    public String getRoute()
    {
        return _route;
    }

    public void setRoute(String route)
    {
        _route = route;
    }
}
