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
package org.labkey.api.pipeline.trigger;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.services.ServiceRegistry;

import java.util.Collection;

public interface PipelineTriggerRegistry
{
    static PipelineTriggerRegistry get()
    {
        return ServiceRegistry.get(PipelineTriggerRegistry.class);
    }

    static void setInstance(PipelineTriggerRegistry instance)
    {
        ServiceRegistry.get().registerService(PipelineTriggerRegistry.class, instance);
    }

    void register(PipelineTriggerType type);

    Collection<PipelineTriggerType> getTypes();

    @Nullable
    PipelineTriggerType getTypeByName(String name);

    <C extends PipelineTriggerConfig> Collection<C> getConfigs(@Nullable Container c, @Nullable PipelineTriggerType<C> type, @Nullable String name, boolean enabledOnly);

    <C extends PipelineTriggerConfig> C getConfigByName(@NotNull Container c, String name);

    <C extends PipelineTriggerConfig> C getConfigById(int rowId);

    void updateConfigLastChecked(int rowId);
}
