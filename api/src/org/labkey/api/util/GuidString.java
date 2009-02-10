package org.labkey.api.util;

import org.labkey.api.data.Container;
import org.apache.commons.beanutils.ConversionException;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: Feb 9, 2009
 * Time: 10:40:19 AM
 */
public class GuidString extends HString
{
    public GuidString(String s)
    {
        _source = s;
        _tainted = !GUID.isGUID(_source);
    }


    public GuidString(Container c)
    {
        _source = c.getId();
        _tainted = false;
    }


    public String toString()
    {
        return isTainted() ? "" : _source;
    }

    @Override
    public boolean equals(Object o)
    {
        return (o instanceof GuidString) && _source.equalsIgnoreCase(((GuidString)o)._source);
    }

    @Override
    public int compareTo(HString str)
    {
        return super.compareToIgnoreCase(str);
    }


   public static class Converter implements org.apache.commons.beanutils.Converter
    {
        public Object convert(Class type, Object value)
        {
            if (value == null)
                return null;
            if (value instanceof GuidString)
                return value;
            if (value instanceof HString)
                value = ((HString)value).getSource();
            GuidString g = new GuidString(String.valueOf(value));
            if (!g.isEmpty() && g.isTainted())
                throw new ConversionException("Invalid unique identifier");
            return g;
        }
    }
}
