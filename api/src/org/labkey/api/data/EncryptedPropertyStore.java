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

import org.apache.commons.codec.binary.Base64;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.PropertyManager.PropertyMap;
import org.labkey.api.security.Encryption;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.ConfigurationException;

/**
 * A PropertyStore that encrypts its contents when writing to the database, and automatically decrypts on read. Uses
 * the MasterEncryptionKey, stored as a property in labkey.xml (or its equivalent deployment descriptor).
 *
 * User: adam
 * Date: 10/11/13
 */
public class EncryptedPropertyStore extends AbstractPropertyStore
{
    private final PropertyEncryption _preferredPropertyEncryption;

    public EncryptedPropertyStore()
    {
        super("Encrypted Properties");

        if (Encryption.isMasterEncryptionPassPhraseSpecified())
            _preferredPropertyEncryption = PropertyEncryption.AES128;
        else
            _preferredPropertyEncryption = PropertyEncryption.NoKey;
    }

    @Override
    protected void validateStore()
    {
        if (PropertyEncryption.AES128 != _preferredPropertyEncryption)
            throw new ConfigurationException("Attempting to use the encrypted property store, but MasterEncryptionKey has not been specified in " + AppProps.getInstance().getWebappConfigurationFilename() + ".",
                "Edit " + AppProps.getInstance().getWebappConfigurationFilename() + " and provide a suitable encryption key. See the server configuration documentation on labkey.org.");
    }

    @Override
    protected boolean isValidPropertyMap(PropertyMap props)
    {
        return props.getEncryptionAlgorithm() != PropertyEncryption.None;
    }

    @Override
    protected String getSaveValue(PropertyMap props, @Nullable String value)
    {
        if (null == value)
            return null;

        return Base64.encodeBase64String(props.getEncryptionAlgorithm().encrypt(value));
    }

    @Override
    protected void fillValueMap(TableSelector selector, final PropertyMap props)
    {
        final PropertyEncryption propertyEncryption = props.getEncryptionAlgorithm();

        validatePropertyMap(props);

        selector.forEach(rs -> {
            String encryptedValue = rs.getString(2);
            String value = null == encryptedValue ? null : propertyEncryption.decrypt(Base64.decodeBase64(encryptedValue));
            props.put(rs.getString(1), value);
        });
    }

    @Override
    protected PropertyEncryption getPreferredPropertyEncryption()
    {
        return _preferredPropertyEncryption;
    }

    @Override
    protected void appendWhereFilter(SQLFragment sql)
    {
        sql.append("NOT Encryption = ?");
        sql.add("None");
    }
}
