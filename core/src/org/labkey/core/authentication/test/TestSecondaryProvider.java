/*
 * Copyright (c) 2015-2017 LabKey Corporation
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
package org.labkey.core.authentication.test;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.security.AuthenticationProvider.SecondaryAuthenticationProvider;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;

/**
 * User: adam
 * Date: 3/11/2015
 * Time: 7:45 AM
 */
public class TestSecondaryProvider implements SecondaryAuthenticationProvider
{
    @Override
    public ActionURL getRedirectURL(User candidate, Container c)
    {
        return TestSecondaryController.getTestSecondaryURL(c);
    }

    @Nullable
    @Override
    public ActionURL getConfigurationLink()
    {
        return null;
    }

    @NotNull
    @Override
    public String getName()
    {
        return "Test Secondary Authentication";
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
