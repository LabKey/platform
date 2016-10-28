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
package org.labkey.core.authentication.test;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.security.AuthenticationManager.LinkFactory;
import org.labkey.api.security.AuthenticationProvider.SSOAuthenticationProvider;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;

/**
 * Created by adam on 6/5/2016.
 */
public class TestSsoProvider implements SSOAuthenticationProvider
{
    static final String NAME = "TestSSO";

    private final LinkFactory _linkFactory = new LinkFactory(this);

    @Override
    public URLHelper getURL(String secret)
    {
        return new ActionURL(TestSsoController.TestSsoAction.class, ContainerManager.getRoot());
    }

    @Override
    public LinkFactory getLinkFactory()
    {
        return _linkFactory;
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
        return NAME;
    }

    @NotNull
    @Override
    public String getDescription()
    {
        return "A trivial, insecure SSO authentication provider (for test purposes only)";
    }
}
