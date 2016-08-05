package org.labkey.api.issues;


import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by davebradlee on 8/3/16.
 */
public class IssuesListDefService
{
    private static final Logger LOG = Logger.getLogger(IssuesListDefService.class);
    private static final IssuesListDefService INSTANCE = new IssuesListDefService();

    private final Map<String, IssuesListDefProvider> _issuesListDefProviders = new ConcurrentHashMap<>();

    private IssuesListDefService()
    {
    }

    public static IssuesListDefService get()
    {
        return INSTANCE;
    }


    public void registerIssuesListDefProvider(IssuesListDefProvider provider)
    {
        if (!_issuesListDefProviders.containsKey(provider.getName().toLowerCase()))
        {
            _issuesListDefProviders.put(provider.getName().toLowerCase(), provider);
        }
        else
            throw new IllegalArgumentException("IssuesListDefProvider '" + provider.getName() + "' is already registered");
    }

    public List<IssuesListDefProvider> getIssuesListDefProviders()
    {
        List<IssuesListDefProvider> providers = new ArrayList<>(_issuesListDefProviders.values());

        Collections.sort(providers, (o1, o2) -> (o1.getLabel().compareToIgnoreCase(o2.getLabel())));
        return Collections.unmodifiableList(providers);
    }

    public IssuesListDefProvider getIssuesListDefProvider(String providerName)
    {
        if (providerName == null)
            return null;
        return _issuesListDefProviders.get(providerName.toLowerCase());
    }

}

