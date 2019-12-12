/*
 * Copyright (c) 2017 LabKey Corporation
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
package org.labkey;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.TableViewFormTestCase;
import org.labkey.api.exp.property.DomainTemplateGroup;
import org.labkey.api.module.CodeOnlyModule;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.script.RhinoService;
import org.labkey.api.view.WebPartFactory;
import org.labkey.api.webdav.ModuleStaticResolverImpl;
import org.labkey.api.webdav.WebdavResolverImpl;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

/**
 * Created by susanh on 1/9/17.
 */
public class InternalModule extends CodeOnlyModule
{
    @Override
    protected void init()
    {
    }

    @NotNull
    @Override
    protected Collection<? extends WebPartFactory> createWebPartFactories()
    {
        return Collections.emptyList();
    }

    @Override
    protected void doStartup(ModuleContext moduleContext)
    {
    }

    @Override
    public @NotNull Set<Class> getIntegrationTests()
    {
        return Set.of(
            DomainTemplateGroup.TestCase.class,
            ModuleStaticResolverImpl.TestCase.class,
            RhinoService.TestCase.class,
            TableViewFormTestCase.class,
            WebdavResolverImpl.TestCase.class
        );
    }
}
