/*
 * Copyright (c) 2013-2026 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.Compress;
import org.labkey.api.util.ConfigurationException;

import java.util.HashMap;
import java.util.Map;
import java.util.zip.DataFormatException;

/**
 * Encryption techniques that are available for property stores. Do not change the serialized names or implementations
 * of these algorithms once they are in use.
 * User: adam
 * Date: 10/24/13
 */

public enum PropertyEncryption
{
    /** Just a marker enum for unencrypted property store */
    None
        {
            @Override
            public byte @NotNull[] encrypt(@NotNull String plainText)
            {
                throw new IllegalStateException("Incorrect PropertyStore for this PropertyMap");
            }

            @Override
            public @NotNull String decrypt(byte @NotNull[] cipherText)
            {
                throw new IllegalStateException("Incorrect PropertyStore for this PropertyMap");
            }

            @Override
            public @NotNull String getSerializedName()
            {
                return "None";
            }
        },
    /** Not real encryption either, just for testing */
    Test
        {
            @Override
            public byte @NotNull[] encrypt(@NotNull String plainText)
            {
                return Compress.deflate(plainText);
            }

            @Override
            public @NotNull String decrypt(byte @NotNull[] cipherText)
            {
                try
                {
                    return Compress.inflate(cipherText);
                }
                catch (DataFormatException e)
                {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public @NotNull String getSerializedName()
            {
                return "Test";
            }
        },
    /** No encryption key was specified in application.properties, so throw ConfigurationException */
    NoKey
        {
            @Override
            public byte @NotNull[] encrypt(@NotNull String plainText)
            {
                throw getConfigurationException();
            }

            @Override
            public @NotNull String decrypt(byte @NotNull[] cipherText)
            {
                throw getConfigurationException();
            }

            @Override
            public @NotNull String getSerializedName()
            {
                return "NoKey";
            }

            private ConfigurationException getConfigurationException()
            {
                return new ConfigurationException("Attempting to save encrypted properties but EncryptionKey has not been specified in " + AppProps.getInstance().getWebappConfigurationFilename() + ".",
                        "Edit " + AppProps.getInstance().getWebappConfigurationFilename() + " and provide a suitable encryption key. See the server configuration documentation on labkey.org.");
            }
        },
    AES128
        {
            @Override
            public byte @NotNull[] encrypt(@NotNull String plainText)
            {
                return AES.get().encrypt(plainText);
            }

            @Override
            public @NotNull String decrypt(byte @NotNull[] cipherText)
            {
                return AES.get().decrypt(cipherText);
            }

            @Override
            public @NotNull String getSerializedName()
            {
                return "AES128";
            }
        };

    public abstract byte @NotNull[] encrypt(@NotNull String plainText);
    public abstract @NotNull String decrypt(byte @NotNull[] cipherText);

    // Canonical name to store in the property set. Do not change these return values, once they are in use!
    // Consider: if we need to, could change to a collection of names, the first being canonical, for backward
    // compatibility purposes.
    public abstract @NotNull String getSerializedName();

    private static final Map<String, PropertyEncryption> SERIALIZED_NAME_MAP = new HashMap<>();

    static
    {
        for (PropertyEncryption encryption : PropertyEncryption.values())
        {
            SERIALIZED_NAME_MAP.put(encryption.getSerializedName(), encryption);
        }
    }

    static @Nullable PropertyEncryption getBySerializedName(String name)
    {
        return SERIALIZED_NAME_MAP.get(name);
    }
}
