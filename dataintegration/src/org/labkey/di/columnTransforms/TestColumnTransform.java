package org.labkey.di.columnTransforms;

import org.labkey.api.di.columnTransform.AbstractColumnTransform;

/**
 * User: tgaluhn
 * Date: 9/26/2016
 *
 * Used in automated testing and gives an example of implementing a transform.
 * Prepends the value of the "id" column of the source query
 * to the value of the source column specified in the ETL xml,
 * then appends the value of the "myConstant" constant set in the xml.
 */
public class TestColumnTransform extends AbstractColumnTransform
{
    @Override
    protected Object doTransform(Object inputValue)
    {
        String prefix = getInputValue("id").toString();
        return prefix + "_" + inputValue + "_" + getConstantValue("myConstant");
    }
}
