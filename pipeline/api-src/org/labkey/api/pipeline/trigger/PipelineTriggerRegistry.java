package org.labkey.api.pipeline.trigger;

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

    <C extends PipelineTriggerConfig> Collection<C> getConfigs(@Nullable Container c, @Nullable PipelineTriggerType<C> type, @Nullable String name);
}
