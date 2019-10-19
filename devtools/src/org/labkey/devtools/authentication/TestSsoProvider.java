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
import org.labkey.api.data.PropertyManager;
import org.labkey.api.security.AuthenticationConfiguration;
import org.labkey.api.security.AuthenticationProvider.SSOAuthenticationProvider;
import org.labkey.api.view.ActionURL;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by adam on 6/5/2016.
 */
public class TestSsoProvider implements SSOAuthenticationProvider
{
    public static final String NAME = "TestSSO";
    static final String SET_KEY = "TestSsoAuthenticationProperties";

    @Override
    public AuthenticationConfiguration getAuthenticationConfiguration(boolean active)
    {
        String key = SET_KEY; // TODO: TestSSOConfigurationProperties?
        Map<String, String> m = PropertyManager.getProperties(key);
        Map<String, String> map = new HashMap<>(m);
        map.put("Provider", NAME);
        map.put("Enabled", Boolean.toString(active));
        map.put("Name", NAME);

        return new TestSsoConfiguration(key, this, map);
    }

    @Nullable
    @Override
    public ActionURL getConfigurationLink()
    {
        return TestSsoController.getConfigureURL(SET_KEY);
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
    public boolean isConfigurationAware()
    {
        return true;
    }
}
