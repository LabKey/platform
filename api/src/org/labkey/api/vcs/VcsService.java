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
package org.labkey.api.vcs;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.services.ServiceRegistry;

import java.io.File;
import java.util.Objects;

public interface VcsService
{
    static @NotNull VcsService get()
    {
        return Objects.requireNonNull(ServiceRegistry.get().getService(VcsService.class));
    }

    static void setInstance(VcsService instance)
    {
        ServiceRegistry.get().registerService(VcsService.class, instance);
    }

    /**
     * Return the appropriate Vcs implementation if the directory is under version control
     * @param directory Directory to test
     * @return The corresponding Vcs implementation or null
     */
    @Nullable Vcs getVcs(File directory);
}
