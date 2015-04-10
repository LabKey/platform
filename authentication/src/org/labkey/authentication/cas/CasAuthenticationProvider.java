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
package org.labkey.authentication.cas;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.security.AuthenticationProvider.SSOAuthenticationProvider;
import org.labkey.api.security.ValidEmail;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Created by adam on 3/29/2015.
 */
public class CasAuthenticationProvider implements SSOAuthenticationProvider
{
    private static final CasAuthenticationProvider INSTANCE = new CasAuthenticationProvider();

    static final String NAME = "CAS";

    private CasAuthenticationProvider()
    {
    }

    public static CasAuthenticationProvider getInstance()
    {
        return INSTANCE;
    }

    @Override
    public AuthenticationResponse authenticate(HttpServletRequest request, HttpServletResponse response, URLHelper returnURL) throws ValidEmail.InvalidEmailException
    {
        return null;
    }

    @Override
    public URLHelper getURL()
    {
        return CasManager.getInstance().getLoginURL();
    }

    @Nullable
    @Override
    public ActionURL getConfigurationLink()
    {
        return CasController.getConfigureURL();
    }

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    public String getDescription()
    {
        return "Apereo Central Authentication Service (CAS)";
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
