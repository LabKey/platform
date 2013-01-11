package org.labkey.api.reports.report;

import org.apache.commons.beanutils.Converter;
import org.labkey.api.reports.model.ViewCategory;
import org.labkey.api.reports.model.ViewCategoryManager;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: 1/10/13
 */
public class ViewCategoryConverter implements Converter
{
    @Override
    public Object convert(Class type, Object value)
    {
        if (value instanceof Integer && type.equals(ViewCategory.class))
        {
            return ViewCategoryManager.getInstance().getCategory((Integer)value);
        }
        return null;
    }
}

