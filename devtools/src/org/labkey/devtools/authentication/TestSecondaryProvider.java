/*
 * Copyright (c) 2016-2019 LabKey Corporation
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
import org.labkey.api.data.ContainerManager;
import org.labkey.api.security.AuthenticationProvider.SecondaryAuthenticationProvider;
import org.labkey.api.security.ConfigurationSettings;
import org.labkey.api.view.ActionURL;
import org.labkey.devtools.authentication.TestSecondaryController.TestSecondarySaveConfigurationAction;

/**
 * User: adam
 * Date: 3/11/2015
 * Time: 7:45 AM
 */
public class TestSecondaryProvider implements SecondaryAuthenticationProvider<TestSecondaryConfiguration>
{
    public static final String NAME = "TestSecondary";

    @Override
    public TestSecondaryConfiguration getAuthenticationConfiguration(@NotNull ConfigurationSettings cs)
    {
        return new TestSecondaryConfiguration(this, cs.getStandardSettings());
    }

    @Override
    public @Nullable ActionURL getSaveLink()
    {
        return new ActionURL(TestSecondarySaveConfigurationAction.class, ContainerManager.getRoot());
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
        return "Adds a trivial, insecure secondary authentication requirement (for test purposes only)";
    }

    @Override
    public boolean bypass()
    {
        return false;
    }
}
