/*
 * Copyright (c) 2016 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.issue.model;

import org.labkey.api.data.Container;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.issues.IssueDetailHeaderLinkProvider;
import org.labkey.api.issues.IssuesListDefProvider;
import org.labkey.api.issues.IssuesListDefService;
import org.labkey.api.security.User;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
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
    public Map<String, Domain> getDomainsForIssueDefKind(String kind, Container container, User user)
    {
        if (kind != null)
        {
            Map<String, Domain> domains = new HashMap<>();
            for (IssueListDef issueListDef : IssueManager.getIssueListDefsByKind(kind, container))
                domains.put(issueListDef.getName(), issueListDef.getDomain(user));

            return domains;
        }

        return Collections.emptyMap();
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
