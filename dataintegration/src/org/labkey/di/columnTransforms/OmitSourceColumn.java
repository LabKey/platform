package org.labkey.di.columnTransforms;

import org.labkey.api.di.columnTransform.AbstractColumnTransform;

/**
 * User: tgaluhn
 * Date: 9/27/2016
 *
 * Simple class to mark a source column as to be omitted from the passthrough to target output
 */
public class OmitSourceColumn extends AbstractColumnTransform
{
    @Override
    protected void registerOutput()
    {
        // Do Nothing
    }

    @Override
    protected Object doTransform(Object inputValue)
    {
        // This should never be called, as registerOutput() is a no-op
        throw new UnsupportedOperationException();
    }
}
