/*
 * Copyright (c) 2016-2017 LabKey Corporation
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
package org.labkey.api.issues;


import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.security.Group;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;

import java.util.List;
import java.util.Map;

public interface IssuesListDefService
{
    static IssuesListDefService get()
    {
        return ServiceRegistry.get(IssuesListDefService.class);
    }

    static void setInstance(IssuesListDefService impl)
    {
        ServiceRegistry.get().registerService(IssuesListDefService.class, impl);
    }

    /**
     * Register a provider that will be used as the "Kind" for a new issue list definition creation.
     * @param provider the provider that defines the domain for the issue list definition.
     */
    void registerIssuesListDefProvider(IssuesListDefProvider provider);

    /**
     * Get the full set of registered issue list definition providers.
     * @return List of IssuesListDefProvider
     */
    List<IssuesListDefProvider> getIssuesListDefProviders();

    /**
     * Get the set of registered issue list definition providers that are enabled based on the given container (most likely
     * based on the set of active modules for that container).
     * @param container the container to check for enabled providers
     * @return
     */
    List<IssuesListDefProvider> getEnabledIssuesListDefProviders(Container container);

    /**
     * Get a registered issue list definition provider based on the provider's name.
     * @param providerName the name to check for in the registered list of providers
     * @return IssuesListDefProvider
     */
    IssuesListDefProvider getIssuesListDefProvider(String providerName);

    /**
     * Get the Domain for a specific issue list definition based on the issue list definition name.
     * @param issueDefName the name of the issue list definition to look for
     * @param container the container to look in
     * @param user the user who made the request
     * @return Domain
     */
    Domain getDomainFromIssueDefName(String issueDefName, Container container, User user);

    /**
     * Get the Domain for a specific issue list definition based on the issue list definition id.
     * @param issueDefId the issue definition row id
     * @param container the container to look in
     * @param user the user who made the request
     * @return Domain
     */
    Domain getDomainFromIssueDefId(int issueDefId, Container container, User user);

    /**
     * Register a provider that will add text links to the issue details header link display.
     * @param provider the provider that will determine which links to add based on a given IssueListDef
     */
    void registerIssueDetailHeaderLinkProvider(IssueDetailHeaderLinkProvider provider);

    /**
     * Returns the list of registered providers which can add links to the issue detail header link listing.
     * @return the list of registered providers
     */
    List<IssueDetailHeaderLinkProvider> getIssueDetailHeaderLinkProviders();

    /**
     * Get a map of the issueDefName to the domain for a given issue domain kind.
     * @param kind The name of the issue def domain kind to filter the set.
     * @param container The container to query
     * @return Map from IssueDefName to Domain
     */
    Map<String, Domain> getDomainsForIssueDefKind(String kind, Container container, User user);

    /**
     * Create a new IssueListDef in the specified container using the given provider name and IssueListDef name.
     * @param container The container to create the new IssueListDef in
     * @param user The user to create the new IssueListDef as
     * @param providerName The name of the IssuesListDefProvider
     * @param label The label to give to the IssueListDef
     * @param itemNounSingular The singular item name to use for this IssueListDef
     * @param itemNounPlural The plural item name to use for this IssueListDef
     * @return int RowId for the newly created IssueListDef
     */
    int createIssueListDef(Container container, User user, @NotNull String providerName, @NotNull String label, @Nullable String itemNounSingular, @Nullable String itemNounPlural);

    /**
     * Set the user group that will be used for the 'Assigned To' dropdown for an issue of this definition. Use null for 'All Project Users'.
     * @param c The container for the IssueListDef
     * @param issueDefName The name of the IssueListDef
     * @param group The group or null for 'All Project Users'
     */
    void setIssueListDefAssignedToGroup(Container c, @NotNull String issueDefName, @Nullable Group group);

    /**
     * Create a new Issue in the IssueListDef for the specified container and user.
     * @param container The container to create the Issue in
     * @param user The user to create the issue as and set the initial assignedTo property
     * @param issueDefName The name of the IssueListDef
     * @param title The Issue title
     * @param body The Issue message body
     * @return int IssueId for the newly created Issue
     */
    int createIssue(Container container, User user, @NotNull String issueDefName, @NotNull String title, @Nullable String body) throws Exception;
}

