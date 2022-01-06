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
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.PropertyManager.PropertyMap;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.Encryption;
import org.labkey.api.security.Encryption.DecryptionException;
import org.labkey.api.security.Encryption.EncryptionMigrationHandler;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.ConfigurationException;
import org.labkey.api.util.logging.LogHelper;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * A PropertyStore that encrypts its contents when writing to the database, and automatically decrypts on read. Uses
 * the EncryptionKey, stored as a property in labkey.xml (or its equivalent deployment descriptor).
 *
 * User: adam
 * Date: 10/11/13
 */
public class EncryptedPropertyStore extends AbstractPropertyStore implements EncryptionMigrationHandler
{
    private static final Logger LOG = LogHelper.getLogger(EncryptedPropertyStore.class, "Encrypted property operations");

    private final PropertyEncryption _preferredPropertyEncryption;

    public EncryptedPropertyStore()
    {
        super("Encrypted Properties");

        if (Encryption.isEncryptionPassPhraseSpecified())
            _preferredPropertyEncryption = PropertyEncryption.AES128;
        else
            _preferredPropertyEncryption = PropertyEncryption.NoKey;
    }

    @Override
    protected void validateStore()
    {
        if (PropertyEncryption.AES128 != _preferredPropertyEncryption)
            throw new ConfigurationException("Attempting to use the encrypted property store, but EncryptionKey has not been specified in " + AppProps.getInstance().getWebappConfigurationFilename() + ".",
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

    @Override
    public void migrateEncryptedContent(String oldPassPhrase, String keySource)
    {
        LOG.info("  Attempting to migrate encrypted property store values");
        TableInfo sets = PropertySchema.getInstance().getTableInfoPropertySets();
        TableInfo props = PropertySchema.getInstance().getTableInfoProperties();

        new TableSelector(sets, Set.of("Set", "Category", "Encryption"), new SimpleFilter(FieldKey.fromParts("Encryption"), "None", CompareType.NEQ), null).forEachMap(map -> {
            int set = (int)map.get("Set");
            String encryption = (String)map.get("Encryption");
            String propertySetName = "\"" + map.get("Category") + "\" (Set = " + set + ")";
            LOG.info("    Attempting to migrate encrypted property set " + propertySetName);
            PropertyEncryption pe = PropertyEncryption.getBySerializedName(encryption);

            if (null != pe)
            {
                try
                {
                    Map<String, String> newProps = new HashMap<>();

                    for (Map<String, Object> m : new TableSelector(props, new SimpleFilter(FieldKey.fromParts("Set"), set, CompareType.EQUAL), null).getMapCollection())
                    {
                        String name = (String) m.get("Name");
                        String encryptedValue = (String) m.get("Value");
                        LOG.info("      Attempting to decrypt property \"" + name + "\"");
                        String decryptedValue = pe.decrypt(Base64.decodeBase64(encryptedValue), oldPassPhrase, keySource);
                        String newEncryptedValue = Base64.encodeBase64String(pe.encrypt(decryptedValue));
                        assert decryptedValue.equals(pe.decrypt(Base64.decodeBase64(newEncryptedValue))); // TODO: Remove
                        newProps.put(name, newEncryptedValue);
                    }

                    for (Map.Entry<String, String> entry : newProps.entrySet())
                    {
                        try
                        {
                            Table.update(null, props, Map.of("Value", entry.getValue()), Map.of("Set", set, "Name", entry.getKey()));
                        }
                        catch (RuntimeSQLException e)
                        {
                            LOG.error("Failed to save re-encrypted property \"" + entry.getKey() + "\"", e);
                        }
                    }

                    LOG.info("    Successfully migrated encrypted property set " + propertySetName);
                }
                catch (DecryptionException e)
                {
                    LOG.warn("    Failed to decrypt the previous property. Skipping encrypted property set " + propertySetName);
                }
            }
        });

        // Clear the cache of encrypted properties since we updated the database directly
        clearCache();
        LOG.info("  Migration of encrypted property store values is complete");
    }
}
