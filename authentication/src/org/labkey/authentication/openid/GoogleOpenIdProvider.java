/*
 * Copyright (c) 2014 LabKey Corporation
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

package org.labkey.authentication.openid;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.expressme.openid.Association;
import org.expressme.openid.Authentication;
import org.expressme.openid.Endpoint;
import org.expressme.openid.OpenIdManager;
import org.labkey.api.cache.Cache;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.security.*;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.RedirectException;
import org.labkey.authentication.AuthenticationModule;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.net.URISyntaxException;
import java.text.ParseException;
import java.text.SimpleDateFormat;


public class GoogleOpenIdProvider implements AuthenticationProvider.RequestAuthenticationProvider
{
    private static final Logger _log = Logger.getLogger(GoogleOpenIdProvider.class);
    public static final String NAME = "Google";
    static final String ATTR_MAC = GoogleOpenIdProvider.class.getName() + "#openid_mac";
    static final String ATTR_ALIAS = GoogleOpenIdProvider.class.getName() + "#openid_alias";


    public boolean isPermanent()
    {
        return false;
    }

    public void activate() throws Exception
    {
    }

    public void deactivate() throws Exception
    {
    }

    public String getName()
    {
        return NAME;
    }


    public String getDescription()
    {
        return "Login using Google account";
    }


    public ActionURL getConfigurationLink()
    {
        return null;
    }


    public static String getAuthenticationUrl(HttpServletRequest request, URLHelper returnUrl) throws URISyntaxException
    {
        OpenIdManager manager = getOpenIdManager(returnUrl);
        Endpoint endpoint = manager.lookupEndpoint("Google");
        Association association = manager.lookupAssociation(endpoint);
        request.getSession().setAttribute(GoogleOpenIdProvider.ATTR_MAC, association.getRawMacKey());
        request.getSession().setAttribute(GoogleOpenIdProvider.ATTR_ALIAS, endpoint.getAlias());
        String authenticateUrl = manager.getAuthenticationUrl(endpoint, association);
        return authenticateUrl;
    }



    @Override
    public AuthenticationResponse authenticate(HttpServletRequest request, HttpServletResponse response, URLHelper returnURL) throws ValidEmail.InvalidEmailException, RedirectException
    {
        if (!AppProps.getInstance().isExperimentalFeatureEnabled(AuthenticationModule.EXPERIMENTAL_OPENID_GOOGLE))
            return AuthenticationResponse.createFailureResponse(FailureReason.notApplicable);


        String nonce = request.getParameter("openid.response_nonce");
        if (StringUtils.isEmpty(nonce))
            return AuthenticationResponse.createFailureResponse(FailureReason.notApplicable);

        String error = checkNonce(request.getParameter("openid.response_nonce"));
        if (null != error)
            return AuthenticationResponse.createFailureResponse(FailureReason.badCredentials);
        byte[] mac_key = (byte[]) request.getSession().getAttribute(ATTR_MAC);
        String alias = (String) request.getSession().getAttribute(ATTR_ALIAS);
        OpenIdManager manager = getOpenIdManager(returnURL);
        Authentication authentication = manager.getAuthentication(request, mac_key, alias);
        //String identity = authentication.getIdentity();
        String email = authentication.getEmail();
        return AuthenticationResponse.createSuccessResponse(new ValidEmail(email));
    }


    public void logout(HttpServletRequest request)
    {
    }


    static OpenIdManager getOpenIdManager(URLHelper returnUrl)
    {
        if (null == returnUrl)
            returnUrl = AppProps.getInstance().getHomePageActionURL();
        LoginUrls urls = PageFlowUtil.urlProvider(LoginUrls.class);
        ActionURL loginUrl = urls.getLoginURL(returnUrl);
        loginUrl.setContainer(ContainerManager.getRoot());

        OpenIdManager manager = new OpenIdManager();
        manager.setRealm(AppProps.getInstance().getBaseServerUrl());
        manager.setReturnTo(loginUrl.getURIString());
        return manager;
    }


    static String checkNonce(String nonce)
    {
        // check response_nonce to prevent replay-attack:
        if (nonce == null || nonce.length() < 20)
            return "Nonce verification failed";
        long nonceTime;
        try
        {
            nonceTime = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ")
                    .parse(nonce.substring(0, 19) + "+0000")
                    .getTime();
        }
        catch (ParseException e)
        {
            return "Nonce verification failed: bad time";
        }
        long diff = System.currentTimeMillis() - nonceTime;
        if (diff < 0)
            diff = (-diff);
        if (diff > CacheManager.HOUR)
            return "Nonce verification failed: bad time";
        if (null != nonceCache.get(nonce))
            return "Nonce verification failed: been there done that";
        nonceCache.put(nonce,nonce);
        return null;
    }

    static final Cache<String,String> nonceCache = CacheManager.getCache(10000, CacheManager.UNLIMITED, "openid cache");
}
