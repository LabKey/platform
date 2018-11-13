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

package org.labkey.core.admin;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.admin.AdminConsoleHeaderLinkProvider;
import org.labkey.api.admin.AdminConsoleService;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * User: emilyz
 * Date: Jul 16, 2018
 */
public class AdminConsoleServiceImpl implements AdminConsoleService
{
    private final List<AdminConsoleHeaderLinkProvider> _providers = new CopyOnWriteArrayList<>();

    @Override
    public void registerAdminConsoleHeaderProvider(AdminConsoleHeaderLinkProvider provider)
    {
        _providers.add(provider);
    }

    @NotNull
    @Override
    public List<AdminConsoleHeaderLinkProvider> getAdminConsoleHeaderProviders()
    {
        return _providers;
    }
}
