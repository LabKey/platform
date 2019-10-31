/*
 * Copyright (c) 2016 LabKey Corporation
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
package org.labkey.devtools.authentication;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.security.AuthenticationConfiguration;
import org.labkey.api.security.AuthenticationProvider.SSOAuthenticationProvider;
import org.labkey.api.security.ConfigurationSettings;
import org.labkey.api.view.ActionURL;
import org.labkey.devtools.authentication.TestSsoController.TestSsoConfigureForm;

/**
 * Created by adam on 6/5/2016.
 */
public class TestSsoProvider implements SSOAuthenticationProvider
{
    public static final String NAME = "TestSSO";
    static final String SET_KEY = "TestSsoAuthenticationProperties";

    @Override
    public AuthenticationConfiguration getAuthenticationConfiguration(@NotNull ConfigurationSettings cs)
    {
        return new TestSsoConfiguration(this, cs.getStandardSettings());
    }

    @Nullable
    @Override
    public ActionURL getConfigurationLink()
    {
        return getConfigurationLink(null);
    }

    @Override
    public @Nullable ActionURL getConfigurationLink(@Nullable Integer rowId)
    {
        return TestSsoController.getConfigureURL(rowId);
    }

    @NotNull
    @Override
    public String getName()
    {
        return NAME;
    }

    @NotNull
    @Override
    public String getDescription()
    {
        return "A trivial, insecure SSO authentication provider (for test purposes only)";
    }

    @Override
    public @Nullable TestSsoConfigureForm getFormFromOldConfiguration(boolean active, boolean hasLogos)
    {
        return active || hasLogos ? new TestSsoConfigureForm() : null;
    }
}
