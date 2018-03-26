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
