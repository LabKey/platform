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

package org.labkey.api.admin;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.services.ServiceRegistry;

import java.util.List;

/**
 * User: emilyz
 * Date: Jul 16, 2018
 */
public interface AdminConsoleService
{
    @Nullable
    static AdminConsoleService get()
    {
        return ServiceRegistry.get().getService(AdminConsoleService.class);
    }

    static void setInstance(AdminConsoleService impl)
    {
        ServiceRegistry.get().registerService(AdminConsoleService.class, impl);
    }

    void registerAdminConsoleHeaderProvider(AdminConsoleHeaderLinkProvider provider);

    @NotNull List<AdminConsoleHeaderLinkProvider> getAdminConsoleHeaderProviders();
}
