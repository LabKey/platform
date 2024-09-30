/*
 * Copyright (c) 2015-2017 LabKey Corporation
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
package org.labkey.core.admin.sitevalidation;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.admin.sitevalidation.SiteValidationProvider;
import org.labkey.api.admin.sitevalidation.SiteValidationProviderFactory;
import org.labkey.api.admin.sitevalidation.SiteValidationResultList;
import org.labkey.api.admin.sitevalidation.SiteValidationService;
import org.labkey.api.admin.sitevalidation.SiteValidatorDescriptor;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.security.User;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ConcurrentSkipListSet;

public class SiteValidationServiceImpl implements SiteValidationService
{
    private final Map<String, Set<SiteValidationProviderFactory>> _siteFactories = new ConcurrentSkipListMap<>();
    private final Map<String, Set<SiteValidationProviderFactory>> _containerFactories = new ConcurrentSkipListMap<>();

    @Override
    public void registerProviderFactory(String module, SiteValidationProviderFactory factory)
    {
        if (factory.isSiteScope())
        {
            Set<SiteValidationProviderFactory> moduleSet = _siteFactories.computeIfAbsent(module, k -> new ConcurrentSkipListSet<>());
            moduleSet.add(factory);
        }
        else
        {
            Set<SiteValidationProviderFactory> moduleSet = _containerFactories.computeIfAbsent(module, k -> new ConcurrentSkipListSet<>());
            moduleSet.add(factory);
        }
    }

    @Override
    public Map<String, Set<SiteValidationProviderFactory>> getSiteFactories()
    {
        return getImmutable(_siteFactories);
    }

    @Override
    public Map<String, Set<SiteValidationProviderFactory>> getContainerFactories()
    {
        return getImmutable(_containerFactories);
    }

    private Map<String, Set<SiteValidationProviderFactory>> getImmutable(Map<String, Set<SiteValidationProviderFactory>> map)
    {
        Map<String, Set<SiteValidationProviderFactory>> copy = new TreeMap<>();
        for (String key : map.keySet())
        {
            Set<SiteValidationProviderFactory> set = Collections.unmodifiableSet(new TreeSet<>(map.get(key)));
            copy.put(key, set);
        }
        return Collections.unmodifiableMap(copy);
    }

    @NotNull
    @Override
    public Map<String, Map<SiteValidatorDescriptor, SiteValidationResultList>> runSiteScopeValidators(List<String> providers, User u)
    {
        Map<String, Map<SiteValidatorDescriptor, SiteValidationResultList>> siteResults = new LinkedHashMap<>();
        Container root = ContainerManager.getRoot();

        for (Map.Entry<String, Set<SiteValidationProviderFactory>> moduleFactories : _siteFactories.entrySet())
        {
            List<SiteValidationProvider> providersToRun = getValidatorsToRun(moduleFactories, providers);

            // Skip work if there are no selected validators
            if (!providersToRun.isEmpty())
            {
                Map<SiteValidatorDescriptor, SiteValidationResultList> moduleResults = new TreeMap<>();
                siteResults.put(moduleFactories.getKey(), moduleResults);

                for (SiteValidationProvider provider : providersToRun)
                {
                    SiteValidationProviderFactory factory = provider.getFactory();
                    SiteValidatorDescriptor descriptor = new SiteValidatorDescriptorImpl(factory.getName(), factory.getDescription());
                    SiteValidationResultList resultList = provider.runValidation(root, u);
                    moduleResults.put(descriptor, null != resultList ? resultList : new SiteValidationResultList());
                }
            }
        }
        return siteResults;
    }

    @NotNull
    @Override
    public Map<String, Map<SiteValidatorDescriptor, Map<String, Map<String, SiteValidationResultList>>>> runContainerScopeValidators(Container topLevel, boolean includeSubFolders, List<String> providers, User u)
    {
        Map<String, Map<SiteValidatorDescriptor, Map<String, Map<String, SiteValidationResultList>>>> allContainerResults = new LinkedHashMap<>();

        if (providers != null && !providers.isEmpty())
        {
            final List<Container> parentList;

            if (topLevel.isRoot())
            {
                parentList = ContainerManager.getProjects();
                if (includeSubFolders)
                    parentList.add(0, topLevel); // Validate the root as well
            }
            else
            {
                parentList = Collections.singletonList(topLevel);
            }

            for (Map.Entry<String, Set<SiteValidationProviderFactory>> moduleFactories : _containerFactories.entrySet())
            {
                Map<SiteValidatorDescriptor, Map<String, Map<String, SiteValidationResultList>>> moduleResults = new TreeMap<>();
                List<SiteValidationProvider> validatorsToRun = getValidatorsToRun(moduleFactories, providers);

                // Skip work if there are no selected validators from this module
                if (!validatorsToRun.isEmpty())
                {
                    // Initialize maps for each provider
                    validatorsToRun.stream()
                        .map(SiteValidationProvider::getFactory)
                        .forEach(factory -> {
                            SiteValidatorDescriptor descriptor = new SiteValidatorDescriptorImpl(factory.getName(), factory.getDescription());
                            moduleResults.put(descriptor, new TreeMap<>());
                        });

                    for (Container parent : parentList)
                    {
                        List<Container> allChildren = includeSubFolders && !parent.isRoot() ? ContainerManager.getAllChildren(parent, u) : List.of(parent);
                        for (Container c : allChildren)
                        {
                            validatorsToRun.forEach(validator -> {
                                SiteValidationResultList resultList = validator.runValidation(c, u);
                                if (null != resultList && !resultList.getResults().isEmpty())
                                {
                                    SiteValidationProviderFactory factory = validator.getFactory();
                                    SiteValidatorDescriptor descriptor = new SiteValidatorDescriptorImpl(factory.getName(), factory.getDescription());
                                    Map<String, Map<String, SiteValidationResultList>> providerResults = moduleResults.get(descriptor);
                                    String projectPath = null == parent.getProject() ? parent.getName() : StringUtils.substringAfter(parent.getProject().getPath(), "/");
                                    Map<String, SiteValidationResultList> projectResults = providerResults.computeIfAbsent(projectPath, k -> new TreeMap<>());
                                    projectResults.put(StringUtils.substringAfter(c.getPath(), "/"), resultList);
                                }
                            });
                        }
                    }

                    allContainerResults.put(moduleFactories.getKey(), moduleResults);
                }
            }
        }

        return allContainerResults;
    }

    private List<SiteValidationProvider> getValidatorsToRun(Map.Entry<String, Set<SiteValidationProviderFactory>> factories, List<String> providers)
    {
        Set<String> includedProviderNames = new HashSet<>();
        if (providers != null)
            includedProviderNames.addAll(providers);

        return factories.getValue().stream()
            .filter(validator -> includedProviderNames.contains(validator.getName()))
            .map(SiteValidationProviderFactory::getSiteValidationProvider)
            .toList();
    }
}
