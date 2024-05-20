package org.labkey.specimen.settings;

import org.labkey.api.action.SpringActionController;
import org.labkey.api.data.Container;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.specimen.SpecimenRequestStatus;
import org.labkey.api.specimen.settings.DisplaySettings;
import org.labkey.api.specimen.settings.RepositorySettings;
import org.labkey.api.specimen.settings.StatusSettings;
import org.labkey.specimen.SpecimenRequestManager;

import java.util.List;
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
        PropertyManager.PropertyMap settingsMap = PropertyManager.getWritableProperties(UserManager.getGuestUser(),
                container, "SpecimenRequestNotifications", true);
        settings.populateMap(settingsMap);
        settingsMap.save();
    }

    public void saveDisplaySettings(Container container, DisplaySettings settings)
    {
        PropertyManager.PropertyMap settingsMap = PropertyManager.getWritableProperties(UserManager.getGuestUser(),
                container, "SpecimenRequestDisplay", true);
        settings.populateMap(settingsMap);
        settingsMap.save();
    }

    public void saveStatusSettings(Container container, StatusSettings settings)
    {
        PropertyManager.PropertyMap settingsMap = PropertyManager.getWritableProperties(UserManager.getGuestUser(),
                container, "SpecimenRequestStatus", true);
        settings.populateMap(settingsMap);
        settingsMap.save();
    }

    public void saveRepositorySettings(Container container, RepositorySettings settings)
    {
        PropertyManager.PropertyMap settingsMap = PropertyManager.getWritableProperties(UserManager.getGuestUser(),
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
            return org.labkey.api.specimen.settings.SettingsManager.get().getRepositorySettings(container).isEnableRequests();
        }
        else
        {
            if (!org.labkey.api.specimen.settings.SettingsManager.get().getRepositorySettings(container).isEnableRequests())
                return false;
            List<SpecimenRequestStatus> statuses = SpecimenRequestManager.get().getRequestStatuses(container, user);
            return (statuses != null && statuses.size() > 1);
        }
    }
}
