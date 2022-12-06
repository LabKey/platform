package org.labkey.api.specimen.settings;

import org.labkey.api.action.SpringActionController;
import org.labkey.api.data.Container;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.security.UserManager;
import org.labkey.api.specimen.SpecimenMigrationService;
import org.labkey.api.specimen.SpecimenRequestStatus;
import org.labkey.api.specimen.SpecimenSchema;
import org.labkey.api.study.QueryHelper;

import java.util.List;
import java.util.Map;

public class SettingsManager
{
    private static final SettingsManager INSTANCE = new SettingsManager();

    private final QueryHelper<SpecimenRequestStatus> _requestStatusHelper;

    public static SettingsManager get()
    {
        return INSTANCE;
    }

    private SettingsManager()
    {
        _requestStatusHelper = new QueryHelper<>(()->SpecimenSchema.get().getTableInfoSampleRequestStatus(), SpecimenRequestStatus.class);
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

    public DisplaySettings getDisplaySettings(Container container)
    {
        Map<String, String> settingsMap = PropertyManager.getProperties(UserManager.getGuestUser(), container, "SpecimenRequestDisplay");
        return settingsMap.isEmpty() ? DisplaySettings.getDefaultSettings() : new DisplaySettings(settingsMap);
    }

    public void saveDisplaySettings(Container container, DisplaySettings settings)
    {
        PropertyManager.PropertyMap settingsMap = PropertyManager.getWritableProperties(UserManager.getGuestUser(),
                container, "SpecimenRequestDisplay", true);
        settings.populateMap(settingsMap);
        settingsMap.save();
    }

    public StatusSettings getStatusSettings(Container container)
    {
        Map<String, String> settingsMap = PropertyManager.getProperties(UserManager.getGuestUser(), container, "SpecimenRequestStatus");
        return settingsMap.get(StatusSettings.KEY_USE_SHOPPING_CART) == null ? StatusSettings.getDefaultSettings() : new StatusSettings(settingsMap);
    }

    public void saveStatusSettings(Container container, StatusSettings settings)
    {
        PropertyManager.PropertyMap settingsMap = PropertyManager.getWritableProperties(UserManager.getGuestUser(),
                container, "SpecimenRequestStatus", true);
        settings.populateMap(settingsMap);
        settingsMap.save();
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

    public void saveRepositorySettings(Container container, RepositorySettings settings)
    {
        PropertyManager.PropertyMap settingsMap = PropertyManager.getWritableProperties(UserManager.getGuestUser(),
                container, "SpecimenRepositorySettings", true);
        settings.populateMap(settingsMap);
        settingsMap.save();
        SpecimenMigrationService.get().clearGroupedValuesForColumn(container);     // May have changed groupings
    }

    public boolean isSpecimenRequestEnabled(Container container)
    {
        return isSpecimenRequestEnabled(container, true);
    }

    public boolean isSpecimenRequestEnabled(Container container, boolean checkExistingStatuses)
    {
        if (!checkExistingStatuses)
        {
            return getRepositorySettings(container).isEnableRequests();
        }
        else
        {
            if (!getRepositorySettings(container).isEnableRequests())
                return false;
            List<SpecimenRequestStatus> statuses = _requestStatusHelper.get(container, "SortOrder");
            return (statuses != null && statuses.size() > 1);
        }
    }
}
