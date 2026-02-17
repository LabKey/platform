package org.labkey.api.query.ai;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.module.Module;
import org.labkey.api.services.ServiceRegistry;

import java.util.List;

public interface ClaudeToolService
{
    static ClaudeToolService get()
    {
        return ServiceRegistry.get().getService(ClaudeToolService.class);
    }

    static void setInstance(ClaudeToolService service)
    {
        ServiceRegistry.get().registerService(ClaudeToolService.class, service);
    }

    void registerTool(@NotNull Module owner, @NotNull ClaudeTool tool);

    @NotNull
    List<ClaudeTool> getTools(@NotNull Container container);
}
