package org.labkey.api.data;

import org.apache.commons.beanutils.Converter;

/**
 * Created by IntelliJ IDEA.
 * User: dave
 * Date: Apr 1, 2010
 * Time: 4:00:09 PM
 */
public class ShowRowsConverter implements Converter
{
    public Object convert(Class type, Object value)
    {
        if(null == value || "null".equals(value))
            return null;
        else
            return ShowRows.valueOf(value.toString().toUpperCase());
    }
}

