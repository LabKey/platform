/*
 * Copyright (c) 2024-2026 LabKey Corporation
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
package org.labkey.specimen.settings;

import org.labkey.api.action.SpringActionController;
import org.labkey.api.data.Container;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.data.PropertyManager.WritablePropertyMap;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.specimen.SpecimenRequestManager;
import org.labkey.specimen.SpecimenRequestStatus;

import java.util.Collection;
import java.util.Map;

public class SettingsManager
{
    private static final SettingsManager INSTANCE = new SettingsManager();

    public static SettingsManager get()
    {
        return INSTANCE;
    }

    private SettingsManager()
    {
    }

    public RequestNotificationSettings getRequestNotificationSettings(Container container)
    {
        Map<String,String> settingsMap = PropertyManager.getProperties(UserManager.getGuestUser(),
                container, "SpecimenRequestNotifications");
        if (settingsMap.get("ReplyTo") == null)
        {
            try (var ignore = SpringActionController.ignoreSqlUpdates())
            {
                RequestNotificationSettings defaults = RequestNotificationSettings.getDefaultSettings(container);
                saveRequestNotificationSettings(container, defaults);
                return defaults;
            }
        }
        else
            return new RequestNotificationSettings(settingsMap);
    }

    public void saveRequestNotificationSettings(Container container, RequestNotificationSettings settings)
    {
        WritablePropertyMap settingsMap = PropertyManager.getWritableProperties(UserManager.getGuestUser(),
                container, "SpecimenRequestNotifications", true);
        settings.populateMap(settingsMap);
        settingsMap.save();
    }

    public void saveDisplaySettings(Container container, DisplaySettings settings)
    {
        WritablePropertyMap settingsMap = PropertyManager.getWritableProperties(UserManager.getGuestUser(),
                container, "SpecimenRequestDisplay", true);
        settings.populateMap(settingsMap);
        settingsMap.save();
    }

    public void saveStatusSettings(Container container, StatusSettings settings)
    {
        WritablePropertyMap settingsMap = PropertyManager.getWritableProperties(UserManager.getGuestUser(),
                container, "SpecimenRequestStatus", true);
        settings.populateMap(settingsMap);
        settingsMap.save();
    }

    public void saveRepositorySettings(Container container, RepositorySettings settings)
    {
        WritablePropertyMap settingsMap = PropertyManager.getWritableProperties(UserManager.getGuestUser(),
                container, "SpecimenRepositorySettings", true);
        settings.populateMap(settingsMap);
        settingsMap.save();
        SpecimenRequestManager.get().clearGroupedValuesForColumn(container);     // May have changed groupings
    }

    public boolean isSpecimenRequestEnabled(Container container, User user)
    {
        return isSpecimenRequestEnabled(container, true, user);
    }

    public boolean isSpecimenRequestEnabled(Container container, boolean checkExistingStatuses, User user)
    {
        if (!checkExistingStatuses)
        {
            return SettingsManager.get().getRepositorySettings(container).isEnableRequests();
        }
        else
        {
            if (!SettingsManager.get().getRepositorySettings(container).isEnableRequests())
                return false;
            Collection<SpecimenRequestStatus> statuses = SpecimenRequestManager.get().getRequestStatuses(container, user);
            return (statuses != null && statuses.size() > 1);
        }
    }

    public DisplaySettings getDisplaySettings(Container container)
    {
        Map<String, String> settingsMap = PropertyManager.getProperties(UserManager.getGuestUser(), container, "SpecimenRequestDisplay");
        return settingsMap.isEmpty() ? DisplaySettings.getDefaultSettings() : new DisplaySettings(settingsMap);
    }

    public StatusSettings getStatusSettings(Container container)
    {
        Map<String, String> settingsMap = PropertyManager.getProperties(UserManager.getGuestUser(), container, "SpecimenRequestStatus");
        return settingsMap.get(StatusSettings.KEY_USE_SHOPPING_CART) == null ? StatusSettings.getDefaultSettings() : new StatusSettings(settingsMap);
    }

    public boolean isSpecimenShoppingCartEnabled(Container container)
    {
        return getStatusSettings(container).isUseShoppingCart();
    }

    public RepositorySettings getRepositorySettings(Container container)
    {
        Map<String,String> settingsMap = PropertyManager.getProperties(UserManager.getGuestUser(), container, "SpecimenRepositorySettings");
        return settingsMap.isEmpty() ? RepositorySettings.getDefaultSettings(container) : new RepositorySettings(container, settingsMap);
    }
}
