/*
 * Copyright (c) 2008-2015 LabKey Corporation
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

package org.labkey.authentication;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.module.DefaultModule;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.security.AuthenticationManager;
import org.labkey.api.security.AuthenticationManager.Priority;
import org.labkey.api.settings.AdminConsole;
import org.labkey.api.settings.AppProps;
import org.labkey.api.view.WebPartFactory;
import org.labkey.authentication.cas.CasAuthenticationProvider;
import org.labkey.authentication.cas.CasController;
import org.labkey.authentication.duo.DuoController;
import org.labkey.authentication.duo.DuoProvider;
import org.labkey.authentication.ldap.LdapAuthenticationProvider;
import org.labkey.authentication.ldap.LdapController;
import org.labkey.authentication.oauth.GoogleOAuthProvider;
import org.labkey.authentication.oauth.OAuthController;
import org.labkey.authentication.saml.SamlController;
import org.labkey.authentication.saml.SamlProvider;
import org.labkey.authentication.test.TestSecondaryController;
import org.labkey.authentication.test.TestSecondaryProvider;

import java.util.Collection;
import java.util.Collections;

public class AuthenticationModule extends DefaultModule
{
    private static Logger _log = Logger.getLogger(AuthenticationModule.class);
    public static final String EXPERIMENTAL_OPENID_GOOGLE = "experimental-openid-google";
    public static final String EXPERIMENTAL_SAML_SERVICE_PROVIDER = "experimental-saml-sp";

    public String getName()
    {
        return "Authentication";
    }

    public double getVersion()
    {
        return 15.20;
    }

    @NotNull
    protected Collection<WebPartFactory> createWebPartFactories()
    {
        return Collections.emptyList();
    }

    public boolean hasScripts()
    {
        return false;
    }

    protected void init()
    {
        addController("ldap", LdapController.class);
        addController("cas", CasController.class);
        addController("oauth", OAuthController.class);
        addController("saml", SamlController.class);
        AdminConsole.addExperimentalFeatureFlag(EXPERIMENTAL_OPENID_GOOGLE, "Login using your Google account", "Authenticate using Google and OAuth 2.0.", true);

        AuthenticationManager.registerProvider(new LdapAuthenticationProvider(), Priority.High);

        if (AppProps.getInstance().isExperimentalFeatureEnabled(EXPERIMENTAL_OPENID_GOOGLE))
            AuthenticationManager.registerProvider(new GoogleOAuthProvider(), Priority.Low);

        AdminConsole.addExperimentalFeatureFlag(EXPERIMENTAL_SAML_SERVICE_PROVIDER, "Login using SAML", "Authenticate using a SAML Identity Provider.", true);
        if (AppProps.getInstance().isExperimentalFeatureEnabled(EXPERIMENTAL_SAML_SERVICE_PROVIDER))
            AuthenticationManager.registerProvider(new SamlProvider(), Priority.Low);

        AuthenticationManager.registerProvider(CasAuthenticationProvider.getInstance(), Priority.Low);

        addController("duo", DuoController.class);
        AuthenticationManager.registerProvider(new DuoProvider(), Priority.Low);

        if (AppProps.getInstance().isDevMode())
        {
            addController("testsecondary", TestSecondaryController.class);
            AuthenticationManager.registerProvider(new TestSecondaryProvider(), Priority.Low);
        }
    }

    public void doStartup(ModuleContext moduleContext)
    {
    }

    public TabDisplayMode getTabDisplayMode()
    {
        return TabDisplayMode.DISPLAY_NEVER;
    }
}