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
package org.labkey.authentication.saml;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.security.AuthenticationProvider;
import org.labkey.api.security.ValidEmail;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.authentication.AuthenticationModule;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * User: tgaluhn
 * Date: 1/19/2015
 */
public class SamlProvider implements AuthenticationProvider.SSOAuthenticationProvider
{
    @Override
    public AuthenticationResponse authenticate(HttpServletRequest request, HttpServletResponse response, URLHelper returnURL) throws ValidEmail.InvalidEmailException
    {
        if (!AppProps.getInstance().isExperimentalFeatureEnabled(AuthenticationModule.EXPERIMENTAL_SAML_SERVICE_PROVIDER))
            return AuthenticationResponse.createFailureResponse(FailureReason.notApplicable);

        if (StringUtils.isBlank(request.getParameter(SamlManager.SAML_RESPONSE_PARAMETER))) // First time through this method
        {
            if (SamlManager.sendSamlRequest(request, response))
                return AuthenticationResponse.createFailureResponse(FailureReason.notApplicable); // TODO: What's the correct return here?
            else
                return AuthenticationResponse.createFailureResponse(FailureReason.configurationError);
        }
        else // Second time, hopefully POST'd by IdP
        {
            String email = SamlManager.getUserFromSamlResponse(request);
            if (StringUtils.isNotBlank(email))
                return AuthenticationResponse.createSuccessResponse(new ValidEmail(email));
            else
                return AuthenticationResponse.createFailureResponse(FailureReason.badCredentials); // TODO: or assume config error?
        }
    }

    @Nullable
    @Override
    public ActionURL getConfigurationLink()
    {
        return new ActionURL(); // No config page yet
    }

    @Override
    public URLHelper getURL()
    {
        return null;  // TODO!!
    }

    @Override
    public String getName()
    {
        return "SAML";
    }

    @Override
    public String getDescription()
    {
        return "Acts as a service provider (SP) to authenticate against a SAML 2.0 Identity Provider (IdP)";
    }

    @Override
    public void logout(HttpServletRequest request)
    {
    }

    @Override
    public void activate() throws Exception
    {
    }

    @Override
    public void deactivate() throws Exception
    {
    }

    @Override
    public boolean isPermanent()
    {
        return false;
    }
}
