package org.labkey.api.gwt.client.util;

import com.google.gwt.user.client.rpc.IsSerializable;

import java.util.Date;

/**
 * Created by IntelliJ IDEA.
 * User: Matthew
 * Date: Apr 25, 2007
 * Time: 4:44:09 PM
 */
public class DateProperty implements IPropertyWrapper, IsSerializable
{
    Date d;

    public DateProperty()
    {
        d = null;
    }

    public DateProperty(Date d)
    {
        this.d = d;
    }

    public Object get()
    {
        return d;
    }

    public void set(Object o)
    {
        d = (Date)o;
    }

    public Date getDate()
    {
        return d;
    }

    public String toString()
    {
        return String.valueOf(d);
    }
}
