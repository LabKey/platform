package org.labkey.api.gwt.client.util;

import com.google.gwt.user.client.rpc.IsSerializable;

/**
 * Created by IntelliJ IDEA.
 * User: Matthew
 * Date: Apr 25, 2007
 * Time: 4:43:06 PM
 */
public class BooleanProperty implements IPropertyWrapper, IsSerializable
{
    Boolean b;

    public BooleanProperty()
    {
        b = null;
    }

    public BooleanProperty(boolean b)
    {
        setBool(b);
    }

    public BooleanProperty(Boolean b)
    {
        set(b);
    }

    public Object get()
    {
        return b;
    }

    public void set(Object o)
    {
        b = (Boolean)o;
    }

    public Boolean getBoolean()
    {
        return b;
    }

    public void setBool(boolean b)
    {
        this.b = Boolean.valueOf(b);
    }

    public boolean getBool()
    {
        return b.booleanValue();
    }

    public String toString()
    {
        return String.valueOf(b);
    }
}
