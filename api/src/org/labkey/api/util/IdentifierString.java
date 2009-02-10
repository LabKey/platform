package org.labkey.api.util;

import org.labkey.api.data.Container;
import org.apache.commons.beanutils.ConversionException;

import java.util.regex.Pattern;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: Feb 9, 2009
 * Time: 10:40:19 AM
 */
public class IdentifierString extends HString
{
    Pattern idPattern = Pattern.compile("\\p{Alpha}\\p{Alnum}*");

    public IdentifierString(String s)
    {
        _source = s;
        _tainted = !idPattern.matcher(s).matches();
    }

    public IdentifierString(Container c)
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
        return (o instanceof IdentifierString) && _source.equalsIgnoreCase(((IdentifierString)o)._source);
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
            if (value instanceof IdentifierString)
                return value;
            if (value instanceof HString)
                value = ((HString)value).getSource();
            IdentifierString g = new IdentifierString(String.valueOf(value));
            if (!g.isEmpty() && g.isTainted())
                throw new ConversionException("Invalid identifier");
            return g;
        }
    }
}