/*
 * Copyright (c) 2015 LabKey Corporation. All rights reserved. No portion of this work may be reproduced in
 * any form or by any electronic or mechanical means without written permission from LabKey Corporation.
 */
package org.labkey.authentication.saml;

import com.onelogin.AccountSettings;
import com.onelogin.AppSettings;
import com.onelogin.saml.AuthRequest;
import com.onelogin.saml.Response;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;

import javax.servlet.http.HttpServletRequest;
import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Map;

/**
 * User: tgaluhn
 * Date: 1/20/2015
 *
 * Work in progress prototype. Makes use of the OneLogin Java SAML toolkit available at:
 * https://github.com/onelogin/java-saml
 *
 *
 * The code here is modeled on the sample web app found in the toolkit.
 *
 * No configuration options have been provided yet. This is hardcoded to go against Tony's instance of the test IdP at onelogin.com.
 * See Tony for assistance if you want to test this.
 *
 */
public class SamlManager
{
    public static final String SAML_RESPONSE_PARAMETER = "SAMLResponse";
    public static final String SAML_REQUEST_PARAMETER = "SAMLRequest";
    protected static final String SAML_PROPERTIES_NORMAL_CATEGORY_KEY= "SAMLNormalProperties";
    protected static final String SAML_PROPERTIES_ENCRYPTED_CATEGORY_KEY= "SAMLEncryptedProperties";

    private static final Logger LOG = Logger.getLogger(SamlManager.class);

    public enum Key {
        Certificate, // X.509 Certificate
        IdPSsoUrl, // IdP SSO URL
        IssuerUrl, //Issuer URL
        SamlRequestParamName, //SAML Provider specific Request Parameter Name
        SamlResponseParamName; //SAML Provider specific Response Parameter Name

        public String getDefault(){return "";}
    }

    public static URLHelper getLoginURL()
    {
        try
        {
            return new URLHelper(getSamlUrl());
        }
        catch (URISyntaxException e)
        {
            throw new RuntimeException("Exception making SAML request", e);
        }
    }

    private static String getSamlUrl()
    {
        // the appSettings object contain application specific settings used by the SAML library
        AppSettings appSettings = new AppSettings();
        // set the URL of the consume.jsp (or similar) file for this app. The SAML Response will be posted to this URL
        appSettings.setAssertionConsumerServiceUrl(SamlController.getValidateURL().getURIString());
        // set the issuer of the authentication request. This would usually be the URL of the issuing web application

        String issuerUrl = getIssuerUrl();
        appSettings.setIssuer(issuerUrl == null ? ActionURL.getBaseServerURL() : issuerUrl);
        
        // the accSettings object contains settings specific to the users account.
        // At this point, your application must have identified the users origin
        AccountSettings accSettings = new AccountSettings();

        // The URL at the Identity Provider where to the authentication request should be sent
        accSettings.setIdpSsoTargetUrl(getIdPSsoUrl());

        // Generate an AuthRequest and send it to the identity provider
        AuthRequest authReq = new AuthRequest(appSettings, accSettings);

        // Omitting setting "relaystate" parameter

        try
        {
            return authReq.getSSOurl();
        }
        catch (IOException | XMLStreamException e)
        {
            throw new RuntimeException("Exception constructing SAML request", e);
        }
    }

    static String getUserFromSamlResponse(HttpServletRequest request)
    {
        String certificateS = getCertificate();

        // user account specific settings. Import the certificate here
        AccountSettings accountSettings = new AccountSettings();
        accountSettings.setCertificate(certificateS);

        try
        {
            Response samlResponse = new Response(accountSettings);

            samlResponse.loadXmlFromBase64(request.getParameter(SAML_RESPONSE_PARAMETER));
            samlResponse.setDestinationUrl(request.getRequestURL().toString());

            if (samlResponse.isValid())
            {
                return samlResponse.getNameId();
            }
            else
                return null; // TODO: Log an invalid SAML login attempt?
        }
        catch (Exception e)
        {
            LOG.error("Exception processing SAML response", e); // TODO: Proper exception handling
            return null;
        }
    }

    public static void saveCertificate(byte[] certBytes)
    {
        PropertyManager.PropertyMap map = PropertyManager.getEncryptedStore().getWritableProperties(SAML_PROPERTIES_ENCRYPTED_CATEGORY_KEY, true);
        String cert = new String(certBytes);
        map.put(Key.Certificate.toString(), cert);
        map.save();
    }

    public static void saveCertificate(String certificate)
    {
        PropertyManager.PropertyMap map = PropertyManager.getEncryptedStore().getWritableProperties(SAML_PROPERTIES_ENCRYPTED_CATEGORY_KEY, true);
        map.put(Key.Certificate.toString(), certificate);
        map.save();
    }

    public static void saveProperties(SamlController.Config config)
    {
        PropertyManager.PropertyMap map = PropertyManager.getNormalStore().getWritableProperties(SAML_PROPERTIES_NORMAL_CATEGORY_KEY, true);
        map.clear();
        map.put(Key.IdPSsoUrl.toString(), config.getIdPSsoUrl());
        map.put(Key.IssuerUrl.toString(), config.getIssuerUrl());
        map.put(Key.SamlRequestParamName.toString(), config.getRequestParamName());
        map.put(Key.SamlResponseParamName.toString(), config.getResponseParamName());
        map.save();
    }

    private static String getProperty(Key key, boolean encrypted)
    {
        Map<String, String> props = getProperties(encrypted);

        String value = props.get(key.toString());
        if (StringUtils.isBlank(value))
            value = key.getDefault();

        return value;
    }

    private static Map<String, String> getProperties(boolean encrypted)
    {
        if(encrypted)
            return PropertyManager.getEncryptedStore().getProperties(SAML_PROPERTIES_ENCRYPTED_CATEGORY_KEY);

        return PropertyManager.getNormalStore().getProperties(SAML_PROPERTIES_NORMAL_CATEGORY_KEY);
    }

    protected static String getIdPSsoUrl()
    {
        return getProperty(Key.IdPSsoUrl, false);
    }

    protected static String getIssuerUrl()
    {
        return getProperty(Key.IssuerUrl, false);
    }

    protected static String getSamlRequestParamName()
    {
        return getProperty(Key.SamlRequestParamName, false);
    }

    protected static String getSamlResponseParamName()
    {
        return getProperty(Key.SamlResponseParamName, false);
    }

    protected static String getCertificate()
    {
        return getProperty(Key.Certificate, true);
    }
}
