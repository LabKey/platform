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
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.data.PropertyManager.PropertyMap;
import org.labkey.api.security.AuthenticationConfigurationCache;
import org.labkey.api.security.AuthenticationManager;
import org.labkey.api.security.AuthenticationSettingsAuditTypeProvider.AuthSettingsAuditEvent;
import org.labkey.api.security.PasswordExpiration;
import org.labkey.api.security.PasswordRule;
import org.labkey.api.security.User;
import org.labkey.api.settings.StartupProperty;
import org.labkey.api.util.StringUtilsLabKey;
import org.labkey.core.login.LoginController.SaveDbLoginPropertiesForm;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

/**
 * User: adam
 * Date: Jan 13, 2010
 * Time: 5:04:48 PM
 */
public class DbLoginManager
{
    // TODO: Move Logins table operations here

    public static DbLoginConfiguration getConfiguration()
    {
        Collection<DbLoginConfiguration> configurations = AuthenticationManager.getActiveConfigurations(DbLoginConfiguration.class);
        if (configurations.size() != 1)
            throw new IllegalStateException("Expected exactly one DbAuthenticationConfiguration, but was: " + configurations.size());

        return configurations.iterator().next();
    }

    public static PasswordRule getPasswordRule()
    {
        return getConfiguration().getPasswordRule();
    }

    public static PasswordExpiration getPasswordExpiration()
    {
        return getConfiguration().getExpiration();
    }

    static final String DATABASE_AUTHENTICATION_CATEGORY_KEY = "DatabaseAuthentication";

    public enum Key implements StartupProperty
    {
        Strength()
        {
            @Override
            public String getDescription()
            {
                return "Password strength. Valid values: " + Arrays.toString(PasswordRule.values());
            }
        },
        Expiration()
        {
            @Override
            public String getDescription()
            {
                return "Password expiration. Valid values: " + Arrays.toString(PasswordExpiration.values());
            }
        }
    }

    public static void saveProperties(User user, SaveDbLoginPropertiesForm form)
    {
        Map<String, String> oldProperties = getProperties();

        PropertyMap map = PropertyManager.getWritableProperties(DATABASE_AUTHENTICATION_CATEGORY_KEY, true);
        map.clear();
        map.put(Key.Strength.toString(), form.getStrength());
        map.put(Key.Expiration.toString(), form.getExpiration());
        map.save();
        AuthenticationConfigurationCache.clear();

        String changes = StringUtilsLabKey.getMapDifference(oldProperties, map);

        if (!changes.isEmpty())
        {
            AuthSettingsAuditEvent event = new AuthSettingsAuditEvent("Database authentication settings were changed");
            event.setChanges(String.join(", ", changes));
            AuditLogService.get().addEvent(user, event);
        }
    }

    static @NotNull Map<String, String> getProperties()
    {
        return PropertyManager.getProperties(DATABASE_AUTHENTICATION_CATEGORY_KEY);
    }
}
