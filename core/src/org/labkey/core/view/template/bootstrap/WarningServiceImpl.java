/*
 * Copyright (c) 2018 LabKey Corporation
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
package org.labkey.core.view.template.bootstrap;

import org.labkey.api.view.template.WarningProvider;
import org.labkey.api.view.template.WarningService;

import java.util.Collection;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class WarningServiceImpl implements WarningService
{
    private final Collection<WarningProvider> _providers = new CopyOnWriteArrayList<>();

    @Override
    public void register(WarningProvider provider)
    {
        _providers.add(provider);
    }

    @Override
    public void forEachProvider(Consumer<WarningProvider> consumer)
    {
        _providers.forEach(consumer);
    }
}
