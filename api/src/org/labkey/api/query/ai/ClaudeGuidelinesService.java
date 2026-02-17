package org.labkey.api.query.ai;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.module.Module;
import org.labkey.api.services.ServiceRegistry;

public interface ClaudeGuidelinesService
{
    static ClaudeGuidelinesService get()
    {
        return ServiceRegistry.get().getService(ClaudeGuidelinesService.class);
    }

    static void setInstance(ClaudeGuidelinesService service)
    {
        ServiceRegistry.get().registerService(ClaudeGuidelinesService.class, service);
    }

    void registerGuidelines(@NotNull Module owner, @NotNull String resourcePath);

    @NotNull
    String getGuidelines(@NotNull Container container);
}
