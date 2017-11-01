/*
 * Copyright (c) 2010-2017 LabKey Corporation
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
package org.labkey.core.login;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.security.PasswordExpiration;

import java.util.Map;

/**
 * User: adam
 * Date: Jan 13, 2010
 * Time: 5:04:48 PM
 */
public class DbLoginManager
{
    // TODO: Move Logins table operations here

    public static PasswordRule getPasswordRule()
    {
        String strength = getProperty(Key.Strength, PasswordRule.Weak);  // TODO: Change to strong -- new installs will use this

        return PasswordRule.valueOf(strength);
    }

    public static PasswordExpiration getPasswordExpiration()
    {
        String strength = getProperty(Key.Expiration, PasswordExpiration.Never);

        PasswordExpiration pe;

        try
        {
            pe = PasswordExpiration.valueOf(strength);
        }
        catch (IllegalArgumentException e)
        {
            pe = PasswordExpiration.Never;
        }

        return pe;
    }


    static final String DATABASE_AUTHENTICATION_CATEGORY_KEY = "DatabaseAuthentication";

    private enum Key { Strength, Expiration }

    public static void saveProperties(LoginController.Config config)
    {
        PropertyManager.PropertyMap map = PropertyManager.getWritableProperties(DATABASE_AUTHENTICATION_CATEGORY_KEY, true);
        map.clear();
        map.put(Key.Strength.toString(), config.getStrength());
        map.put(Key.Expiration.toString(), config.getExpiration());
        map.save();
    }

    private static @NotNull Map<String, String> getProperties()
    {
        return PropertyManager.getProperties(DATABASE_AUTHENTICATION_CATEGORY_KEY);
    }

    private static String getProperty(Key key, Enum defaultValue)
    {
        Map<String, String> props = getProperties();

        String value = props.get(key.toString());

        if (null != value)
            return value;
        else
            return defaultValue.toString();
    }
}
