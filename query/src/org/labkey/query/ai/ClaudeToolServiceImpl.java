package org.labkey.query.ai;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.module.Module;
import org.labkey.api.query.ai.ClaudeTool;
import org.labkey.api.query.ai.ClaudeToolService;
import org.labkey.api.util.Pair;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

public class ClaudeToolServiceImpl implements ClaudeToolService
{
    private final CopyOnWriteArrayList<Pair<Module, ClaudeTool>> _tools = new CopyOnWriteArrayList<>();

    @Override
    public void registerTool(@NotNull Module owner, @NotNull ClaudeTool tool)
    {
        _tools.add(new Pair<>(owner, tool));
    }

    @Override
    @NotNull
    public List<ClaudeTool> getTools(@NotNull Container container)
    {
        Set<Module> activeModules = container.getActiveModules();
        return _tools.stream()
            .filter(pair -> activeModules.contains(pair.first))
            .map(pair -> pair.second)
            .toList();
    }
}
