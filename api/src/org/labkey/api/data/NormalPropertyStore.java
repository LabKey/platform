/*
 * Copyright (c) 2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
