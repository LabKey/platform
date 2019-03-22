/*
 * Copyright (c) 2013-2015 LabKey Corporation
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
 * A PropertyStore that does not encrypt its values when persisted in the database.
 *
 * User: adam
 * Date: 10/11/13
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
        return props.getEncryptionAlgorithm() == PropertyEncryption.None;
    }

    @Override
    protected void fillValueMap(TableSelector selector, PropertyManager.PropertyMap props)
    {
        validatePropertyMap(props);

        selector.fillValueMap(props);
    }

    @Override
    protected String getSaveValue(PropertyManager.PropertyMap props, @Nullable String value)
    {
        if (props.getEncryptionAlgorithm() != PropertyEncryption.None)
            throw new IllegalStateException("NormalPropertyStore should not be saving a PropertyMap encrypted with " + props.getEncryptionAlgorithm());

        return value;
    }

    @Override
    protected PropertyEncryption getPreferredPropertyEncryption()
    {
        return PropertyEncryption.None;
    }

    @Override
    protected void appendWhereFilter(SQLFragment sql)
    {
        sql.append("Encryption = ?");
        sql.add("None");
    }
}
