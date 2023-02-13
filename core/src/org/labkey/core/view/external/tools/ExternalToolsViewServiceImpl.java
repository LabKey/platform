package org.labkey.core.view.external.tools;

import org.labkey.api.external.tools.ExternalToolsViewProvider;
import org.labkey.api.external.tools.ExternalToolsViewService;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class ExternalToolsViewServiceImpl implements ExternalToolsViewService
{
    private List<ExternalToolsViewProvider> _externalToolsViewProvider = new CopyOnWriteArrayList<>();

    @Override
    public void registerExternalAccessViewProvider(ExternalToolsViewProvider provider)
    {
        _externalToolsViewProvider.add(provider);
    }

    @Override
    public Collection<ExternalToolsViewProvider> getExternalAccessViewProviders()
    {
        return _externalToolsViewProvider;
    }
}
