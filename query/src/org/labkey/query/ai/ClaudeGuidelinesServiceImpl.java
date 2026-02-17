package org.labkey.query.ai;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.module.Module;
import org.labkey.api.query.ai.ClaudeGuidelinesService;
import org.labkey.api.resource.Resource;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.logging.LogHelper;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

public class ClaudeGuidelinesServiceImpl implements ClaudeGuidelinesService
{
    private static final Logger LOG = LogHelper.getLogger(ClaudeGuidelinesServiceImpl.class, "AI guidelines loading");

    private final CopyOnWriteArrayList<Pair<Module, String>> _guidelines = new CopyOnWriteArrayList<>();

    @Override
    public void registerGuidelines(@NotNull Module owner, @NotNull String resourcePath)
    {
        _guidelines.add(new Pair<>(owner, resourcePath));
    }

    @Override
    @NotNull
    public String getGuidelines(@NotNull Container container)
    {
        Set<Module> activeModules = container.getActiveModules();
        return _guidelines.stream()
            .filter(pair -> activeModules.contains(pair.first))
            .map(pair -> loadResource(pair.first, pair.second))
            .filter(content -> content != null && !content.isEmpty())
            .collect(Collectors.joining("\n\n"));
    }

    private String loadResource(Module module, String resourcePath)
    {
        Resource resource = module.getModuleResource(resourcePath);
        if (resource == null || !resource.exists())
        {
            LOG.warn("Guidelines resource '{}' not found in module '{}'", resourcePath, module.getName());
            return null;
        }
        try (InputStream is = resource.getInputStream())
        {
            return PageFlowUtil.getStreamContentsAsString(is);
        }
        catch (IOException e)
        {
            LOG.warn("Failed to read guidelines resource '{}' from module '{}': {}", resourcePath, module.getName(), e.getMessage());
            return null;
        }
    }
}
