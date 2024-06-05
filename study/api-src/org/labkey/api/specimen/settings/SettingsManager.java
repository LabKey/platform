package org.labkey.api.specimen.settings;

import org.labkey.api.data.Container;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.security.UserManager;

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
