package org.labkey.authentication.duo;


import org.apache.commons.lang3.StringUtils;
import org.labkey.api.data.PropertyManager;

import java.util.Map;

/**
 * User: tgaluhn
 * Date: 3/6/2015
 */
public class DuoManager
{
    private static final String DUO_AUTHENTICATION_CATEGORY_KEY = "DUOAuthentication";
    private enum Key {IntegrationKey, SecretKey, APIHostname}

    public static void saveProperties(DuoController.Config config)
    {
        PropertyManager.PropertyMap map = PropertyManager.getEncryptedStore().getWritableProperties(DUO_AUTHENTICATION_CATEGORY_KEY, true);
        map.clear();
        map.put(Key.IntegrationKey.toString(), config.getIntegrationKey());
        map.put(Key.SecretKey.toString(), config.getSecretKey());
        map.put(Key.APIHostname.toString(), config.getApiHostname());
        map.save();
    }
    private static Map<String, String> getProperties()
    {
        return PropertyManager.getEncryptedStore().getProperties(DUO_AUTHENTICATION_CATEGORY_KEY);
    }

    private static String getProperty(Key key, String defaultValue)
    {
        Map<String, String> props = getProperties();

        String value = props.get(key.toString());

        if (StringUtils.isNotEmpty(value))
            return value;
        else
            return defaultValue;
    }

    public static String getIntegrationKey()
    {
        return getProperty(Key.IntegrationKey, "");
    }

    public static String getSecretKey()
    {
        return getProperty(Key.SecretKey, "");
    }

    public static String getAPIHostname()
    {
        return getProperty(Key.APIHostname, "");
    }

}
