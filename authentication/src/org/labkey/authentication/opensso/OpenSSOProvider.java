/*
 * Copyright (c) 2007-2015 LabKey Corporation
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

package org.labkey.authentication.opensso;

import org.apache.log4j.Logger;
import org.labkey.api.security.AuthenticationManager;
import org.labkey.api.security.AuthenticationProvider;
import org.labkey.api.util.URLHelper;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.view.ActionURL;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

/**
 * User: adam
 * Date: Oct 10, 2007
 * Time: 7:01:10 PM
 */

@Deprecated
// No longer supported, no longer works. However, this code is a useful model for future SSO providers (e.g., OpenID).
public class OpenSSOProvider implements AuthenticationProvider.SSOAuthenticationProvider
{
    private static final Logger _log = Logger.getLogger(OpenSSOProvider.class);
    public static final String NAME = "OpenSSO";

    public boolean isPermanent()
    {
        return false;
    }

    public void activate()
    {
        try
        {
            OpenSSOManager.get().activate();
        }
        catch (IOException e)
        {
            throw new UnexpectedException(e);
        }
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
        return "Connects to OpenSSO, an open-source authentication server, enabling single sign-on solutions with one or more other web sites";
    }

    public ActionURL getConfigurationLink()
    {
        return OpenSSOController.getCurrentSettingsURL();
    }

//        String referrerPrefix = OpenSSOManager.get().getReferrerPrefix();
//
//        if (null != referrerPrefix)
//        {
//            // Note to developers: this is difficult to test/debug because (in my experience) "referer" is null when linking
//            // to http://localhost.  Use an actual domain name to test this code (e.g., http://dhcp155191.fhcrc.org).
//            String referer = request.getHeader("Referer");
//
//            if (null != referer && referer.startsWith(referrerPrefix))
//            {
//                AuthenticationManager.LinkFactory factory = AuthenticationManager.getLinkFactory(NAME);
//
//                if (null != factory && null != returnURL)
//                {
//                    ActionURL url = factory.getURL(returnURL);
//                    throw new RedirectException(url);
//                }
//            }
//        }
//
//        return AuthenticationResponse.createFailureResponse(FailureReason.notApplicable);     // Rely on login screen to present link to OpenSSO

    @Override
    public URLHelper getURL(String secret)
    {
        return null;
    }


    @Override
    public AuthenticationManager.LinkFactory getLinkFactory()
    {
        return null;
    }

    public void logout(HttpServletRequest request)
    {
//        try
//        {
//            SSOTokenManager manager = SSOTokenManager.getInstance();
//            SSOToken token = manager.createSSOToken(request);
//            manager.destroyToken(token);
//        }
//        catch (SSOException e)
//        {
//            // Ignore
//        }
    }
}
