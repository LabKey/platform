package org.labkey.di.columnTransforms;

import org.labkey.api.di.columnTransform.ColumnTransformImpl;

/**
 * User: tgaluhn
 * Date: 9/27/2016
 *
 * Simple class to mark a source column as to be omitted from the passthrough to target output
 */
public class OmitSourceColumn extends ColumnTransformImpl
{
    @Override
    protected void registerOutput()
    {
        // Do Nothing
    }
}
