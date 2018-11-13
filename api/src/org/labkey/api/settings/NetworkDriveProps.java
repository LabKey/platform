/*
 * Copyright (c) 2018 LabKey Corporation
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
package org.labkey.api.settings;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.PropertyManager;

public class NetworkDriveProps
{
    static final String NETWORK_DRIVE_CATEGORY = "MappedNetworkDrive";
    static final String NETWORK_DRIVE_LETTER = "networkDriveLetter";
    static final String NETWORK_DRIVE_PATH = "networkDrivePath";
    static final String NETWORK_DRIVE_USER = "networkDriveUser";
    static final String NETWORK_DRIVE_PASSWORD = "networkDrivePassword";

    @Nullable
    public static String getNetworkDriveLetter()
    {
        return getStringValue(NETWORK_DRIVE_LETTER);
    }

    @Nullable
    public static String getNetworkDrivePath()
    {
        return getStringValue(NETWORK_DRIVE_PATH);
    }

    @Nullable
    public static String getNetworkDriveUser()
    {
        return getStringValue(NETWORK_DRIVE_USER);
    }

    @Nullable
    public static String getNetworkDrivePassword()
    {
        return getStringValue(NETWORK_DRIVE_PASSWORD);
    }

    public static void setNetworkDriveLetter(String letter)
    {
        saveStringValue(NETWORK_DRIVE_LETTER, letter);
    }

    public static void setNetworkDrivePath(String path)
    {
        saveStringValue(NETWORK_DRIVE_PATH, path);
    }

    public static void setNetworkDriveUser(String user)
    {
        saveStringValue(NETWORK_DRIVE_USER, user);
    }

    public static void setNetworkDrivePassword(String password)
    {
        saveStringValue(NETWORK_DRIVE_PASSWORD, password);
    }

    private static void saveStringValue(String key, String value)
    {
        if (StringUtils.isNotBlank(value))
        {
            PropertyManager.PropertyMap map = PropertyManager.getEncryptedStore().getWritableProperties(NETWORK_DRIVE_CATEGORY, true);
            map.put(key, value);
            map.save();
        }
    }

    @Nullable
    private static String getStringValue(String key)
    {
        PropertyManager.PropertyMap map = PropertyManager.getEncryptedStore().getProperties(NETWORK_DRIVE_CATEGORY);
        return map.get(key);
    }
}
