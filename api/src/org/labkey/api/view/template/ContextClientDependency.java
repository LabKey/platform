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
package org.labkey.api.view.template;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.module.Module;
import org.labkey.clientLibrary.xml.ModeTypeEnum;

import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Handles a module context reference
 */
public class ContextClientDependency extends ClientDependency
{
    private final Module _module;

    protected ContextClientDependency(@NotNull Module m, ModeTypeEnum.Enum mode)
    {
        super(TYPE.context, mode);
        _module = m;
    }

    @Override
    protected void init()
    {
    }

    @NotNull
    @Override
    protected Stream<Supplier<ClientDependency>> getDependencyStream(Container c)
    {
        return _module.getClientDependencies(c).stream();
    }

    @NotNull
    @Override
    public Set<Module> getRequiredModuleContexts(Container c)
    {
        Set<Module> ret = super.getRequiredModuleContexts(c);
        ret.add(_module);

        return ret;
    }

    @Override
    protected String getUniqueKey()
    {
        return getCacheKey("moduleContext|" + _module.toString(), _mode);
    }

    @Override
    public String getScriptString()
    {
        return _module.getName() + "." + _primaryType.name();
    }
}
