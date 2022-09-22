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
import org.json.old.JSONArray;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.security.AuthenticationProvider.SSOAuthenticationProvider;
import org.labkey.api.security.ConfigurationSettings;
import org.labkey.api.security.SettingsField;
import org.labkey.api.view.ActionURL;
import org.labkey.devtools.authentication.TestSsoController.TestSsoSaveConfigurationAction;

/**
 * Created by adam on 6/5/2016.
 */
public class TestSsoProvider implements SSOAuthenticationProvider<TestSsoConfiguration>
{
    public static final String NAME = "TestSSO";

    @Override
    public TestSsoConfiguration getAuthenticationConfiguration(@NotNull ConfigurationSettings cs)
    {
        return new TestSsoConfiguration(this, cs.getStandardSettings(), cs.getProperties());
    }

    @Override
    public @NotNull ActionURL getSaveLink()
    {
        return new ActionURL(TestSsoSaveConfigurationAction.class, ContainerManager.getRoot());
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
    public @NotNull JSONArray getSettingsFields()
    {
        return new JSONArray()
            .put(SettingsField.of("autoRedirect", SettingsField.FieldType.checkbox, "Default to this TestSSO configuration", "Redirects the login page directly to the TestSSO page instead of requiring the user to click on a logo.", false, false))
            .put(SettingsField.getStandardDomainField());
    }
}
