package org.labkey.api.data;

import org.jetbrains.annotations.Nullable;

/**
 * User: adam
 * Date: 10/11/13
 * Time: 9:31 PM
 */
public class NormalPropertyStore extends AbstractPropertyStore
{
    public NormalPropertyStore()
    {
        super("Properties");
    }

    @Override
    protected void validateStore()
    {
        // Normal property store is always valid
    }

    @Override
    protected boolean isValidPropertyMap(PropertyManager.PropertyMap props)
    {
        return props.getEncryption() == Encryption.None;
    }

    @Override
    protected void fillValueMap(TableSelector selector, PropertyManager.PropertyMap props)
    {
        if (props.getEncryption() != Encryption.None)
            throw new IllegalStateException("NormalPropertyStore should not be retrieving a PropertyMap encrypted with " + props.getEncryption());

        selector.fillValueMap(props);
    }

    @Override
    protected String getSaveValue(PropertyManager.PropertyMap props, @Nullable String value)
    {
        if (props.getEncryption() != Encryption.None)
            throw new IllegalStateException("NormalPropertyStore should not be saving a PropertyMap encrypted with " + props.getEncryption());

        return value;
    }

    @Override
    protected Encryption getPreferredEncryption()
    {
        return Encryption.None;
    }
}
