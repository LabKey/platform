package org.labkey.api.gwt.client.util;

import com.google.gwt.user.client.rpc.IsSerializable;

/**
 * Created by IntelliJ IDEA.
 * User: Matthew
 * Date: Apr 25, 2007
 * Time: 4:43:06 PM
 */
public class IntegerProperty implements IPropertyWrapper, IsSerializable
{
    Integer i;

    public IntegerProperty()
    {
        i = null;
    }

    public IntegerProperty(int i)
    {
        setInt(i);
    }

    public IntegerProperty(Integer i)
    {
        set(i);
    }

    public Object get()
    {
        return i;
    }

    public void set(Object o)
    {
        i = (Integer)o;
    }

    public Integer getInteger()
    {
        return i;
    }

    public void setInt(int i)
    {
        // this.i = Integer.valueOf(i);
        this.i = new Integer(i);
    }

    public int getInt()
    {
        return i.intValue();
    }

    public String toString()
    {
        return String.valueOf(i);
    }
}
