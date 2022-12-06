/*
 * Copyright (c) 2016-2018 LabKey Corporation
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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.ObjectFactory;
import org.labkey.api.data.Sort;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainUtil;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.issues.IssueDetailHeaderLinkProvider;
import org.labkey.api.issues.IssuesDomainKindProperties;
import org.labkey.api.issues.IssuesListDefProvider;
import org.labkey.api.issues.IssuesListDefService;
import org.labkey.api.issues.IssuesSchema;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.Group;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.util.HtmlString;
import org.labkey.issue.query.IssuesListDefTable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class IssuesListDefServiceImpl implements IssuesListDefService
{
    private static final Logger LOG = LogManager.getLogger(IssuesListDefServiceImpl.class);
    private final Map<String, IssuesListDefProvider> _issuesListDefProviders = new ConcurrentHashMap<>();
    private final List<IssueDetailHeaderLinkProvider> _headerLinkProviders = new ArrayList<>();

    private static final Comparator<IssuesListDefProvider> ISSUES_LIST_DEF_PROVIDER_COMPARATOR = Comparator.comparing(IssuesListDefProvider::getLabel, String.CASE_INSENSITIVE_ORDER);

    @Override
    public IssuesDomainKindProperties getIssueDomainKindProperties(Container container, @Nullable String defName)
    {
        if (defName == null)
            return new IssuesDomainKindProperties();

        IssueManager.EntryTypeNames typeNames = IssueManager.getEntryTypeNames(container, defName);
        String sortDirection = IssueManager.getCommentSortDirection(container, defName).name();

        Integer assignedToGroup = null;
        Group group = IssueManager.getAssignedToGroup(container, defName);
        if (group != null)
            assignedToGroup = group.getUserId();

        Integer assignedToUser = null;
        User defaultUser = IssueManager.getDefaultAssignedToUser(container, defName);
        if (defaultUser != null)
            assignedToUser = defaultUser.getUserId();

        String relatedFolderName = IssueManager.getDefaultRelatedFolder(container, defName);

        return new IssuesDomainKindProperties(defName, typeNames.singularName, typeNames.pluralName, sortDirection, assignedToGroup, assignedToUser, relatedFolderName);
    }

    @Override
    public ValidationException updateIssueDefinition(Container container, User user, GWTDomain<? extends GWTPropertyDescriptor> original, GWTDomain<? extends GWTPropertyDescriptor> update, IssuesDomainKindProperties properties)
    {
        ValidationException exception = new ValidationException();
        IssueManager.EntryTypeNames names = new IssueManager.EntryTypeNames();

        try (DbScope.Transaction transaction = IssuesSchema.getInstance().getSchema().getScope().ensureTransaction())
        {
            if (properties != null)
            {
                names.singularName = properties.getSingularItemName();
                names.pluralName = properties.getPluralItemName();
                String issueDefName = properties.getIssueDefName();

                IssueManager.saveEntryTypeNames(container, issueDefName, names);
                saveIssueProperties(container, properties, issueDefName);
            }

            // allow for the case of updating just the issues def properties but not the domain (i.e. shared domain case)
            if (original != null && update != null)
            {
                if (!original.getDomainURI().equals(update.getDomainURI()))
                    exception.addGlobalError("Cannot change domainId of an existing issue definition.");
                else
                    exception = DomainUtil.updateDomainDescriptor(original, update, container, user);
            }

            if (!exception.hasErrors())
            {
                transaction.commit();
            }
            return exception;
        }
    }

    @Override
    public void saveIssueProperties(Container container, IssuesDomainKindProperties properties, String name)
    {
        IssueManager.saveCommentSortDirection(container, name, Sort.SortDirection.fromString(properties.getCommentSortDirection()));

        Group group = null;
        if (properties.getAssignedToGroup() != null)
        {
            group = SecurityManager.getGroup(properties.getAssignedToGroup());
            if (null == group)
                throw new IllegalArgumentException("'" + properties.getAssignedToGroup().toString() +
                        "' group not found. Please refer to core.Groups for a valid list of groups.");
        }
        IssueManager.saveAssignedToGroup(container, name, group);

        User user = null;
        if (properties.getAssignedToUser() != null)
        {
            user = UserManager.getUser(properties.getAssignedToUser());
            if (null == user)
                throw new IllegalArgumentException("'" + properties.getAssignedToUser().toString() +
                        "' user not found. Please refer to core.Users for a valid list of users.");
        }

        IssueManager.saveDefaultAssignedToUser(container, name, user);
        IssueManager.setPropDefaultRelatedFolder(container, name, properties.getRelatedFolderName());
    }

    @Override
    public String getNameFromDomain(GWTDomain domain)
    {
        return IssuesListDefTable.nameFromLabel(domain.getName());

    }

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
                        .sorted(ISSUES_LIST_DEF_PROVIDER_COMPARATOR)
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
                        .sorted(ISSUES_LIST_DEF_PROVIDER_COMPARATOR)
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
    public Domain getDomainFromIssueDefId(int issueDefId, Container container, User user)
    {
        IssueListDef issueListDef = IssueManager.getIssueListDef(container, issueDefId);
        if (issueListDef != null)
        {
            Domain domain = issueListDef.getDomain(user);
            if (domain == null)
                LOG.warn("Unable to find the domain for issue list definition id: " + issueDefId + " and container: " + container);
            return domain;
        }
        else
            LOG.warn("Unable to find the issue list definition for id: " + issueDefId + " and container: " + container);

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

    @Override
    public int createIssueListDef(Container container, User user, @NotNull String providerName, @NotNull String label, @Nullable String itemNounSingular, @Nullable String itemNounPlural)
    {
        IssueListDef newDef = IssueManager.createIssueListDef(container, user, providerName, label);
        if (itemNounSingular != null)
            IssueManager.saveEntryTypeNames(newDef.getDomainContainer(user), newDef.getName(), itemNounSingular, itemNounPlural);

        return newDef.getRowId();
    }

    @Override
    public void setIssueListDefAssignedToGroup(Container c, @NotNull String issueDefName, @Nullable Group group)
    {
        IssueManager.saveAssignedToGroup(c, issueDefName, group);
    }

    @Override
    @Deprecated // TODO: Delete
    public int createIssue(Container container, User user, @NotNull String issueDefName, @NotNull String title, @Nullable String body)
    {
        return createIssue(container, user, issueDefName, title, null != body ? HtmlString.unsafe(body) : null);
    }

    @Override
    public int createIssue(Container container, User user, @NotNull String issueDefName, @NotNull String title, @Nullable HtmlString body)
    {
        IssueListDef def = IssueManager.getIssueListDef(container, issueDefName);
        if (def == null)
            throw new IllegalArgumentException("Could not find the IssueListDef with the following name: " + issueDefName);

        IssueObject issue = new IssueObject();
        issue.open(container, user);
        issue.setIssueDefName(def.getName());
        issue.setAssignedTo(user.getUserId());
        issue.setTitle(title);
        issue.setPriority("3");
        issue.setType("Todo");
        if (body != null)
            issue.addComment(user, body);

        ObjectFactory<IssueObject> factory = ObjectFactory.Registry.getFactory(IssueObject.class);
        factory.toMap(issue, issue.getProperties());

        IssueManager.saveIssue(user, container, issue);
        return issue.getIssueId();
    }

    @Override
    public void uncache(Container container)
    {
        IssueListDefCache.uncache(container);
    }

    @Override
    public void deleteIssueDefsForDomain(User user, Domain domain)
    {
        IssueManager.deleteIssueDefsForDomain(user, domain);
    }
}
