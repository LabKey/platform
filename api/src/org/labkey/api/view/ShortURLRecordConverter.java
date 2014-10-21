package org.labkey.api.view;

import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.beanutils.Converter;
import org.labkey.api.services.ServiceRegistry;

/**
 * User: vsharma
 * Date: 8/26/2014
 * Time: 10:23 AM
 */
public class ShortURLRecordConverter implements Converter
{
    public Object convert(Class type, Object value)
    {
        if (value == null || !type.equals(ShortURLRecord.class))
            return null;
        else
        {
            ShortURLRecord record = ServiceRegistry.get(ShortURLService.class).getForEntityId(value.toString());
            if(record == null)
            {
                throw new ConversionException("Could not convert " + value.toString() + " to a ShortURLRecord.");
            }
            return record;
        }
    }
}
