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
package org.labkey.authentication.test;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.security.AuthenticationProvider.SecondaryAuthenticationProvider;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;
import org.labkey.authentication.duo.DuoController;

import javax.servlet.http.HttpServletRequest;

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
        return DuoController.getTestSecondaryURL(c);
    }

    @Nullable
    @Override
    public ActionURL getConfigurationLink()
    {
        return null;
    }

    @Override
    public String getName()
    {
        return "Test Secondary Authentication";
    }

    @Override
    public String getDescription()
    {
        return "Adds an annoying secondary authentication requirement";
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

    @Override
    public boolean bypass()
    {
        return false;
    }
}
