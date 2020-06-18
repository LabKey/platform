package org.labkey.core.view.external.tools;

import org.labkey.api.external.tools.ExternalToolsViewProvider;
import org.labkey.api.external.tools.ExternalToolsViewService;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

public class ExternalToolsViewServiceImpl implements ExternalToolsViewService
{
    private List<ExternalToolsViewProvider> _externalToolsViewProvider = new LinkedList<>();

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
