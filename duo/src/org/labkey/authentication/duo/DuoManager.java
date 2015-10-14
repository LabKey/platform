/*
 * Copyright (c) 2015 LabKey Corporation. All rights reserved. No portion of this work may be reproduced in
 * any form or by any electronic or mechanical means without written permission from LabKey Corporation.
 */
package org.labkey.authentication.duo;


import com.duosecurity.duoweb.DuoWeb;
import com.duosecurity.duoweb.DuoWebException;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.User;
import org.springframework.validation.BindException;

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
    private static final Logger LOG = Logger.getLogger(DuoManager.class);
    private static final String DUO_AUTHENTICATION_CATEGORY_KEY = "DuoAuthentication";

    public enum Key {
        IntegrationKey, // an id grouping for users
        SecretKey, // effectively duo's public key
        ApplicationKey // effectively a LabKey private key;
                {
                    @Override
                    public String getDefault()
                    {
                        return generateApplicationKey();
                    }
                },
        APIHostname, // duo server
        Bypass
                {
                    @Override
                    public String getDefault()
                    {
                        return ModuleLoader.getServletContext().getInitParameter("org.labkey.authentication.duo." + Bypass.toString());
                    }
                };

        public String getDefault(){return "";}
    }

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

    private static String getProperty(Key key)
    {
        Map<String, String> props = getProperties();

        String value = props.get(key.toString());
        if (StringUtils.isBlank(value))
            value = key.getDefault();
        return value;
    }

    public static String getIntegrationKey()
    {
        return getProperty(Key.IntegrationKey);
    }

    public static String getSecretKey()
    {
        return getProperty(Key.SecretKey);
    }

    public static String getApplicationKey()
    {
        return getProperty(Key.ApplicationKey);
    }

    public static String getAPIHostname()
    {
        return getProperty(Key.APIHostname);
    }

    private static String generateApplicationKey()
    {
        return RandomStringUtils.randomAlphanumeric(64);
    }

    public static boolean isConfigured()
    {
        return StringUtils.isNoneBlank(getIntegrationKey(), getSecretKey(), getAPIHostname());
    }

    public static String generateSignedRequest(User u)
    {
        return DuoWeb.signRequest(getIntegrationKey(), getSecretKey(), getApplicationKey(), Integer.toString(u.getUserId()));
    }

    public static String verifySignedResponse(String signedResponse, boolean test, BindException errors)
    {
        String verifiedUsername = null;
        try
        {
            verifiedUsername = DuoWeb.verifyResponse(getIntegrationKey(), getSecretKey(), getApplicationKey(), signedResponse);
        }
        catch (DuoWebException e)
        {
            LOG.warn("Bad signed response from Duo.", e);
            errors.reject("Failed Duo authentication.");
        }
        catch (NoSuchAlgorithmException | InvalidKeyException | IOException e)
        {
            // This is likely an environment or provider problem
            LOG.warn("Non-Duo error verifying Duo response", e);
            errors.reject("Server error");
        }

        return verifiedUsername;
    }

    public static boolean bypassDuoAuthentication()
    {
        return Boolean.parseBoolean(Key.Bypass.getDefault());
    }
}
