package org.labkey.api.gwt.client.util;

import com.google.gwt.user.client.rpc.IsSerializable;

/**
 * Created by IntelliJ IDEA.
 * User: Matthew
 * Date: Apr 25, 2007
 * Time: 4:43:06 PM
 */
public class DoubleProperty implements IPropertyWrapper, IsSerializable
{
    Double d;

    public DoubleProperty()
    {
        d = null;
    }

    public DoubleProperty(double d)
    {
        setDbl(d);
    }

    public DoubleProperty(Double d)
    {
        set(d);
    }

    public Object get()
    {
        return d;
    }

    public void set(Object o)
    {
        d = (Double)o;
    }

    public Double getDouble()
    {
        return d;
    }

    public void setDbl(double d)
    {
        //this.d = Double.valueOf(d);
        this.d = new Double(d);
    }

    public double getDbl()
    {
        return d.doubleValue();
    }

    public String toString()
    {
        return String.valueOf(d);
    }
}
