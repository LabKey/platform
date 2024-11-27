package org.labkey.api.gwt.client.model;

import com.google.gwt.user.client.rpc.IsSerializable;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

@EqualsAndHashCode
public class GWTFilterCriteria implements Serializable, IsSerializable
{
    private String _name;
    private String _op;
    private Integer _propertyId;
    private Object _value;

    public GWTFilterCriteria()
    {
    }

    public GWTFilterCriteria(GWTFilterCriteria fc)
    {
        setName(fc.getName());
        setOp(fc.getOp());
        setPropertyId(fc.getPropertyId());
        setValue(fc.getValue());
    }

    public String getName()
    {
        return _name;
    }

    public void setName(String name)
    {
        _name = name;
    }

    public String getOp()
    {
        return _op;
    }

    public void setOp(String op)
    {
        _op = op;
    }

    public Integer getPropertyId()
    {
        return _propertyId;
    }

    public void setPropertyId(Integer propertyId)
    {
        _propertyId = propertyId;
    }

    public Object getValue()
    {
        return _value;
    }

    public void setValue(Object value)
    {
        _value = value;
    }
}

