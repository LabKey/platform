/*
 * Copyright (c) 2019 LabKey Corporation
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

package org.labkey.devtools;

import org.apache.commons.collections4.Factory;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.module.CodeOnlyModule;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.notification.NotificationMenuView;
import org.labkey.api.security.AuthenticationManager;
import org.labkey.api.settings.AdminConsole;
import org.labkey.api.util.JspTestCase;
import org.labkey.api.view.WebPartFactory;
import org.labkey.devtools.authentication.TestSecondaryController;
import org.labkey.devtools.authentication.TestSecondaryProvider;
import org.labkey.devtools.authentication.TestSsoController;
import org.labkey.devtools.authentication.TestSsoProvider;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class DevtoolsModule extends CodeOnlyModule
{
    static final String NAME = "DeveloperTools";

    public static final String EXPERIMENTAL_DEV_DISABLE_UNSAFE_INLINE = "experimental-unsafe-inline";

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    @NotNull
    protected Collection<WebPartFactory> createWebPartFactories()
    {
        return Collections.emptyList();
    }

    @Override
    protected void init()
    {
        addController(TestController.NAME, TestController.class);
        addController(ToolsController.NAME, ToolsController.class);

        addController("testsecondary", TestSecondaryController.class);
        AuthenticationManager.registerProvider(new TestSecondaryProvider());
        addController("testsso", TestSsoController.class);
        AuthenticationManager.registerProvider(new TestSsoProvider());

        AdminConsole.addExperimentalFeatureFlag(EXPERIMENTAL_DEV_DISABLE_UNSAFE_INLINE, "Disable unsafe-line",
                "For development purposes only, setting this experimental flag will disable unsafe-line javascript in the browser see https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Content-Security-Policy/script-src.", false);
    }

    @Override
    public void doStartup(ModuleContext moduleContext)
    {
        // add a container listener so we'll know when our container is deleted:
        ContainerManager.addContainerListener(new DevtoolsContainerListener());
    }

    @Override
    @NotNull
    public Collection<String> getSummary(Container c)
    {
        return Collections.emptyList();
    }

    @Override
    public @NotNull List<Factory<Class<?>>> getIntegrationTestFactories()
    {
        return Collections.singletonList(new JspTestCase("/org/labkey/devtools/test/JspTestCaseTest.jsp"));
    }
}