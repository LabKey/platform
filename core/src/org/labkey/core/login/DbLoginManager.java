package org.labkey.core.login;

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

        return PasswordExpiration.valueOf(strength);
    }


    private static final String DATABASE_AUTHENTICATION_CATEGORY_KEY = "DatabaseAuthentication";
    private enum Key { Strength, Expiration }

    public static void saveProperties(LoginController.Config config)
    {
        PropertyManager.PropertyMap map = PropertyManager.getWritableProperties(DATABASE_AUTHENTICATION_CATEGORY_KEY, true);
        map.clear();
        map.put(Key.Strength.toString(), config.getStrength());
        map.put(Key.Expiration.toString(), config.getExpiration());
        PropertyManager.saveProperties(map);
    }

    private static Map<String, String> getProperties()
    {
        return PropertyManager.getProperties(DATABASE_AUTHENTICATION_CATEGORY_KEY, true);
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
