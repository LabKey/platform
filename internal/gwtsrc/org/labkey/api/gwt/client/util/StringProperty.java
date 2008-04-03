package org.labkey.api.gwt.client.util;

import com.google.gwt.user.client.rpc.IsSerializable;

/**
 * Created by IntelliJ IDEA.
 * User: Matthew
 * Date: Apr 25, 2007
 * Time: 4:44:09 PM
 */
public class StringProperty implements IPropertyWrapper, IsSerializable
{
    String s;

    public StringProperty()
    {
        s = null;
    }

    public StringProperty(String s)
    {
        this.s = s;
    }

    public Object get()
    {
        return s;
    }

    public void set(Object o)
    {
        s = (String)o;
    }

    public String getString()
    {
        return s;
    }

    public String toString()
    {
        return s;
    }
}
