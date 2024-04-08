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
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ConcurrentSkipListSet;

public class SiteValidationServiceImpl implements SiteValidationService
{
    private final Map<String, Set<SiteValidationProvider>> siteValidators = new ConcurrentSkipListMap<>();
    private final Map<String, Set<SiteValidationProvider>> containerValidators = new ConcurrentSkipListMap<>();

    @Override
    public void registerProvider(String module, SiteValidationProvider provider)
    {
        if (provider.isSiteScope())
        {
            Set<SiteValidationProvider> moduleSet = siteValidators.computeIfAbsent(module, k -> new ConcurrentSkipListSet<>());
            moduleSet.add(provider);
        }
        else
        {
            Set<SiteValidationProvider> moduleSet = containerValidators.computeIfAbsent(module, k -> new ConcurrentSkipListSet<>());
            moduleSet.add(provider);
        }
    }

    @Override
    public Map<String, Set<SiteValidationProvider>> getSiteProviders()
    {
        return siteValidators;
    }

    @Override
    public Map<String, Set<SiteValidationProvider>> getContainerProviders()
    {
        return containerValidators;
    }

    @NotNull
    @Override
    public Map<String, Map<SiteValidatorDescriptor, SiteValidationResultList>> runSiteScopeValidators(List<String> providers, User u)
    {
        Map<String, Map<SiteValidatorDescriptor, SiteValidationResultList>> siteResults = new LinkedHashMap<>();
        Container root = ContainerManager.getRoot();

        for (Map.Entry<String, Set<SiteValidationProvider>> moduleValidators : siteValidators.entrySet())
        {
            List<SiteValidationProvider> validatorsToRun = getValidatorsToRun(moduleValidators, providers);

            // Skip work if there are no selected validators
            if (!validatorsToRun.isEmpty())
            {
                Map<SiteValidatorDescriptor, SiteValidationResultList> moduleResults = new TreeMap<>();
                siteResults.put(moduleValidators.getKey(), moduleResults);

                for (SiteValidationProvider validator : validatorsToRun)
                {
                    SiteValidatorDescriptor descriptor = new SiteValidatorDescriptorImpl(validator.getName(), validator.getDescription());
                    SiteValidationResultList resultList = validator.runValidation(root, u);
                    if (null != resultList)
                        moduleResults.put(descriptor, resultList);
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
            }
            else
            {
                parentList = Collections.singletonList(topLevel);
            }

            for (Map.Entry<String, Set<SiteValidationProvider>> moduleValidators : containerValidators.entrySet())
            {
                Map<SiteValidatorDescriptor, Map<String, Map<String, SiteValidationResultList>>> moduleResults = new TreeMap<>();
                List<SiteValidationProvider> validatorsToRun = getValidatorsToRun(moduleValidators, providers);

                // Skip work if there are no selected validators from this module
                if (!validatorsToRun.isEmpty())
                {
                    // Initialize maps for each provider
                    for (SiteValidationProvider validator : validatorsToRun)
                    {
                        SiteValidatorDescriptor descriptor = new SiteValidatorDescriptorImpl(validator.getName(), validator.getDescription());
                        moduleResults.put(descriptor, new TreeMap<>());
                    }

                    for (Container parent : parentList)
                    {
                        List<Container> allChildren = includeSubFolders ? ContainerManager.getAllChildren(parent, u) : List.of(parent);
                        for (Container c : allChildren)
                        {
                            validatorsToRun.stream().filter(validator -> validator.shouldRun(c, u)).forEach(validator -> {
                                SiteValidationResultList resultList = validator.runValidation(c, u);
                                if (null != resultList && !resultList.getResults().isEmpty())
                                {
                                    SiteValidatorDescriptor descriptor = new SiteValidatorDescriptorImpl(validator.getName(), validator.getDescription());
                                    Map<String, Map<String, SiteValidationResultList>> providerResults = moduleResults.get(descriptor);
                                    String projectPath = null == parent.getProject() ? parent.getName() : StringUtils.substringAfter(parent.getProject().getPath(), "/");
                                    Map<String, SiteValidationResultList> projectResults = providerResults.computeIfAbsent(projectPath, k -> new TreeMap<>());
                                    projectResults.put(StringUtils.substringAfter(c.getPath(), "/"), resultList);
                                }
                            });
                        }
                    }

                    allContainerResults.put(moduleValidators.getKey(), moduleResults);
                }
            }
        }

        return allContainerResults;
    }

    private List<SiteValidationProvider> getValidatorsToRun(Map.Entry<String, Set<SiteValidationProvider>> moduleValidators, List<String> providers)
    {
        Set<String> includedProviderNames = new HashSet<>();
        if (providers != null)
            includedProviderNames.addAll(providers);

        return moduleValidators.getValue().stream()
            .filter(validator -> includedProviderNames.contains(validator.getName()))
            .toList();
    }
}
