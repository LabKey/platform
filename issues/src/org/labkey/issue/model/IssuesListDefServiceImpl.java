package org.labkey.issue.model;

import org.labkey.api.data.Container;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.issues.IssueDetailHeaderLinkProvider;
import org.labkey.api.issues.IssuesListDefProvider;
import org.labkey.api.issues.IssuesListDefService;
import org.labkey.api.security.User;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class IssuesListDefServiceImpl implements IssuesListDefService.Service
{
    private Map<String, IssuesListDefProvider> _issuesListDefProviders = new ConcurrentHashMap<>();
    private List<IssueDetailHeaderLinkProvider> _headerLinkProviders = new ArrayList<>();

    @Override
    public void registerIssuesListDefProvider(IssuesListDefProvider provider)
    {
        if (!_issuesListDefProviders.containsKey(provider.getName().toLowerCase()))
        {
            _issuesListDefProviders.put(provider.getName().toLowerCase(), provider);
        }
        else
            throw new IllegalArgumentException("IssuesListDefProvider '" + provider.getName() + "' is already registered");
    }

    @Override
    public List<IssuesListDefProvider> getIssuesListDefProviders()
    {
        return Collections.unmodifiableList(
                _issuesListDefProviders.values()
                        .stream()
                        .sorted((o1, o2) -> (o1.getLabel().compareToIgnoreCase(o2.getLabel())))
                        .collect(Collectors.toList())
        );
    }

    @Override
    public List<IssuesListDefProvider> getEnabledIssuesListDefProviders(Container container)
    {
        return Collections.unmodifiableList(
                _issuesListDefProviders.values()
                        .stream()
                        .filter(provider -> provider.isEnabled(container))
                        .sorted((o1, o2) -> (o1.getLabel().compareToIgnoreCase(o2.getLabel())))
                        .collect(Collectors.toList())
        );
    }

    @Override
    public IssuesListDefProvider getIssuesListDefProvider(String providerName)
    {
        if (providerName == null)
            return null;

        return _issuesListDefProviders.get(providerName.toLowerCase());
    }

    @Override
    public Domain getDomainFromIssueDefName(String issueDefName, Container container, User user)
    {
        IssueListDef issueListDef = IssueManager.getIssueListDef(container, issueDefName);
        if (issueListDef != null)
        {
            return issueListDef.getDomain(user);
        }

        return null;
    }

    @Override
    public void registerIssueDetailHeaderLinkProvider(IssueDetailHeaderLinkProvider provider)
    {
        _headerLinkProviders.add(provider);
    }

    @Override
    public List<IssueDetailHeaderLinkProvider> getIssueDetailHeaderLinkProviders()
    {
        return Collections.unmodifiableList(_headerLinkProviders);
    }
}
