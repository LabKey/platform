/*
 * Copyright (c) 2015 LabKey Corporation
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

package org.labkey.authentication.oauth;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.StatusLine;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.data.PropertyStore;
import org.labkey.api.security.AuthenticationProvider.SSOAuthenticationProvider;
import org.labkey.api.security.ValidEmail;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.CSRFException;
import org.labkey.api.util.CSRFUtil;
import org.labkey.api.util.ConfigurationException;
import org.labkey.api.util.SessionHelper;
import org.labkey.api.util.URLHelper;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.view.ActionURL;
import org.labkey.authentication.AuthenticationModule;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;


public class GoogleOAuthProvider implements SSOAuthenticationProvider
{
    public static final String NAME = "Google";


    // TODO AppProps()
    private static String getClientId()
    {
        PropertyStore store = PropertyManager.getEncryptedStore();
        PropertyManager.PropertyMap map = store.getProperties(ContainerManager.getRoot(), GoogleOAuthProvider.class.getName());
        return map.get("client_id");
    }

    private static String getClientSecret()
    {
        PropertyStore store = PropertyManager.getEncryptedStore();
        PropertyManager.PropertyMap map = store.getProperties(ContainerManager.getRoot(), GoogleOAuthProvider.class.getName());
        return map.get("client_secret");
    }


    public boolean isPermanent()
    {
        return false;
    }

    public void activate()
    {
    }

    public void deactivate()
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
        return new ActionURL(OAuthController.ConfigureAction.class, ContainerManager.getRoot());
    }


    /* NOTE: the Google+ signin button could link directly to this URL, rather than redirecting through oauth/redirect.view */
    public static String getAuthenticationUrl(String state, @NotNull URLHelper returnUrl) throws URISyntaxException
    {
        URLHelper toGoogle = new URLHelper("https://accounts.google.com/o/oauth2/auth");
        toGoogle.addParameter("response_type", "code");
        toGoogle.addParameter("client_id", getClientId());
        toGoogle.addParameter("redirect_uri", returnUrl.getURIString());
        toGoogle.addParameter("scope", "openid email");
        toGoogle.addParameter("state", state);
        toGoogle.addParameter("access_type", "online");
        toGoogle.addParameter("openid.realm", "");
        toGoogle.addParameter("approval_prompt", "auto");
        // TODO login_hint if user selected "remember email"
        return toGoogle.getURIString();
    }


    private static class GoogleAuthBean
    {
        // auth
        String code;
        String state;
        String session_state;
        String authuser;
        String error;
        // token
        String access_token;
        String id_token;
        Integer expires_in;
        String token_type;
        //people
    }


    public static AuthenticationResponse authenticate(HttpServletRequest request, HttpServletResponse response) throws ValidEmail.InvalidEmailException
    {
        if (!AppProps.getInstance().isExperimentalFeatureEnabled(AuthenticationModule.EXPERIMENTAL_OPENID_GOOGLE))
            return AuthenticationResponse.createFailureResponse(FailureReason.notApplicable);

        GoogleAuthBean bean = new GoogleAuthBean();
        bean.error = request.getParameter("error");
        bean.code = request.getParameter("code");
        bean.state = request.getParameter("state");
        bean.session_state = request.getParameter("session_state");
        bean.authuser = request.getParameter("authuser");

        // TODO good error reporting
        if (StringUtils.isNotEmpty(bean.error))
            return AuthenticationResponse.createFailureResponse(FailureReason.badCredentials,null);

        if (StringUtils.isAnyEmpty(bean.code, bean.state, bean.session_state, bean.authuser))
            return AuthenticationResponse.createFailureResponse(FailureReason.notApplicable);

        if (!StringUtils.equals(bean.state,CSRFUtil.getExpectedToken(request, response)))
            throw new CSRFException();

        SessionHelper.setAttribute(request, GoogleAuthBean.class.getName(), bean, true);

        // Turn "code" into "access_token"
        try
        {
            //String tokenAPI = "https://www.googleapis.com/oauth2/v3/token";
            String tokenAPI = "https://accounts.google.com/o/oauth2/token";
            List<NameValuePair> nvps = new ArrayList<>();
            nvps.add(new BasicNameValuePair("code", bean.code));
            nvps.add(new BasicNameValuePair("client_id", getClientId()));
            nvps.add(new BasicNameValuePair("client_secret", getClientSecret()));
            nvps.add(new BasicNameValuePair("redirect_uri", new ActionURL(OAuthController.ReturnAction.class, ContainerManager.getRoot()).getURIString()));
            nvps.add(new BasicNameValuePair("grant_type", "authorization_code"));

            HttpPost post = new HttpPost(tokenAPI);
            post.setEntity(new UrlEncodedFormEntity(nvps));

            HttpResponse tokenResponse =  HttpClientBuilder.create().build().execute(post, HttpClientContext.create());
            StatusLine status = tokenResponse.getStatusLine();
            if (status.getStatusCode() == HttpStatus.SC_OK)
            {
                String json =  new BasicResponseHandler().handleResponse(tokenResponse);
                JSONObject result = new JSONObject(new JSONTokener(json));
                bean.access_token = (String)result.get("access_token");
                bean.id_token = (String)result.get("id_token");
                bean.token_type = (String)result.get("token_type");
                bean.expires_in = (Integer)result.get("expires_in");
                System.err.println(json);
            }
            else
            {
                // TODO good error reporting
                return AuthenticationResponse.createFailureResponse(FailureReason.badCredentials,null);
            }
        }
        catch (IOException x)
        {
            throw new UnexpectedException(x);
        }

        // get uer information

        //GET https://www.googleapis.com/plus/v1/people/me?access_token={bean.access_token}
        try
        {
            String peopleApi = "https://www.googleapis.com/plus/v1/people/me?access_token=" + bean.access_token;
            HttpGet get = new HttpGet(peopleApi);
            HttpResponse tokenResponse =  HttpClientBuilder.create().build().execute(get, HttpClientContext.create());
            StatusLine status = tokenResponse.getStatusLine();
            if (status.getStatusCode() == HttpStatus.SC_OK)
            {
                String json =  new BasicResponseHandler().handleResponse(tokenResponse);
                JSONObject result = new JSONObject(new JSONTokener(json));
                JSONArray array = (JSONArray)result.get("emails");
                JSONObject emailObj = (JSONObject)array.get(0);
                String email = (String)emailObj.get("value");
                // TODO handle multiple emails?
                return AuthenticationResponse.createSuccessResponse(new ValidEmail(email));
            }
            else
            {
                // TODO good error reporting
                return AuthenticationResponse.createFailureResponse(FailureReason.badCredentials,null);
            }
        }
        catch (IOException x)
        {
            throw new UnexpectedException(x);
        }
    }

    @Override
    public URLHelper getURL(String secret)
    {
        try
        {
            URLHelper return_to = new ActionURL(OAuthController.ReturnAction.class, ContainerManager.getRoot());
            String redirect = GoogleOAuthProvider.getAuthenticationUrl(secret, return_to);
            return new URLHelper(redirect);
        }
        catch (URISyntaxException x)
        {
            throw new ConfigurationException("Could not parse SSO provider URL", x);
        }
    }


    public void logout(HttpServletRequest request)
    {
        // TODO
        // https://accounts.google.com/o/oauth2/revoke
    }
}
