package org.labkey.di.columnTransforms;

import org.labkey.api.di.columnTransform.ColumnTransform;

/**
 * User: tgaluhn
 * Date: 9/22/2016
 *
 * Map columns names in-flight between source and target.
 * This is the default ColumnTransform class applied when no explicit class is set.
 * Allows the same source column to be specified in multiple <column/> elements to map to multiple target columns.
 */
public class ColumnMappingTransform extends ColumnTransform
{
    @Override
    public boolean requiresTargetColumnName()
    {
        return true;
    }

    @Override
    protected Object doTransform(Object inputValue)
    {
        return inputValue;
    }
}
