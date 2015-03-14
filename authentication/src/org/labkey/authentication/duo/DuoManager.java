package org.labkey.authentication.duo;


import com.duosecurity.duoweb.DuoWeb;
import com.duosecurity.duoweb.DuoWebException;
import org.apache.commons.lang3.StringUtils;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.User;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Map;

/**
 * User: tgaluhn
 * Date: 3/6/2015
 */
public class DuoManager
{
    private static final String DUO_AUTHENTICATION_CATEGORY_KEY = "DuoAuthentication";
    private enum Key {IntegrationKey, SecretKey, ApplicationKey, APIHostname}

    public static void saveProperties(DuoController.Config config)
    {
        PropertyManager.PropertyMap map = PropertyManager.getEncryptedStore().getWritableProperties(DUO_AUTHENTICATION_CATEGORY_KEY, true);
        map.clear();
        map.put(Key.IntegrationKey.toString(), config.getIntegrationKey());
        map.put(Key.SecretKey.toString(), config.getSecretKey());
        map.put(Key.ApplicationKey.toString(), config.getApplicationKey());
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

    public static String getApplicationKey()
    {
        return getProperty(Key.ApplicationKey, "");
    }

    public static String getAPIHostname()
    {
        return getProperty(Key.APIHostname, "");
    }

    private static String generateApplicationKey()
    {
        //TODO: allow for generating a private 40+ character key, kept secret from Duo
        return null;
    }

    public static boolean isConfigured()
    {
        return StringUtils.isNoneBlank(getIntegrationKey(), getSecretKey(), getAPIHostname());
    }

    public static String generateSignedRequest(User u)
    {
        String signedRequest = null;
        if (isConfigured())
        {
            signedRequest = DuoWeb.signRequest(getIntegrationKey(), getSecretKey(), getApplicationKey(), u.getEmail());
        }
        return signedRequest;
    }

    public static String verifySignedResponse(String signedResponse)
    {
        String verifiedUsername = null;
        if (isConfigured())
        {
            try
            {
                verifiedUsername = DuoWeb.verifyResponse(getIntegrationKey(), getSecretKey(), getApplicationKey(), signedResponse);
                // TODO: figure out what to do with exceptions- for test mode, probably rethrow as RunTimeException.
                // For normal usage, log them and give nice error to user that authentication is not available at this time,
                // try again later and/or contact sys admin
            }
            catch (DuoWebException e)
            {
                e.printStackTrace();
            }
            catch (NoSuchAlgorithmException e)
            {
                e.printStackTrace();
            }
            catch (InvalidKeyException e)
            {
                e.printStackTrace();
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }
        return verifiedUsername;
    }

    public static boolean bypassDuoAuthentication()
    {
        return Boolean.parseBoolean(ModuleLoader.getServletContext().getInitParameter("org.labkey.authentication.duo.bypassDuo"));
    }
}
