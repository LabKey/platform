/*
 * Copyright (c) 2019-2026 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.module.CodeOnlyModule;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.security.AuthenticationManager;
import org.labkey.api.settings.OptionalFeatureService;
import org.labkey.api.util.JspTestCase;
import org.labkey.api.view.WebPartFactory;
import org.labkey.devtools.authentication.TestSecondaryController;
import org.labkey.devtools.authentication.TestSecondaryProvider;
import org.labkey.devtools.authentication.TestSsoController;
import org.labkey.devtools.authentication.TestSsoProvider;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

public class DevtoolsModule extends CodeOnlyModule
{
    static final String NAME = "DeveloperTools";

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

        OptionalFeatureService.get().addExperimentalFeatureFlag(
            Domain.EXPERIMENTAL_FUZZ_STORAGE_NAME,
            "'fuzz' name of database columns used to back domain properties",
            "This is dev/test feature and not intended for any production usage.",
            false,
            true
        );
    }

    @Override
    public void doStartup(ModuleContext moduleContext)
    {
    }

    @Override
    public @NotNull Collection<Supplier<Class<?>>> getIntegrationTestFactories()
    {
        List<Supplier<Class<?>>> list = new ArrayList<>(super.getIntegrationTestFactories());
        list.add(new JspTestCase("/org/labkey/devtools/test/JspTestCaseTest.jsp"));
        return list;
    }

    @Override
    public @NotNull Set<Class<?>> getIntegrationTests()
    {
        return Set.of(
            TestController.JsonInputLimitTest.class,
            ToolsController.TestCase.class
        );
    }
}