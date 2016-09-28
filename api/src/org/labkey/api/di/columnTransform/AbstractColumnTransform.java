package org.labkey.api.di.columnTransform;

/**
 * User: tgaluhn
 * Date: 9/27/2016
 *
 * Base class for custom column transforms.
 * Implement doTransform() to do the work.
 *
 * For most implelmentations, this should be the parent class, not
 * ColumnTransformImpl.
 */
public abstract class AbstractColumnTransform extends ColumnTransformImpl
{
    @Override
    protected void registerOutput()
    {
        addOutputColumn(getTargetColumnName(), () ->
                doTransform(getInputValue()));
    }

    /**
     * Implement this method to perform the transform operation on the input value for a given .
     * If needed, other values (from other columns, constants, or configuration settings such as
     * etlName, source query name, etc.) are available via public getters.
     *
     * @param inputValue The value from the source query
     * @return The output value to be written to the target
     */
    protected abstract Object doTransform(Object inputValue);
}
